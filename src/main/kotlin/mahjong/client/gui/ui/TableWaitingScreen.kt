package mahjong.client.gui.ui

import mahjong.MOD_ID
import mahjong.block.MahjongTableBlockEntity
import mahjong.client.gui.gui_widgets.*
import mahjong.game.game_logic.MahjongRule
import mahjong.game.game_logic.MahjongTableBehavior
import mahjong.game.game_logic.MahjongTile
import mahjong.network.MahjongTablePayload
import mahjong.network.sendPayloadToServer
import mahjong.registry.ItemRegistry
import mahjong.util.plus
import io.github.cottonmc.cotton.gui.client.CottonClientScreen
import io.github.cottonmc.cotton.gui.client.LibGui
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription
import io.github.cottonmc.cotton.gui.widget.*
import io.github.cottonmc.cotton.gui.widget.data.VerticalAlignment
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import java.util.*

@Environment(EnvType.CLIENT)
class MahjongTableWaitingScreen(
    mahjongTable: MahjongTableBlockEntity,
) : CottonClientScreen(MahjongTableGui(mahjongTable)) {

    override fun shouldPause(): Boolean = false

    fun refresh() {
        (description as MahjongTableGui).refresh()
    }
}

@Environment(EnvType.CLIENT)
class MahjongTableGui(
    val mahjongTable: MahjongTableBlockEntity,
) : LightweightGuiDescription() {

    private val client = MinecraftClient.getInstance()
    private val player: ClientPlayerEntity = client.player!!

    private val players = mahjongTable.players
    private val playerEntityNames = mahjongTable.playerEntityNames
    private val bots = mahjongTable.bots
    private val ready = mahjongTable.ready
    private val rule get() = mahjongTable.rule

    private val isInThisGame: Boolean
        get() = player.uuidAsString in players
    private val isHost: Boolean
        get() = players[0] == player.uuidAsString
    private val isReady: Boolean
        get() {
            val index = players.indexOf(player.uuidAsString)
            if (index == -1) return false
            return ready[index]
        }
    private val isAllReady: Boolean
        get() = (false !in mahjongTable.ready)
    private val isFull: Boolean
        get() = "" !in players
    private val playerAmount: Int
        get() = players.filterNot { it == "" }.size

    private val darkMode: Boolean
        get() = LibGui.isDarkMode()
    private val ruleTexts = mutableListOf<WText>()
    private val playerInfoItems: List<PlayerInfoItem> = List(4) { PlayerInfoItem(it) }

    private lateinit var joinOrLeave: WButton
    private var readyOrNot: WButton? = null
    private var start: WidgetTooltipButton? = null
    private var addBot: WButton? = null
    private var editRules: WButton? = null
    private val kick: MutableList<WButton?> = mutableListOf(null, null, null)

    init {
        rootPlainPanel(width = ROOT_WIDTH, height = ROOT_HEIGHT) {
            val icon = item(
                x = BORDER_MARGIN,
                y = BORDER_MARGIN,
                itemStack = ItemRegistry.mahjongTile.defaultStack.also { it.damage = MahjongTile.S1.code })
            label(
                x = icon.x + icon.width + 5,
                y = icon.y,
                height = icon.height,
                text = Text.translatable("$MOD_ID.game.riichi_mahjong"),
                verticalAlignment = VerticalAlignment.CENTER,
            )
            joinOrLeave = button(
                x = BUTTON_JOIN_OR_LEAVE_X,
                y = BUTTON_JOIN_OR_LEAVE_Y,
                width = BUTTON_WIDTH,
                onClick = {
                    if (!isInThisGame) {
                        sendPayloadToServer(
                            payload = MahjongTablePayload(
                                behavior = MahjongTableBehavior.JOIN,
                                pos = mahjongTable.pos
                            )
                        )
                    } else {
                        sendPayloadToServer(
                            payload = MahjongTablePayload(
                                behavior = MahjongTableBehavior.LEAVE,
                                pos = mahjongTable.pos
                            )
                        )
                    }
                }
            )
            playerInfoItems.forEachIndexed { index, playerInfoItem ->
                this.add(
                    playerInfoItem,
                    44,
                    30 + index * playerInfoItem.height,
                    playerInfoItem.width,
                    playerInfoItem.height
                )
            }
            scrollPanel(
                x = playerInfoItems[0].x + playerInfoItems[0].width,
                y = 14,
                width = 140,
                height = ROOT_HEIGHT - 14 * 2
            ) {
                rule.toTexts(
                    color2 = if (!darkMode) Formatting.DARK_GRAY else Formatting.YELLOW,
                    color3 = if (!darkMode) Formatting.DARK_PURPLE else Formatting.GREEN,
                    color4 = if (!darkMode) Formatting.LIGHT_PURPLE else Formatting.AQUA,
                    color5 = if (!darkMode) Formatting.DARK_GRAY else Formatting.WHITE
                ).forEachIndexed { index, text ->
                    val y = if (index > 0) ruleTexts[index - 1].let { it.y + it.height } + 3 else 0
                    ruleTexts += when (index) {
                        3 -> tooltipText(
                            x = 0,
                            y = y,
                            width = 132,
                            text = text,
                            tooltip = arrayOf(
                                Text.translatable(
                                    "$MOD_ID.game.starting_points.description",
                                    MahjongRule.MIN_POINTS,
                                    MahjongRule.MAX_POINTS
                                )
                            )
                        )
                        4 -> tooltipText(
                            x = 0,
                            y = y,
                            width = 132,
                            text = text,
                            tooltip = arrayOf(Text.translatable("$MOD_ID.game.min_points_to_win.description"))
                        )
                        else -> text(x = 0, y = y, width = 132, text = text)
                    }
                }
            }
        }
        refresh()
    }


    fun refresh() {
        with(joinOrLeave) {
            isEnabled = if (isInThisGame) true else "" in players
            label =
                if (!isInThisGame) Text.translatable("$MOD_ID.gui.button.join") else Text.translatable("$MOD_ID.gui.button.leave")
        }
        playerInfoItems.forEach {
            it.entityName = playerEntityNames[it.number]
            it.stringUUID = players[it.number]
            it.isBot = bots[it.number]
            it.ready = ready[it.number]
            it.fresh()
        }
        rule.toTexts(
            color2 = if (!darkMode) Formatting.DARK_GRAY else Formatting.YELLOW,
            color3 = if (!darkMode) Formatting.DARK_PURPLE else Formatting.GREEN,
            color4 = if (!darkMode) Formatting.LIGHT_PURPLE else Formatting.AQUA,
            color5 = if (!darkMode) Formatting.DARK_GRAY else Formatting.WHITE
        ).forEachIndexed { index, text ->
            ruleTexts[index].also {
                it.text = text
                val y = if (index > 0) {
                    ruleTexts[index - 1].let { wText -> wText.y + wText.height } + 3
                } else {
                    0
                }
                it.setLocation(0, y)
            }
        }
        when {
            isHost -> {
                if (start == null) {
                    start = WidgetTooltipButton(
                        label = Text.translatable("$MOD_ID.gui.button.start"),
                        tooltip = arrayOf(Text.translatable("$MOD_ID.gui.tooltip.clickable.when_all_ready"))
                    )
                    (rootPanel as WPlainPanel).add(
                        start,
                        joinOrLeave.x,
                        joinOrLeave.y - BUTTON_HEIGHT - BUTTON_PADDING,
                        BUTTON_WIDTH,
                        BUTTON_HEIGHT
                    )
                }
                start!!.apply {
                    isEnabled = isAllReady
                    onClick = Runnable {
                        if (isAllReady && isHost) {
                            sendPayloadToServer(
                                payload = MahjongTablePayload(
                                    behavior = MahjongTableBehavior.START,
                                    pos = mahjongTable.pos
                                )
                            )
                        }
                    }
                }
                if (addBot == null) {
                    addBot = WButton(Text.translatable("$MOD_ID.gui.button.add_bot"))
                    (rootPanel as WPlainPanel).add(
                        addBot,
                        start!!.x,
                        start!!.y - BUTTON_HEIGHT - BUTTON_PADDING,
                        BUTTON_WIDTH,
                        BUTTON_HEIGHT
                    )
                }
                addBot!!.apply {
                    isEnabled = !isFull
                    onClick = Runnable {
                        if (!isFull && isHost) {
                            sendPayloadToServer(
                                payload = MahjongTablePayload(
                                    behavior = MahjongTableBehavior.ADD_BOT,
                                    pos = mahjongTable.pos
                                )
                            )
                        }
                    }
                }
                if (editRules == null) {
                    editRules = WButton(Text.translatable("$MOD_ID.gui.button.edit_rules"))
                    (rootPanel as WPlainPanel).add(
                        editRules,
                        addBot!!.x,
                        addBot!!.y - BUTTON_HEIGHT - BUTTON_PADDING,
                        BUTTON_WIDTH,
                        BUTTON_HEIGHT
                    )
                }
                editRules!!.apply {
                    onClick = Runnable {
                        if (isHost) {
                            sendPayloadToServer(
                                payload = MahjongTablePayload(
                                    behavior = MahjongTableBehavior.OPEN_RULES_EDITOR_GUI,
                                    pos = mahjongTable.pos
                                )
                            )
                        }
                    }
                }
                repeat(3) {
                    val playerIndex = it + 1
                    if (kick[it] == null) {
                        val kickText = Text.translatable("$MOD_ID.gui.button.kick")
                        kick[it] = WButton(kickText)
                        val buttonWidth = client.textRenderer.getWidth(kickText) + 12
                        (rootPanel as WPlainPanel).add(
                            kick[it],
                            playerInfoItems[0].x - BUTTON_PADDING - buttonWidth,
                            playerInfoItems[playerIndex].y + 2,
                            buttonWidth,
                            BUTTON_HEIGHT
                        )
                    }
                    kick[it]!!.apply {
                        isEnabled = playerAmount > playerIndex
                        onClick = Runnable {
                            if (isHost) {
                                sendPayloadToServer(
                                    payload = MahjongTablePayload(
                                        behavior = MahjongTableBehavior.KICK,
                                        pos = mahjongTable.pos,
                                        extraData = playerIndex.toString()
                                    )
                                )
                            }
                        }
                    }
                }
                clearNotHostButtons()
            }
            isInThisGame -> {
                if (readyOrNot == null) {
                    readyOrNot = WButton()
                    (rootPanel as WPlainPanel).add(
                        readyOrNot,
                        joinOrLeave.x,
                        joinOrLeave.y - BUTTON_HEIGHT - BUTTON_PADDING,
                        BUTTON_WIDTH,
                        BUTTON_HEIGHT
                    )
                }
                readyOrNot!!.apply {
                    label = if (isReady) {
                        Text.translatable("$MOD_ID.gui.button.not_ready")
                    } else {
                        Text.translatable("$MOD_ID.gui.button.ready")
                    }
                    onClick = Runnable {
                        if (isReady) {
                            sendPayloadToServer(
                                payload = MahjongTablePayload(
                                    behavior = MahjongTableBehavior.NOT_READY,
                                    pos = mahjongTable.pos
                                )
                            )
                        } else {
                            sendPayloadToServer(
                                payload = MahjongTablePayload(
                                    behavior = MahjongTableBehavior.READY,
                                    pos = mahjongTable.pos
                                )
                            )
                        }
                    }
                }
                clearHostButtons()
            }
            else -> {
                clearHostButtons()
                clearNotHostButtons()
            }
        }
    }

    private fun clearHostButtons() {
        start?.let { rootPanel.remove(it) }
        start = null
        addBot?.let { rootPanel.remove(it) }
        addBot = null
        editRules?.let { rootPanel.remove(it) }
        editRules = null
        kick.filterNotNull().forEach { rootPanel.remove(it) }
        repeat(3) { kick[it] = null }
    }

    private fun clearNotHostButtons() {
        readyOrNot?.let { rootPanel.remove(it) }
        readyOrNot = null
    }

    companion object {
        private const val ROOT_WIDTH = 400
        private const val ROOT_HEIGHT = 200
        private const val BUTTON_WIDTH = 80
        private const val BORDER_MARGIN = 8
        private const val BUTTON_HEIGHT = 20
        private const val BUTTON_JOIN_OR_LEAVE_X = 310
        private const val BUTTON_JOIN_OR_LEAVE_Y = 160
        private const val BUTTON_PADDING = 5
    }

    class PlayerInfoItem(
        val number: Int,
    ) : WPlainPanel() {
        var entityName: String = ""
        var stringUUID: String = ""
        var isBot: Boolean = false
        var ready: Boolean = false
        private val client = MinecraftClient.getInstance()
        private val fontHeight = client.textRenderer.fontHeight
        private val ttPlayer = Text.translatable("$MOD_ID.game.player")
        private val ttHost = Text.translatable("$MOD_ID.game.host")
        private val ttReady = Text.translatable("$MOD_ID.gui.button.ready")
        private val ttNotReady = Text.translatable("$MOD_ID.gui.button.not_ready")
        private val ttEmpty = Text.translatable("$MOD_ID.game.empty")

        init {
            this.setSize(WIDTH, HEIGHT)
        }

        private var face: WWidget =
            faceWidget(x = 0, y = 0, width = 22, height = 22, isBot = isBot, uuid = null, name = null)

        private val name: WLabel = label(
            x = face.x + face.width + 6,
            y = face.y + face.height - fontHeight,
            text = Text.of(entityName)
        )
        private val playerNum = ttPlayer + " ${number + 1}"
        private val numberAndReady: WLabel = label(
            x = name.x,
            y = name.y - fontHeight - 3,
            text = if (number == 0) {
                playerNum + " (" + ttHost + ")"
            } else {
                playerNum
            }
        )

        fun fresh() {
            when {
                isBot -> {
                    if (face !is WidgetBotFace) {
                        this.remove(face)
                        face = botFace(face.x, face.y, face.width, face.height)
                    }
                }
                stringUUID.isNotEmpty() -> {
                    if (face !is WidgetPlayerFace) {
                        this.remove(face)
                        face = playerFace(
                            face.x,
                            face.y,
                            face.width,
                            face.height,
                            uuid = UUID.fromString(stringUUID),
                            name = entityName
                        )
                    } else {
                        (face as WidgetPlayerFace).also {
                            val uuidFromStr = UUID.fromString(stringUUID)
                            if (entityName != it.name && uuidFromStr != it.uuid) {
                                it.setUuidAndName(uuid = uuidFromStr, name = entityName)
                            }
                        }
                    }
                }
                else -> {
                    if (face !is WSprite) {
                        this.remove(face)
                        face = image(
                            face.x,
                            face.y,
                            face.width,
                            face.height,
                            Identifier.of("minecraft:textures/item/structure_void.png")
                        )
                    }
                }
            }
            if (number != 0) {
                numberAndReady.text = if (entityName.isNotEmpty()) {
                    Text.literal("") + playerNum + " (" + (if (ready) ttReady else ttNotReady) + ")"
                } else {
                    playerNum
                }
            }
            name.text = when {
                entityName.isNotEmpty() ->
                    if (!isBot) Text.of(entityName)
                    else Text.translatable("entity.$MOD_ID.mahjong_bot")
                else -> ttEmpty
            }
        }

        companion object {
            const val HEIGHT = 40
            const val WIDTH = 120

            private fun WPlainPanel.faceWidget(
                x: Int,
                y: Int,
                width: Int,
                height: Int,
                isBot: Boolean,
                uuid: UUID?,
                name: String?,
            ): WWidget = when {
                isBot -> botFace(x, y, width, height)
                uuid != null && name != null -> playerFace(x, y, width, height, uuid, name)
                else -> image(x, y, width, height, Identifier.of("minecraft:textures/item/structure_void.png"))
            }
        }
    }
}