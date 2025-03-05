package mahjong.client.gui.gui_widgets


import io.github.cottonmc.cotton.gui.widget.WWidget
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.DrawContext


import net.minecraft.util.Identifier

class WidgetBotFace(
    var code: Int? = null,
    var alignment: HorizontalAlignment = HorizontalAlignment.CENTER,
) : WWidget() {

    override fun canResize(): Boolean = true

    @Environment(EnvType.CLIENT)
    override fun paint(context: DrawContext, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val textureId = getBotHeadTexture()
        val boxWidth = height
        val imgX = when (alignment) {
            HorizontalAlignment.LEFT -> x
            HorizontalAlignment.CENTER -> x + (width - boxWidth) / 2
            HorizontalAlignment.RIGHT -> x + width - boxWidth
        }

        context.drawTexture(textureId, imgX, y, 0F, 0F, boxWidth, height, boxWidth, height)
    }

    @Environment(EnvType.CLIENT)
    private fun getBotHeadTexture(): Identifier {
        return Identifier.of("mahjong", "textures/entity/icon.png")
    }
}