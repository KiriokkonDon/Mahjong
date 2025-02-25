package mahjong.client.gui.gui_widgets

import mahjong.MOD_ID
import mahjong.MahjongClient
import mahjong.client.ModConfig
import mahjong.entity.MahjongTileEntity
import mahjong.entity.TileFacing
import mahjong.game.game_logic.MahjongGameBehavior
import mahjong.game.game_logic.MahjongTile
import mahjong.network.MahjongGamePayload
import mahjong.network.sendPayloadToServer
import io.github.cottonmc.cotton.gui.widget.WLabel
import io.github.cottonmc.cotton.gui.widget.WPlainPanel
import io.github.cottonmc.cotton.gui.widget.data.Color
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment
import io.github.cottonmc.cotton.gui.widget.data.VerticalAlignment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult


class WidgetTileHints(
    private val tileHints: ModConfig.TileHints,
) : WPlainPanel() {

    private var tileHintItem: TileHintItem? = null
        set(value) {
            field = value
            MahjongClient.hud?.reposition()
        }

    private var machiOfTargetItems: List<TileHintItem>? = null
    private val backgroundVisible: Boolean get() = tileHintItem != null
    private var hitResult: EntityHitResult? = null

    @Environment(EnvType.CLIENT)
    override fun paint(context: DrawContext, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val target =
            MinecraftClient.getInstance().crosshairTarget?.takeIf { it.type == HitResult.Type.ENTITY } as EntityHitResult?
        hitResult =
            target?.takeIf { it.entity is MahjongTileEntity && (it.entity as MahjongTileEntity).mahjongTile != MahjongTile.UNKNOWN }
        updateHints(hitResult)
        if (backgroundPainter != null && backgroundVisible) backgroundPainter.paintBackground(context, x, y, this)
        children.forEach { it.paint(context, x + it.x, y + it.y, mouseX - it.x, mouseY - it.y) }
    }

    private fun updateHints(hitResult: EntityHitResult?) {
        if (hitResult == null) {
            tileHintItem?.let { remove(it) }
            tileHintItem = null
            machiOfTargetItems?.forEach { remove(it) }
            machiOfTarget = null
            machiOfTargetItems = null
            return
        }
        val mahjongTileEntity = hitResult.entity as MahjongTileEntity
        val mahjongTile = mahjongTileEntity.mahjongTile
        if (mahjongTile == MahjongTile.UNKNOWN) return
        val mahjongTable = mahjongTileEntity.mahjongTable
        val remainingAmount = mahjongTable?.remainingTiles?.get(mahjongTileEntity.mahjong4jTile.code) ?: 0
        val player = MinecraftClient.getInstance().player
        val playerUuidString = player?.uuidAsString
        val remainingAmountVisible =
            playerUuidString != null && mahjongTable != null && playerUuidString in mahjongTable.players
        if (tileHintItem == null) {
            val hintItem = TileHintItem(
                scale = tileHints.hudAttribute.scale,
                tile = mahjongTile,
                remainingAmount = remainingAmount,
                remainingAmountVisible = remainingAmountVisible
            )
            add(hintItem, 0, 0, hintItem.width, hintItem.height)
            tileHintItem = hintItem
        }
        tileHintItem?.also {
            it.scale = tileHints.hudAttribute.scale
            val isOwner = playerUuidString != null && mahjongTileEntity.ownerUUID == playerUuidString //這張牌的主人是這個玩家
            val isFacingHorizontal = mahjongTileEntity.facing == TileFacing.HORIZONTAL

            if (isOwner && isFacingHorizontal && (it.tile != mahjongTile || (machiOfTarget == null && machiOfTargetItems == null))) {

                sendPayloadToServer(
                    payload = MahjongGamePayload(
                        behavior = MahjongGameBehavior.MACHI,
                        extraData = Json.encodeToString(mahjongTile)
                    )
                )
            }
            it.tile = mahjongTile
            it.setRemainingTiles(
                amount = remainingAmount,
                visible = remainingAmountVisible,
                noYaku = false,
                machi = false
            )
        }
    }

    fun reposition() {
        val hitResult = hitResult ?: return
        val mahjongTileEntity = hitResult.entity as MahjongTileEntity
        val mahjongTable = mahjongTileEntity.mahjongTable
        val remainingAmount = { code: Int -> mahjongTable?.remainingTiles?.get(code) ?: 0 }
        machiOfTarget?.also { options ->
            machiOfTargetItems?.forEach { remove(it) }
            machiOfTargetItems = buildList {
                options.forEach { tile, (han, furiten) ->
                    val hintItem = TileHintItem(
                        scale = tileHints.hudAttribute.scale,
                        tile = tile,
                        remainingAmount = remainingAmount(tile.mahjong4jTile.code),
                        remainingAmountVisible = true,
                        noYaku = han == 0,
                        machi = true,
                        furiten = furiten
                    )
                    add(hintItem, 0, 0, hintItem.width, hintItem.height)
                    this += hintItem
                }
            }
        }
        children.forEachIndexed { index, widget ->
            val x = if (index > 0) children[index - 1].let { it.x + it.width } + INTERVAL else 0
            widget.setLocation(x, widget.y)
        }
        val rootX = children.maxOf { it.x + it.width }
        val rootY = children.maxOf { it.y + it.height }
        setSize(rootX, rootY)
    }

    companion object {
        private const val INTERVAL = 12

        var machiOfTarget: Map<MahjongTile, Pair<Int, Boolean>>? = null
            set(value) {
                field = value
                MahjongClient.hud?.reposition()
            }
    }

    class TileHintItem(
        var scale: Double,
        tile: MahjongTile,
        furiten: Boolean = false,
        remainingAmount: Int,
        remainingAmountVisible: Boolean,
        noYaku: Boolean = false,
        machi: Boolean = false,
    ) : WPlainPanel() {
        private val client = MinecraftClient.getInstance()
        private val furitenWidget: WLabel
        private val tileWidget: WidgetMahjongTile
        private val remainingAmountWidget: WLabel
        private val furitenText get() = Text.translatable("$MOD_ID.game.furiten")
        private val noYakuText get() = Text.translatable("$MOD_ID.game.no_yaku")
        private val tilesLeftText = { amount: Int -> Text.translatable("$MOD_ID.game.tiles_left", amount) }

        init {
            val furitenWidth = client.textRenderer.getWidth(furitenText)
            val tileWidth = (HINT_WIDTH * scale).toInt()
            val remainingAmountWidth = client.textRenderer.getWidth(tilesLeftText(remainingAmount))
            val width = maxOf(furitenWidth, tileWidth, remainingAmountWidth) + INSET * 2
            val height = (HINT_HEIGHT * scale + MARGIN * 2 + client.textRenderer.fontHeight * 2 + INSET * 2).toInt()
            setSize(width, height)
            furitenWidget = label(
                x = 0,
                y = INSET,
                width = width,
                height = client.textRenderer.fontHeight,
                text = if (furiten) furitenText else Text.of(""),
                horizontalAlignment = HorizontalAlignment.CENTER,
                verticalAlignment = VerticalAlignment.CENTER,
                color = Color.PURPLE_DYE.toRgb()
            )
            tileWidget = mahjongTile(
                x = ((width - tileWidth) / 2.0).toInt(),
                y = furitenWidget.let { it.y + it.height } + MARGIN,
                width = tileWidth,
                height = (HINT_HEIGHT * scale).toInt(),
                mahjongTile = tile,
            )
            remainingAmountWidget = label(
                x = 0,
                y = tileWidget.let { it.y + it.height } + MARGIN,
                width = width,
                height = client.textRenderer.fontHeight + INSET,
                text = when {
                    noYaku -> noYakuText
                    remainingAmountVisible -> tilesLeftText(remainingAmount)
                    else -> Text.of("")
                },
                horizontalAlignment = HorizontalAlignment.CENTER,
                verticalAlignment = VerticalAlignment.CENTER,
                color = if (machi) Color.GREEN.toRgb() else Color.WHITE.toRgb()
            )
        }

        var tile: MahjongTile
            get() = tileWidget.mahjongTile
            set(value) {
                tileWidget.mahjongTile = value
            }

        fun setRemainingTiles(amount: Int, visible: Boolean, noYaku: Boolean, machi: Boolean) {
            remainingAmountWidget.text = when {
                noYaku -> noYakuText
                visible -> tilesLeftText(amount)
                else -> Text.of("")
            }
            remainingAmountWidget.color = if (machi) Color.GREEN.toRgb() else Color.WHITE.toRgb()
        }

        companion object {
            const val HINT_WIDTH = 24
            const val HINT_HEIGHT = 32
            const val MARGIN = 3
            const val INSET = 4
        }
    }
}