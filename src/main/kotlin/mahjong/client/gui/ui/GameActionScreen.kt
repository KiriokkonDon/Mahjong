package mahjong.client.gui.ui

import mahjong.MOD_ID
import mahjong.client.gui.gui_widgets.*
import mahjong.game.game_logic.ClaimTarget
import mahjong.game.game_logic.MahjongGameBehavior
import mahjong.game.game_logic.MahjongRule
import mahjong.game.game_logic.MahjongTile
import mahjong.network.MahjongGamePayload
import mahjong.network.sendPayloadToServer
import mahjong.scheduler.client.ClientCountdownTimeHandler
import io.github.cottonmc.cotton.gui.client.CottonClientScreen
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription
import io.github.cottonmc.cotton.gui.widget.WPlainPanel
import io.github.cottonmc.cotton.gui.widget.data.Color
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.mahjong4j.tile.Tile

@Environment(EnvType.CLIENT)
class MahjongGameBehaviorScreen(
    behavior: MahjongGameBehavior,
    hands: List<MahjongTile>,
    target: ClaimTarget,
    data: String,
) : CottonClientScreen(MahjongGameBehaviorGui(behavior, hands, target, data)) {
    override fun shouldPause(): Boolean = false
}

@Environment(EnvType.CLIENT)
class MahjongGameBehaviorGui(
    private val behavior: MahjongGameBehavior,
    private val hands: List<MahjongTile>,
    private val target: ClaimTarget,
    private val data: String,
) : LightweightGuiDescription() {

    private val basicTime: Int
        get() = ClientCountdownTimeHandler.basicAndExtraTime.first ?: 0
    private val extraTime: Int
        get() = ClientCountdownTimeHandler.basicAndExtraTime.second ?: 0
    private val basicTimeText: String
        get() = if (basicTime > 0) "§a$basicTime" else ""
    private val plusTimeText: String
        get() = if (basicTime > 0 && extraTime > 0) "§e + " else ""
    private val extraTimeText: String
        get() = if (extraTime > 0) "§c$extraTime" else ""
    private val timeText: Text
        get() = Text.of("$basicTimeText$plusTimeText$extraTimeText")

    private val alreadyDrewTile = when (behavior) {
        MahjongGameBehavior.TSUMO,
        MahjongGameBehavior.KYUUSHU_KYUUHAI,
        MahjongGameBehavior.ANKAN_OR_KAKAN,
        -> true
        else -> false
    }
    private val handsWidth =
        TILE_WIDTH * hands.size + TILE_GAP * (hands.size - 1) + if (alreadyDrewTile) TAKEN_TILE_GAP else 0
    private var claimingTile: MahjongTile? = null

    private val behaviorItemLists =
        buildList {
            when (behavior) {
                MahjongGameBehavior.CHII -> {
                    val dataList = Json.decodeFromString<List<String>>(data)
                    claimingTile = Json.decodeFromString<MahjongTile>(dataList[0])
                    val tilePairsForChii =
                        Json.decodeFromString<List<Pair<MahjongTile, MahjongTile>>>(dataList[1])
                    tilePairsForChii.forEach {
                        val tiles = it.toList().toMutableList().apply { this += claimingTile!! }
                        this += BehaviorItem(behavior, target, tiles)
                    }
                }
                MahjongGameBehavior.PON_OR_CHII -> {
                    val dataList = Json.decodeFromString<List<String>>(data)
                    claimingTile = Json.decodeFromString<MahjongTile>(dataList[0])
                    val tilePairsForChii =
                        Json.decodeFromString<List<Pair<MahjongTile, MahjongTile>>>(dataList[1])
                    val tilePairForPon = Json.decodeFromString<Pair<MahjongTile, MahjongTile>>(dataList[2])
                    this += BehaviorItem(
                        behavior = MahjongGameBehavior.PON,
                        target = target,
                        tiles = tilePairForPon.toList().toMutableList().apply { this += claimingTile!! }
                    )
                    tilePairsForChii.forEach {
                        this += BehaviorItem(
                            behavior = MahjongGameBehavior.CHII,
                            target = target,
                            tiles = it.toList().toMutableList().apply { this += claimingTile!! }
                        )
                    }
                }
                MahjongGameBehavior.PON -> {
                    val dataList = Json.decodeFromString<List<String>>(data)
                    claimingTile = Json.decodeFromString<MahjongTile>(dataList[0])
                    val tilePairForPon = Json.decodeFromString<Pair<MahjongTile, MahjongTile>>(dataList[1])
                    val tiles = tilePairForPon.toList().toMutableList().apply { this += claimingTile!! }
                    this += BehaviorItem(MahjongGameBehavior.PON, target, tiles)
                }
                MahjongGameBehavior.ANKAN_OR_KAKAN -> {
                    val dataList = Json.decodeFromString<List<String>>(data)
                    val canAnkanTiles = Json.decodeFromString<MutableSet<MahjongTile>>(dataList[0])
                    val canKakanTiles = Json.decodeFromString<MutableSet<Pair<MahjongTile, ClaimTarget>>>(dataList[1])
                    val rule = MahjongRule.fromJsonString(dataList[2])
                    val redFiveQuantity = rule.redFive.quantity
                    canAnkanTiles.distinctBy { it.mahjong4jTile.code }
                        .forEach { tile ->
                            val isFiveTile =
                                tile.mahjong4jTile == MahjongTile.S5.mahjong4jTile || tile.mahjong4jTile == MahjongTile.P5.mahjong4jTile || tile.mahjong4jTile == MahjongTile.M5.mahjong4jTile
                            if (redFiveQuantity == 0 || !isFiveTile) {
                                this += BehaviorItem(MahjongGameBehavior.ANKAN, target, List(4) { tile })
                            } else {
                                val redFiveTile = getRedFiveTile(tile)
                                    ?: throw IllegalStateException("Cannot get red-five tile from $tile")
                                val notRedFiveTile = MahjongTile.entries[redFiveTile.mahjong4jTile.code]
                                val tiles = List(4) { if (it < redFiveQuantity) redFiveTile else notRedFiveTile }
                                this += BehaviorItem(MahjongGameBehavior.ANKAN, target, tiles)
                            }
                        }
                    canKakanTiles.forEach { (tile, oTarget) ->
                        val isFiveTile =
                            tile.mahjong4jTile == MahjongTile.S5.mahjong4jTile || tile.mahjong4jTile == MahjongTile.P5.mahjong4jTile || tile.mahjong4jTile == MahjongTile.M5.mahjong4jTile
                        if (redFiveQuantity == 0 || !isFiveTile) {
                            this += BehaviorItem(MahjongGameBehavior.KAKAN, oTarget, List(4) { tile })
                        } else {
                            val redFiveAmount = redFiveQuantity - if (tile.isRed) 1 else 0
                            val redFiveTile = getRedFiveTile(tile)
                                ?: throw IllegalStateException("Cannot get red-five tile from $tile")
                            val notRedFiveTile = MahjongTile.entries[redFiveTile.mahjong4jTile.code]
                            val tiles = List(4) { index ->
                                if (index < 3) {
                                    if (index < redFiveAmount) redFiveTile else notRedFiveTile
                                } else {
                                    tile
                                }
                            }
                            this += BehaviorItem(MahjongGameBehavior.KAKAN, oTarget, tiles)
                        }
                    }
                }
                MahjongGameBehavior.MINKAN -> {
                    val dataList = Json.decodeFromString<MutableList<String>>(data)
                    claimingTile = Json.decodeFromString<MahjongTile>(dataList[0])
                    val rule = MahjongRule.fromJsonString(dataList[1])
                    val redFiveQuantity = rule.redFive.quantity
                    val isFiveTile =
                        claimingTile!!.mahjong4jTile == MahjongTile.S5.mahjong4jTile || claimingTile!!.mahjong4jTile == MahjongTile.P5.mahjong4jTile || claimingTile!!.mahjong4jTile == MahjongTile.M5.mahjong4jTile
                    if (redFiveQuantity == 0 || !isFiveTile) {
                        this += BehaviorItem(MahjongGameBehavior.MINKAN, target, List(4) { claimingTile!! })
                        this += BehaviorItem(MahjongGameBehavior.PON, target, List(3) { claimingTile!! })
                    } else {
                        val redFiveAmount = redFiveQuantity - if (claimingTile!!.isRed) 1 else 0
                        val redFiveTile = getRedFiveTile(claimingTile!!)
                            ?: throw IllegalStateException("Cannot get red-five tile from $claimingTile")
                        val notRedFiveTile = MahjongTile.entries[redFiveTile.mahjong4jTile.code]
                        val tilesForPon = MutableList(2) { if (it < redFiveAmount) redFiveTile else notRedFiveTile }
                        val tilesForKan = tilesForPon.toMutableList()
                        tilesForKan += notRedFiveTile
                        tilesForPon += claimingTile!!
                        tilesForKan += claimingTile!!
                        this += BehaviorItem(MahjongGameBehavior.MINKAN, target, tilesForKan)
                        this += BehaviorItem(MahjongGameBehavior.PON, target, tilesForPon)
                    }
                }
                MahjongGameBehavior.RIICHI -> {
                    val tilePairsForRiichi =
                        Json.decodeFromString<MutableList<Pair<MahjongTile, MutableList<MahjongTile>>>>(data)
                    tilePairsForRiichi.forEach { (tile, _) ->
                        this += BehaviorItem(MahjongGameBehavior.RIICHI, target, listOf(tile))
                    }
                }
                MahjongGameBehavior.KYUUSHU_KYUUHAI -> {
                    val allYaochu = hands.filter { it.mahjong4jTile.isYaochu }
                    this += BehaviorItem(behavior, target, allYaochu)
                }
                MahjongGameBehavior.RON -> {
                    claimingTile = Json.decodeFromString<MahjongTile>(data)
                    this += BehaviorItem(behavior, target, listOf(claimingTile!!))
                }
                MahjongGameBehavior.TSUMO -> {
                    this += BehaviorItem(behavior, target, listOf(hands.last()))
                }
                else -> {
                }
            }
        }

    private fun getRedFiveTile(tile: MahjongTile): MahjongTile? = when (tile) {
        MahjongTile.M5, MahjongTile.M5_RED -> MahjongTile.M5_RED
        MahjongTile.S5, MahjongTile.S5_RED -> MahjongTile.S5_RED
        MahjongTile.P5, MahjongTile.P5_RED -> MahjongTile.P5_RED
        else -> null
    }

    private val handsHintTiles: List<Tile> = buildList {
        behaviorItemLists.forEach {
            val tiles = it.tiles.toMutableList()
            when (it.behavior) {
                MahjongGameBehavior.ANKAN,
                MahjongGameBehavior.KAKAN,
                MahjongGameBehavior.MINKAN,
                MahjongGameBehavior.RIICHI,
                MahjongGameBehavior.TSUMO,
                MahjongGameBehavior.RON,
                -> this += tiles.last().mahjong4jTile
                else -> {
                    tiles.removeLast()
                    tiles.forEach { tile -> this += tile.mahjong4jTile }
                }
            }
        }
    }

    init {
        rootPlainPanel(width = ROOT_WIDTH, height = ROOT_HEIGHT) {
            val timer = dynamicLabel(x = 8, y = 8, text = { timeText.string })
            button(
                x = ROOT_WIDTH - BUTTON_WIDTH - BORDER_MARGIN,
                y = BORDER_MARGIN,
                width = BUTTON_WIDTH,
                height = BUTTON_HEIGHT,
                label = Text.translatable("$MOD_ID.game.behavior.skip"),
                onClick = {
                    sendPayloadToServer(
                        payload = MahjongGamePayload(behavior = MahjongGameBehavior.SKIP)
                    )
                }
            )
            claimingTileWidget()
            val handsWidgetHeight = TILE_HEIGHT + TILE_GAP + HINT_HEIGHT
            val handsWidget = handsWidget(
                x = 0,
                y = ROOT_HEIGHT - handsWidgetHeight - BORDER_MARGIN,
                width = ROOT_WIDTH,
                height = handsWidgetHeight
            )
            optionsWidget(
                x = 0,
                y = HINT_HEIGHT * 2 + TILE_GAP * 2 + TILE_HEIGHT + TILE_GAP * 6,
                width = ROOT_WIDTH,
                height = ROOT_HEIGHT - timer.height - handsWidget.height
            )
        }
    }

    private fun WPlainPanel.claimingTileWidget(): WidgetMahjongTile? {
        claimingTile?.let {
            val mtX = (ROOT_WIDTH - TILE_WIDTH) / 2
            val tileWidget =
                mahjongTile(
                    x = mtX,
                    y = HINT_HEIGHT + TILE_GAP + BORDER_MARGIN,
                    width = TILE_WIDTH,
                    height = TILE_HEIGHT,
                    mahjongTile = it
                )
            val target = if (behavior == MahjongGameBehavior.KAKAN) ClaimTarget.SELF else target
            val hWidth = if (target == ClaimTarget.LEFT || target == ClaimTarget.RIGHT) HINT_HEIGHT else TILE_WIDTH
            val hHeight =
                if (target == ClaimTarget.LEFT || target == ClaimTarget.RIGHT) TILE_HEIGHT else HINT_HEIGHT
            colorBlock(
                x = when (target) {
                    ClaimTarget.LEFT -> tileWidget.x - TILE_GAP - hWidth
                    ClaimTarget.RIGHT -> tileWidget.x + TILE_WIDTH + TILE_GAP
                    else -> tileWidget.x
                },
                y = when (target) {
                    ClaimTarget.ACROSS -> tileWidget.y - TILE_GAP - hHeight
                    ClaimTarget.SELF -> tileWidget.y + tileWidget.height + TILE_GAP
                    else -> tileWidget.y
                },
                width = hWidth,
                height = hHeight,
                color = Color.GREEN
            )
            return tileWidget
        } ?: return null
    }

    private fun WPlainPanel.handsWidget(x: Int, y: Int, width: Int, height: Int) =
        plainPanel(x, y, width, height) {
            hands.forEachIndexed { index, tile ->
                val tileX = ((width - handsWidth) / 2
                        + (TILE_WIDTH + TILE_GAP) * index
                        + if (alreadyDrewTile && index == hands.lastIndex) TAKEN_TILE_GAP else 0)
                if (tile.mahjong4jTile in handsHintTiles) {
                    colorBlock(
                        x = tileX,
                        y = TILE_HEIGHT + TILE_GAP,
                        width = TILE_WIDTH,
                        height = HINT_HEIGHT,
                        color = Color.RED_DYE
                    )
                }
                mahjongTile(x = tileX, y = 0, width = TILE_WIDTH, height = TILE_HEIGHT, mahjongTile = tile)
            }
        }


    private fun WPlainPanel.optionsWidget(x: Int, y: Int, width: Int, height: Int) =
        scrollPanel(x, y, width, height) {
            val totalWidth = behaviorItemLists.size * OptionItem.WIDTH + (behaviorItemLists.size - 1) * OPTION_GAP
            val offsetX = (width - totalWidth) / 2
            val x0 = if (offsetX > 0) offsetX else 0
            behaviorItemLists.forEachIndexed { index, tilesForOption ->
                val option = OptionItem(tilesForOption, hands, data)
                val optionX = x0 + (OptionItem.WIDTH + OPTION_GAP) * index
                this.add(option, optionX, 0, OptionItem.WIDTH, OptionItem.HEIGHT)
            }
        }


    data class BehaviorItem(
        val behavior: MahjongGameBehavior,
        val target: ClaimTarget,
        val tiles: List<MahjongTile>,
    )

    class OptionItem(
        behaviorItem: BehaviorItem,
        hands: List<MahjongTile>,
        data: String,
    ) : WPlainPanel() {
        private val behavior = behaviorItem.behavior
        private val target = behaviorItem.target
        private val tiles = behaviorItem.tiles

        init {
            setSize(WIDTH, HEIGHT)
            initTiles()
            val tooltip: Array<Text> = when (behavior) {
                MahjongGameBehavior.RIICHI ->
                    buildList<Text> {
                        this += Text.translatable("$MOD_ID.game.machi").formatted(Formatting.RED)
                            .formatted(Formatting.BOLD)
                        val tilePairsForRiichi =
                            Json.decodeFromString<MutableList<Pair<MahjongTile, MutableList<MahjongTile>>>>(data)
                        val pair = tilePairsForRiichi.find { it.first == tiles[0] }
                        val machi = pair!!.second
                            .distinctBy { tile -> tile.toText().string }
                            .sortedBy { tile -> tile.sortOrder }
                        this += machi.map { Text.of("§3 - §e${it.toText().string}") }
                    }.toTypedArray()
                MahjongGameBehavior.EXHAUSTIVE_DRAW ->
                    arrayOf(behavior.toText().formatted(Formatting.RED).formatted(Formatting.BOLD))
                else -> arrayOf()
            }
            tooltipButton(
                x = BUTTON_X,
                y = BUTTON_Y,
                width = BUTTON_WIDTH,
                tooltip = tooltip,
                label = when (behavior) {
                    MahjongGameBehavior.MINKAN,
                    MahjongGameBehavior.ANKAN,
                    MahjongGameBehavior.KAKAN,
                    -> MahjongGameBehavior.KAN.toText()
                    MahjongGameBehavior.KYUUSHU_KYUUHAI -> MahjongGameBehavior.EXHAUSTIVE_DRAW.toText()
                    else -> behavior.toText()
                },
                onClick = {
                    when (behavior) {
                        MahjongGameBehavior.CHII -> {
                            val pair = tiles[0] to tiles[1]
                            sendPayloadToServer(
                                payload = MahjongGamePayload(
                                    behavior = behavior,
                                    extraData = Json.encodeToString(pair)
                                )
                            )
                        }
                        MahjongGameBehavior.KAKAN, MahjongGameBehavior.ANKAN -> {
                            sendPayloadToServer(
                                payload = MahjongGamePayload(
                                    behavior = MahjongGameBehavior.ANKAN_OR_KAKAN,
                                    extraData = Json.encodeToString(tiles.last())
                                )
                            )
                        }
                        MahjongGameBehavior.PON, MahjongGameBehavior.MINKAN,
                        MahjongGameBehavior.TSUMO, MahjongGameBehavior.RON,
                        MahjongGameBehavior.KYUUSHU_KYUUHAI,
                        -> {
                            sendPayloadToServer(
                                payload = MahjongGamePayload(behavior = behavior)
                            )
                        }
                        MahjongGameBehavior.RIICHI -> {
                            sendPayloadToServer(
                                payload = MahjongGamePayload(
                                    behavior = MahjongGameBehavior.RIICHI,
                                    extraData = Json.encodeToString(tiles.first())
                                )
                            )
                        }
                        else -> {
                        }
                    }
                }
            )
        }

        private fun initTiles() {
            if (target == ClaimTarget.SELF || behavior == MahjongGameBehavior.RON) {
                val totalWidth = tiles.size * TILE_WIDTH + (tiles.size - 1) * TILE_GAP
                val x0 = (WIDTH - totalWidth) / 2
                tiles.forEachIndexed { index, mahjongTile ->
                    val x = x0 + (TILE_WIDTH + TILE_GAP) * index
                    mahjongTile(
                        x = x,
                        y = NORMAL_TILE_Y,
                        width = TILE_WIDTH,
                        height = TILE_HEIGHT,
                        mahjongTile = mahjongTile
                    )
                }
            } else {
                val atLeastOneHorizontalTiles = tiles.toMutableList()
                val kakanTile = if (behavior == MahjongGameBehavior.KAKAN) {
                    atLeastOneHorizontalTiles.removeLast()
                } else null
                val claimingTile = atLeastOneHorizontalTiles.removeLast()
                atLeastOneHorizontalTiles.sortBy { it.sortOrder }
                val claimingTileIndex = when (target) {
                    ClaimTarget.RIGHT -> 2
                    ClaimTarget.ACROSS -> 1
                    ClaimTarget.LEFT -> 0
                    else -> throw IllegalStateException("При вычислении опций действия в маджонге возникла невозможная ситуация")
                }
                val claimingTileDirection = when (target) {
                    ClaimTarget.RIGHT, ClaimTarget.ACROSS -> WidgetMahjongTile.TileDirection.RIGHT
                    ClaimTarget.LEFT -> WidgetMahjongTile.TileDirection.LEFT
                    else -> throw IllegalStateException("При вычислении опций действия в маджонге возникла невозможная ситуация")
                }
                atLeastOneHorizontalTiles.add(claimingTileIndex, claimingTile)
                val totalWidth = ((atLeastOneHorizontalTiles.size - 1) * TILE_WIDTH
                        + TILE_HEIGHT
                        + (atLeastOneHorizontalTiles.size - 1) * TILE_GAP)
                val x0 = (WIDTH - totalWidth) / 2
                val widgets = mutableListOf<WidgetMahjongTile>()
                atLeastOneHorizontalTiles.forEachIndexed { index, tile ->
                    val x = if (index > 0) widgets[index - 1].let { it.x + it.width + TILE_GAP } else x0
                    if (index == claimingTileIndex) {
                        if (kakanTile != null) {
                            mahjongTile(
                                x = x,
                                y = KAKAN_TILE_Y,
                                width = TILE_WIDTH,
                                height = TILE_HEIGHT,
                                mahjongTile = kakanTile,
                                direction = claimingTileDirection
                            )
                        }
                        widgets += mahjongTile(
                            x = x,
                            y = HORIZONTAL_TILE_Y,
                            width = TILE_WIDTH,
                            height = TILE_HEIGHT,
                            mahjongTile = tile,
                            direction = claimingTileDirection
                        )
                    } else {
                        widgets += mahjongTile(
                            x = x,
                            y = NORMAL_TILE_Y,
                            width = TILE_WIDTH,
                            height = TILE_HEIGHT,
                            mahjongTile = tile
                        )
                    }
                }
            }
        }

        companion object {
            private const val KAKAN_TILE_Y = 0
            private const val HORIZONTAL_TILE_Y = KAKAN_TILE_Y + TILE_WIDTH + TILE_GAP
            private const val NORMAL_TILE_Y = HORIZONTAL_TILE_Y + TILE_WIDTH - TILE_HEIGHT
            const val WIDTH = TILE_WIDTH * 4 + TILE_GAP * 3
            const val HEIGHT = TILE_WIDTH * 2 + TILE_GAP * 2 + BUTTON_HEIGHT
            private const val BUTTON_X = (WIDTH - BUTTON_WIDTH) / 2
            private const val BUTTON_Y = NORMAL_TILE_Y + TILE_HEIGHT + TILE_GAP
        }
    }

    companion object {
        private const val ROOT_WIDTH = 400
        private const val ROOT_HEIGHT = 200
        private const val BUTTON_WIDTH = 80
        private const val BUTTON_HEIGHT = 20
        private const val TILE_SCALE = 0.5f
        private const val TILE_WIDTH = (48 * TILE_SCALE).toInt()
        private const val TILE_HEIGHT = (64 * TILE_SCALE).toInt()
        private const val TILE_GAP = TILE_WIDTH / 12
        private const val TAKEN_TILE_GAP = TILE_GAP * 3
        private const val OPTION_GAP = TILE_GAP * 12
        private const val HINT_HEIGHT = TILE_GAP * 2
        private const val BORDER_MARGIN = 8
    }
}
