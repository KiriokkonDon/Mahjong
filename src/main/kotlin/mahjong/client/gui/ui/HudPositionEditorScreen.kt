package mahjong.client.gui.ui

import mahjong.MOD_ID
import mahjong.MahjongClient
import mahjong.client.HudAttribute
import mahjong.client.gui.gui_widgets.WidgetDraggablePlainPanel
import mahjong.client.gui.gui_widgets.rootPlainPanel
import io.github.cottonmc.cotton.gui.client.BackgroundPainter
import io.github.cottonmc.cotton.gui.client.CottonClientScreen
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription
import io.github.cottonmc.cotton.gui.widget.WText
import io.github.cottonmc.cotton.gui.widget.data.Color
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment
import io.github.cottonmc.cotton.gui.widget.data.VerticalAlignment
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

@Environment(EnvType.CLIENT)
class HudPositionEditorScreen(hud: MahjongHud) : CottonClientScreen(HudPositionEditorGui(hud)) {
    override fun init() {
        super.init()
        (description as HudPositionEditorGui).reposition(width, height)
    }

    override fun close() {
        super.close()
        MahjongClient.hud?.reposition()
    }
}

@Environment(EnvType.CLIENT)
class HudPositionEditorGui(
    private val hud: MahjongHud
) : LightweightGuiDescription() {
    private val window = MinecraftClient.getInstance().window
    private val config = MahjongClient.config
    private val quickActionsText = createCenteredTextWidget(Text.translatable("config.$MOD_ID.quick_actions"))
    private val quickActions = WidgetDraggablePlainPanel(color = config.quickActions.hudAttribute.backgroundColor) { x, y ->
        config.quickActions.hudAttribute.setPosition(x, y)
        MahjongClient.saveConfig()
    }.also { it.add(quickActionsText, 0, 0, it.width, it.height) }
    private val tileHintsText = createCenteredTextWidget(Text.translatable("config.$MOD_ID.tile_hints"))
    private val tileHints = WidgetDraggablePlainPanel(color = config.tileHints.hudAttribute.backgroundColor) { _, y ->
        config.tileHints.hudAttribute.setPosition(0, y)
        MahjongClient.saveConfig()
    }.also { it.add(tileHintsText, 0, 0, it.width, it.height) }

    init {
        fullscreen = true
        rootPlainPanel(width = 0, height = 0) {
            backgroundPainter = BackgroundPainter.createColorful((0x00_000000).toInt())
            this.add(quickActions, 0, 0)
            this.add(tileHints, 0, 0)
        }
    }

    fun reposition(screenWidth: Int, screenHeight: Int) {
        hud.quickActionsRootSize.also { (width, height) ->
            this.quickActions.setSize(width, height)
            quickActionsText.setSize(width, height)
        }
        config.quickActions.hudAttribute.also {
            this.quickActions.setLocation(
                (screenWidth * it.x).toInt(),
                (screenHeight * it.y).toInt()
            )
        }
        hud.tileHintsRootSize.also { (_, height) ->
            this.tileHints.setSize(screenWidth, height)
            tileHintsText.setSize(screenWidth, height)
        }
        config.tileHints.hudAttribute.also { this.tileHints.setLocation(0, (screenHeight * it.y).toInt()) }
    }

    private fun HudAttribute.setPosition(x: Int, y: Int) {
        this.x = x / window.scaledWidth.toDouble()
        this.y = y / window.scaledHeight.toDouble()
    }

    private fun createCenteredTextWidget(text: Text, color: Int = Color.WHITE.toRgb()): WText =
        WText(text, color).apply {
            verticalAlignment = VerticalAlignment.CENTER
            horizontalAlignment = HorizontalAlignment.CENTER
        }
}