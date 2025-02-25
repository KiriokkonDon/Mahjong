package mahjong.client.render

import mahjong.MOD_ID
import mahjong.MahjongClient
import mahjong.block.MahjongTable
import mahjong.block.MahjongTablePart
import mahjong.block.MahjongTableBlockEntity
import mahjong.game.GameStatus
import mahjong.game.game_logic.Wind
import mahjong.util.RenderHelper
import mahjong.util.plus
import io.github.cottonmc.cotton.gui.widget.data.Color
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text

@Environment(EnvType.CLIENT)
class TableBlockEntityRenderer(
    context: BlockEntityRendererFactory.Context
) : BlockEntityRenderer<MahjongTableBlockEntity> {
    private val textRenderer = context.textRenderer

    override fun render(
        blockEntity: MahjongTableBlockEntity,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int
    ) {
        if (!MahjongClient.config.displayTableLabels) return
        if (blockEntity.cachedState[MahjongTable.PART] != MahjongTablePart.BOTTOM_CENTER) return
        renderCenterLabels(blockEntity, matrices, vertexConsumers)
        renderPlayerLabels(blockEntity, matrices, vertexConsumers)
    }

    private fun renderPlayerLabels(
        blockEntity: MahjongTableBlockEntity,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider
    ) {
        if (!blockEntity.playing || blockEntity.seat.size != 4) return
        val blockPos = blockEntity.pos
        val dealer = blockEntity.dealer
        val seat = blockEntity.seat
        val dealerSeatIndex = seat.indexOf(dealer).also { if (it == -1) return }
        seat.forEachIndexed { index, stringUUID ->
            val bPos = when (index) {
                0 -> blockPos.east().east()
                1 -> blockPos.north().north()
                2 -> blockPos.west().west()
                else -> blockPos.south().south()
            }
            val windIndex = (dealerSeatIndex - index).let { if (it >= 0) 4 - it else -it } % 4
            buildList {
                this += Wind.entries[windIndex].toText()
                this += Text.of(blockEntity.points[index].toString())
                reverse()
                forEachIndexed { index1, text ->
                    RenderHelper.renderLabel(
                        textRenderer = textRenderer,
                        matrices = matrices,
                        offsetX = 0.5 + (bPos.x - blockPos.x),
                        offsetY = 1.0 + (bPos.y - blockPos.y) + WIND_PADDING + (LABEL_INTERVAL * index1),
                        offsetZ = 0.5 + (bPos.z - blockPos.z),
                        text = text,
                        color = if (stringUUID == dealer) 0xEEB24F else Color.WHITE.toRgb(),
                        light = RenderHelper.getLightLevel(blockEntity.world!!, blockEntity.pos.up()),
                        vertexConsumers = vertexConsumers
                    )
                }
            }
        }
    }


    private fun renderCenterLabels(
        blockEntity: MahjongTableBlockEntity,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider
    ) {
        val playerAmount = blockEntity.players.count { it.isNotEmpty() }
        val readyAmount = blockEntity.ready.count { it }
        val currentReady = READY + ": $readyAmount/4"
        val currentPlayers = PLAYER + ": $playerAmount/4"
        val currentStatus = STATUS + ": " + (if (blockEntity.playing) PLAYING else WAITING)
        val labelPadding = if (blockEntity.playing) PLAYING_PADDING else WAITING_PADDING
        buildList {
            this += currentStatus
            if (!blockEntity.playing) {
                this += currentPlayers
                this += currentReady
            } else {
                val round = blockEntity.round
                val windText = round.wind.toText()
                this += Text.translatable("$MOD_ID.game.round.title", windText, round.round + 1)
            }
            reverse()
            forEachIndexed { index, text ->
                RenderHelper.renderLabel(
                    textRenderer = textRenderer,
                    matrices = matrices,
                    offsetX = 0.5,
                    offsetY = 1.0 + labelPadding + (LABEL_INTERVAL * index),
                    offsetZ = 0.5,
                    text = text,
                    color = Color.WHITE.toRgb(),
                    light = RenderHelper.getLightLevel(blockEntity.world!!, blockEntity.pos.up()),
                    vertexConsumers = vertexConsumers
                )
            }
        }
    }

    companion object {
        private const val WAITING_PADDING = 0.4
        private const val PLAYING_PADDING = 1.6
        private const val WIND_PADDING = 1.6
        private const val LABEL_INTERVAL = 0.25
        private val READY get() = Text.translatable("$MOD_ID.gui.button.ready")
        private val PLAYER get() = Text.translatable("$MOD_ID.game.player")
        private val STATUS get() = Text.translatable("$MOD_ID.game.status")
        private val WAITING = GameStatus.WAITING.toText()
        private val PLAYING = GameStatus.PLAYING.toText()
    }
}