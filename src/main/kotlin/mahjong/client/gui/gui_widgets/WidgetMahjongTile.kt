package mahjong.client.gui.gui_widgets

import mahjong.game.game_logic.MahjongTile
import io.github.cottonmc.cotton.gui.client.ScreenDrawing
import io.github.cottonmc.cotton.gui.widget.WWidget
import io.github.cottonmc.cotton.gui.widget.data.Color
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.math.RotationAxis

class WidgetMahjongTile(
    var mahjongTile: MahjongTile,
    var direction: TileDirection = TileDirection.NORMAL,
) : WWidget() {

    override fun canResize(): Boolean = true

    override fun setSize(x: Int, y: Int) {
        if (direction == TileDirection.NORMAL) {
            super.setSize(x, y)
        } else {
            this.width = y
            this.height = x
        }
    }

    @Environment(EnvType.CLIENT)
    override fun paint(context: DrawContext, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        if (direction == TileDirection.NORMAL) {
            ScreenDrawing.texturedRect(
                context,
                x,
                y,
                width,
                height,
                mahjongTile.surfaceIdentifier,
                Color.WHITE.toRgb()
            )
        } else {
            val matrices = context.matrices
            matrices.push()
            when (direction) {
                TileDirection.RIGHT -> {
                    matrices.translate((x + width).toDouble(), y.toDouble(), 0.0)
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90f))
                }
                TileDirection.LEFT -> {
                    matrices.translate(x.toDouble(), (y + height).toDouble(), 0.0)
                    matrices.multiply(RotationAxis.NEGATIVE_Z.rotationDegrees(90f))
                }
                else -> return
            }
            ScreenDrawing.texturedRect(
                context,
                0,
                0,
                height,
                width,
                mahjongTile.surfaceIdentifier,
                Color.WHITE.toRgb()
            )
            matrices.pop()
        }
    }

    enum class TileDirection {
        LEFT, NORMAL, RIGHT
    }
}