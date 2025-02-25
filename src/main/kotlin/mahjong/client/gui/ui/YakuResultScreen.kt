package mahjong.client.gui.ui

import mahjong.MOD_ID
import mahjong.client.gui.icon.BotFaceIcon
import mahjong.client.gui.icon.PlayerFaceIcon
import mahjong.client.gui.gui_widgets.*
import mahjong.game.game_logic.YakuSettlement
import mahjong.scheduler.client.YakuSettleHandler
import mahjong.util.plus
import io.github.cottonmc.cotton.gui.client.CottonClientScreen
import io.github.cottonmc.cotton.gui.client.LibGui
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription
import io.github.cottonmc.cotton.gui.widget.WLabel
import io.github.cottonmc.cotton.gui.widget.WPlainPanel
import io.github.cottonmc.cotton.gui.widget.data.Color
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment
import io.github.cottonmc.cotton.gui.widget.data.VerticalAlignment
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.*

@Environment(EnvType.CLIENT)
class YakuSettlementScreen(
    settlements: List<YakuSettlement>
) : CottonClientScreen(YakuSettlementGui(settlements)) {
    override fun shouldPause(): Boolean = false
}

@Environment(EnvType.CLIENT)
class YakuSettlementGui(
    private val settlements: List<YakuSettlement>
) : LightweightGuiDescription() {

    private val client = MinecraftClient.getInstance()
    private val fontHeight = client.textRenderer.fontHeight
    private val time: Int
        get() = YakuSettleHandler.time
    private val timeText: Text
        get() {
            val dotAmount = 3 - (time % 3)
            var text = "$time "
            repeat(dotAmount) { text += "." }
            return Text.of(text)
        }
    private val darkMode: Boolean
        get() = LibGui.isDarkMode()

    init {
        rootTabPanel(width = ROOT_WIDTH, height = ROOT_HEIGHT) {
            settlements.forEach {
                val hands = it.hands
                val fuuroList = it.fuuroList
                val yakumanList = it.yakumanList
                val doubleYakumanList = it.doubleYakumanList
                val nagashiMangan = it.nagashiMangan
                val redFiveCount = it.redFiveCount
                val score = it.score
                val widget = WPlainPanel().apply {
                    dynamicLabel(
                        x = BORDER_MARGIN,
                        y = ROOT_HEIGHT + TAB_HEIGHT - fontHeight,
                        text = { timeText.string },
                        color = COLOR_RED
                    )
                    val playerInfo = plainPanel(
                        x = BORDER_MARGIN,
                        y = BORDER_MARGIN,
                        width = PLAYER_INFO_WIDTH,
                        height = PLAYER_INFO_HEIGHT
                    ) {
                        val face = if (it.isRealPlayer) {
                            playerFace(
                                x = (PLAYER_INFO_WIDTH - PLAYER_FACE_WIDTH) / 2,
                                y = 12,
                                width = PLAYER_FACE_WIDTH,
                                height = PLAYER_FACE_WIDTH,
                                uuid = UUID.fromString(it.uuid),
                                name = it.displayName
                            )
                        } else {
                            botFace(
                                x = (PLAYER_INFO_WIDTH - PLAYER_FACE_WIDTH) / 2,
                                y = 12,
                                width = PLAYER_FACE_WIDTH,
                                height = PLAYER_FACE_WIDTH,
                                code = it.botCode
                            )
                        }
                        label(
                            x = face.x,
                            y = face.y + face.height + 12,
                            width = face.width,
                            horizontalAlignment = HorizontalAlignment.CENTER,
                            text = Text.of(it.displayName)
                        )
                    }
                    val playerTiles = plainPanel(
                        x = playerInfo.x + playerInfo.width + 16,
                        y = 12,
                        width = ROOT_WIDTH - PLAYER_INFO_WIDTH - BORDER_MARGIN * 2,
                        height = TILE_HEIGHT
                    ) {
                        val amount =
                            hands.size + fuuroList.sumOf { fuuro -> fuuro.second.size } + if (!nagashiMangan) 1 else 0
                        val rate = if (amount > 16) 16f / amount else 1f
                        val tileWidth = (TILE_WIDTH * rate).toInt()
                        val tileHeight = (TILE_HEIGHT * rate).toInt()
                        val tileGap = (TILE_GAP * rate).toInt()
                        var tileX = 0
                        hands.forEach { tile ->
                            mahjongTile(
                                x = tileX,
                                y = 0,
                                width = tileWidth,
                                height = tileHeight,
                                mahjongTile = tile
                            )
                            tileX += (tileWidth + tileGap)
                        }
                        if (it.fuuroList.isNotEmpty()) {
                            tileX += tileGap * 2
                            it.fuuroList.forEach { (isAnkan, tiles) ->
                                tiles.forEachIndexed { index, mahjongTile ->
                                    if (isAnkan && (index == 0 || index == 3)) {
                                        colorBlock(
                                            x = tileX,
                                            y = 0,
                                            width = tileWidth,
                                            height = tileHeight,
                                            color = COLOR_TILE_BACK
                                        )
                                    } else {
                                        mahjongTile(
                                            x = tileX,
                                            y = 0,
                                            width = tileWidth,
                                            height = tileHeight,
                                            mahjongTile = mahjongTile
                                        )
                                    }
                                    tileX += (tileWidth + tileGap)
                                }
                            }
                        }
                        if (!nagashiMangan) {
                            tileX += tileGap * 3
                            mahjongTile(
                                x = tileX,
                                y = 0,
                                width = tileWidth,
                                height = tileHeight,
                                mahjongTile = it.winningTile
                            )
                        }
                    }
                    val separator1 = colorBlock(
                        x = playerTiles.x,
                        y = playerTiles.y + playerTiles.height + SEPARATOR_PADDING,
                        width = playerTiles.width,
                        height = SEPARATOR_SIZE,
                        color = if (darkMode) SEPARATOR_COLOR_DARK else SEPARATOR_COLOR_LIGHT
                    )
                    val doraAndUraDoraIndicators = plainPanel(
                        x = separator1.x,
                        y = separator1.y + separator1.height + SEPARATOR_PADDING,
                        width = separator1.width,
                        height = TILE_HEIGHT
                    ) {
                        val uraDoraIndicators = if (it.riichi) it.uraDoraIndicators else listOf()
                        var tileX = 0
                        val doraText = label(
                            x = tileX,
                            y = 0,
                            height = TILE_HEIGHT,
                            verticalAlignment = VerticalAlignment.CENTER,
                            text = Text.translatable("$MOD_ID.game.dora"),
                            color = Color.PURPLE_DYE.toRgb()
                        )
                        tileX += doraText.width + TILE_GAP * 2
                        repeat(5) { index ->
                            if (index < it.doraIndicators.size) {
                                mahjongTile(
                                    x = tileX,
                                    y = 0,
                                    width = TILE_WIDTH,
                                    height = TILE_HEIGHT,
                                    mahjongTile = it.doraIndicators[index]
                                )
                            } else {
                                colorBlock(
                                    x = tileX,
                                    y = 0,
                                    width = TILE_WIDTH,
                                    height = TILE_HEIGHT,
                                    color = COLOR_TILE_BACK
                                )
                            }
                            tileX += (TILE_WIDTH + TILE_GAP)
                        }
                        tileX += TILE_GAP * 5
                        val uraDoraText = label(
                            x = tileX,
                            y = 0,
                            height = TILE_HEIGHT,
                            verticalAlignment = VerticalAlignment.CENTER,
                            text = Text.translatable("$MOD_ID.game.ura_dora"),
                            color = Color.PURPLE_DYE.toRgb()
                        )
                        tileX += uraDoraText.width + TILE_GAP * 2
                        repeat(5) { index ->
                            if (index < uraDoraIndicators.size) {
                                mahjongTile(
                                    x = tileX,
                                    y = 0,
                                    width = TILE_WIDTH,
                                    height = TILE_HEIGHT,
                                    mahjongTile = uraDoraIndicators[index]
                                )
                            } else {
                                colorBlock(
                                    x = tileX,
                                    y = 0,
                                    width = TILE_WIDTH,
                                    height = TILE_HEIGHT,
                                    color = COLOR_TILE_BACK
                                )
                            }
                            tileX += (TILE_WIDTH + TILE_GAP)
                        }
                    }
                    val separator2 = colorBlock(
                        x = doraAndUraDoraIndicators.x,
                        y = doraAndUraDoraIndicators.y + doraAndUraDoraIndicators.height + SEPARATOR_PADDING,
                        width = doraAndUraDoraIndicators.width,
                        height = SEPARATOR_SIZE,
                        color = if (darkMode) SEPARATOR_COLOR_DARK else SEPARATOR_COLOR_LIGHT
                    )
                    val yakuList = scrollPanel(
                        x = separator2.x,
                        y = separator2.y + separator2.height + SEPARATOR_PADDING,
                        width = separator2.width,
                        height = YAKU_LIST_HEIGHT
                    ) {
                        val yakuHanMap = hashMapOf<String, Int>()
                        when {
                            yakumanList.isNotEmpty() || doubleYakumanList.isNotEmpty() -> {
                                yakumanList.forEach { yakuman -> yakuHanMap[yakuman.name.lowercase()] = -1 }
                                doubleYakumanList.forEach { doubleYakuman ->
                                    yakuHanMap[doubleYakuman.name.lowercase()] = -1
                                }
                            }
                            nagashiMangan -> yakuHanMap["nagashi_mangan"] = -1
                            else -> {
                                it.yakuList.forEach { yaku ->
                                    val key = yaku.name.lowercase()
                                    if (yakuHanMap[key] != null) yakuHanMap[key] =
                                        yakuHanMap[key]!! + yaku.han
                                    else yakuHanMap[key] = yaku.han
                                }
                                if (redFiveCount > 0) yakuHanMap["red_five"] = redFiveCount
                            }
                        }
                        val yakuLabels = mutableListOf<WLabel>()
                        yakuHanMap.forEach { (yaku, han) ->
                            if (yakuLabels.size > 0) {
                                colorBlock(
                                    x = 0,
                                    y = yakuLabels.last().let { label -> label.y + label.height + SEPARATOR_PADDING },
                                    width = doraAndUraDoraIndicators.width - SCROLL_BAR_SIZE - 4,
                                    height = SEPARATOR_SIZE,
                                    color = if (darkMode) YAKU_SEPARATOR_COLOR_DARK else YAKU_SEPARATOR_COLOR_LIGHT
                                )
                            }
                            val yakuName = label(
                                x = 0,
                                y = if (yakuLabels.size > 0) yakuLabels.last()
                                    .let { label -> label.y + label.height + SEPARATOR_PADDING * 2 + SEPARATOR_SIZE } else 0,
                                width = YAKU_LABEL_WIDTH,
                                text = Text.translatable("$MOD_ID.game.yaku.$yaku"),
                                verticalAlignment = VerticalAlignment.CENTER,
                                color = if (han >= 0) Color.BLACK.toRgb() else Color.CYAN_DYE.toRgb()
                            )
                            yakuLabels += yakuName
                            if (han >= 0) {
                                label(
                                    x = yakuName.x + yakuName.width,
                                    y = yakuName.y,
                                    width = doraAndUraDoraIndicators.width - yakuName.width - SCROLL_BAR_SIZE - 8,
                                    height = yakuName.height,
                                    text = Text.of(han.toString()),
                                    color = Color.GREEN_DYE.toRgb(),
                                    verticalAlignment = VerticalAlignment.CENTER,
                                    horizontalAlignment = HorizontalAlignment.RIGHT
                                )
                            }
                        }
                    }

                    val scoreHeight = fontHeight + TILE_GAP * 3
                    var scoreText: MutableText = Text.literal("")

                    if (yakumanList.isEmpty() && doubleYakumanList.isEmpty() && !nagashiMangan) {
                        val fu = Text.literal("${it.fu}").formatted(Formatting.DARK_AQUA)
                        val fuText = Text.translatable("$MOD_ID.game.fu").formatted(Formatting.DARK_PURPLE)
                        val han = Text.literal("${it.han}").formatted(Formatting.DARK_AQUA)
                        val hanText = Text.translatable("$MOD_ID.game.han").formatted(Formatting.DARK_PURPLE)
                        scoreText += (fu + " " + fuText + " " + han + " " + hanText)
                    }
                    scoreText += "  §c$score"
                    val scoreAlias: MutableText? = when {
                        nagashiMangan -> Text.translatable("$MOD_ID.game.score.mangan")
                        yakumanList.isNotEmpty() || doubleYakumanList.isNotEmpty() -> {
                            val rate = (yakumanList.size * 1 + doubleYakumanList.size * 2).let { amount ->
                                if (amount > 6) 6 else amount
                            }
                            Text.translatable("$MOD_ID.game.score.yakuman_${rate}x")
                        }
                        it.han >= 13 -> Text.translatable("$MOD_ID.game.score.kazoe_yakuman")
                        it.han >= 11 -> Text.translatable("$MOD_ID.game.score.sanbaiman")
                        it.han >= 8 -> Text.translatable("$MOD_ID.game.score.baiman")
                        it.han >= 6 -> Text.translatable("$MOD_ID.game.score.haneman")
                        it.han >= 5 || (it.fu >= 40 && it.han == 4) || (it.fu >= 70 && it.han == 3) ->
                            Text.translatable("$MOD_ID.game.score.mangan")
                        else -> null
                    }?.also { alias -> alias.formatted(Formatting.BOLD).formatted(Formatting.DARK_RED) }
                    if (scoreAlias != null) {
                        val decoratedStr = "§4§l!!"
                        scoreText += "  $decoratedStr"
                        scoreText += scoreAlias
                        scoreText += decoratedStr
                    }
                    plainPanel(
                        x = yakuList.x,
                        y = yakuList.y + yakuList.height + 8,
                        width = yakuList.width,
                        height = scoreHeight
                    ) {
                        label(
                            x = 0,
                            y = 0,
                            width = yakuList.width - BORDER_MARGIN,
                            height = scoreHeight,
                            text = scoreText,
                            horizontalAlignment = HorizontalAlignment.RIGHT,
                            verticalAlignment = VerticalAlignment.CENTER
                        )
                    }
                }
                tab(
                    widget = widget,
                    icon = if (it.isRealPlayer) PlayerFaceIcon(
                        uuid = UUID.fromString(it.uuid),
                        name = it.displayName
                    ) else BotFaceIcon(code = it.botCode),
                    tooltip = listOf(Text.of(it.displayName))
                )
            }
        }
    }

    companion object {
        private const val TAB_HEIGHT = 34
        private const val ROOT_WIDTH = 400
        private const val ROOT_HEIGHT = 200 - TAB_HEIGHT
        private const val BORDER_MARGIN = 8
        private const val PLAYER_INFO_WIDTH = 84
        private const val PLAYER_INFO_HEIGHT = 84
        private const val PLAYER_FACE_WIDTH = 48
        private const val TILE_SCALE = 0.35f
        private const val TILE_WIDTH = (48 * TILE_SCALE).toInt()
        private const val TILE_HEIGHT = (64 * TILE_SCALE).toInt()
        private const val TILE_GAP = 3
        private const val YAKU_LABEL_WIDTH = 120
        private const val YAKU_LIST_HEIGHT = 135 - TAB_HEIGHT
        private const val COLOR_TILE_BACK = (0xFF_9CFF69).toInt()
        private const val COLOR_RED = (0xFF_FF5555).toInt()
        private const val SCROLL_BAR_SIZE = 8
        private const val SEPARATOR_COLOR_DARK = (0xFF747A80).toInt()
        private const val SEPARATOR_COLOR_LIGHT = (0xFF3C3F41).toInt()
        private const val YAKU_SEPARATOR_COLOR_DARK = (0xFF5F6467).toInt()
        private const val YAKU_SEPARATOR_COLOR_LIGHT = (0xFFAFB1B3).toInt()
        private const val SEPARATOR_PADDING = 5
        private const val SEPARATOR_SIZE = 1
    }
}