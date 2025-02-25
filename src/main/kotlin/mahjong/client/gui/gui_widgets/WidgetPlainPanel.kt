package mahjong.client.gui.gui_widgets

import mahjong.game.game_logic.MahjongTile
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription
import io.github.cottonmc.cotton.gui.widget.*
import io.github.cottonmc.cotton.gui.widget.data.Color
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment
import io.github.cottonmc.cotton.gui.widget.data.VerticalAlignment
import io.github.cottonmc.cotton.gui.widget.icon.Icon
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.*


@Environment(EnvType.CLIENT)
fun LightweightGuiDescription.rootPlainPanel(
    width: Int,
    height: Int,
    init: WPlainPanel.() -> Unit
): WPlainPanel {
    val plainPanel = WPlainPanel().also {
        it.setSize(width, height)
        it.init()
    }
    rootPanel = plainPanel
    rootPanel.validate(this)
    return plainPanel
}


@Environment(EnvType.CLIENT)
fun LightweightGuiDescription.rootScrollPanel(
    width: Int,
    height: Int,
    init: WPlainPanel.() -> Unit
): WScrollPanel {
    val plainPanel = WPlainPanel().also { it.init() }
    val scrollPanel = WScrollPanel(plainPanel).also { it.setSize(width, height) }
    rootPanel = scrollPanel
    rootPanel.validate(this)
    return scrollPanel
}


@Environment(EnvType.CLIENT)
fun WPlainPanel.plainPanel(
    x: Int,
    y: Int,
    width: Int = 18,
    height: Int = 18,
    init: WPlainPanel.() -> Unit = {}
): WPlainPanel {
    val plainPanel = WPlainPanel()
    plainPanel.init()
    this.add(plainPanel, x, y, width, height)
    return plainPanel
}



@Environment(EnvType.CLIENT)
fun WPlainPanel.scrollPanel(
    x: Int,
    y: Int,
    width: Int = 18,
    height: Int = 18,
    init: WPlainPanel.() -> Unit = {}
): WScrollPanel {
    val plainPanel = WPlainPanel().also { it.init() }
    val scrollPanel = WScrollPanel(plainPanel)
    this.add(scrollPanel, x, y, width, height)
    return scrollPanel
}

@Environment(EnvType.CLIENT)
fun WPlainPanel.image(
    x: Int,
    y: Int,
    width: Int = 18,
    height: Int = 18,
    imagePath: Identifier,
    init: WSprite.() -> Unit = {}
): WSprite {
    val sprite = WSprite(imagePath)
    sprite.init()
    this.add(sprite, x, y, width, height)
    return sprite
}

@Environment(EnvType.CLIENT)
fun WPlainPanel.button(
    x: Int,
    y: Int,
    width: Int = 18,
    height: Int = 20,
    icon: Icon? = null,
    label: Text? = null,
    enabled: Boolean = true,
    textAlignment: HorizontalAlignment = HorizontalAlignment.CENTER,
    onClick: WButton.() -> Unit = {},
    init: WButton.() -> Unit = {}
): WButton {
    val button = WButton(icon, label)
    button.init()
    button.isEnabled = enabled
    button.alignment = textAlignment
    button.setOnClick { onClick.invoke(button) }
    this.add(button, x, y, width, height)
    return button
}

@Environment(EnvType.CLIENT)
fun WPlainPanel.label(
    x: Int,
    y: Int,
    width: Int? = null,
    height: Int = 18,
    text: Text,
    color: Int = WLabel.DEFAULT_TEXT_COLOR,
    darkModeColor: Int = WLabel.DEFAULT_DARKMODE_TEXT_COLOR,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.LEFT,
    verticalAlignment: VerticalAlignment = VerticalAlignment.TOP,
    init: WLabel.() -> Unit = {}
): WLabel {
    val label = WLabel(text)
    val tWidth = width ?: MinecraftClient.getInstance().textRenderer.getWidth(text)
    label.horizontalAlignment = horizontalAlignment
    label.verticalAlignment = verticalAlignment
    label.color = color
    label.darkmodeColor = darkModeColor
    label.init()
    this.add(label, x, y, tWidth, height)
    return label
}


@Environment(EnvType.CLIENT)
fun WPlainPanel.dynamicLabel(
    x: Int,
    y: Int,
    width: Int? = null,
    height: Int? = null,
    text: () -> String,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.LEFT,
    color: Int = WDynamicLabel.DEFAULT_TEXT_COLOR,
    darkModeColor: Int = WDynamicLabel.DEFAULT_DARKMODE_TEXT_COLOR,
    init: WDynamicLabel.() -> Unit = {}
): WDynamicLabel {
    val label = WDynamicLabel(text)
    label.setAlignment(horizontalAlignment)
    label.setColor(color, darkModeColor)
    label.init()
    this.add(label, x, y,width ?: label.width,height ?: label.height)
    return label
}

@Environment(EnvType.CLIENT)
fun WPlainPanel.text(
    x: Int,
    y: Int,
    width: Int? = null,
    height: Int = 18,
    text: Text,
    color: Int = WLabel.DEFAULT_TEXT_COLOR,
    darkModeColor: Int = WLabel.DEFAULT_DARKMODE_TEXT_COLOR,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.LEFT,
    verticalAlignment: VerticalAlignment = VerticalAlignment.TOP,
    init: WText.() -> Unit = {}
): WText {
    val wText = WText(text)
    val tWidth = width ?: MinecraftClient.getInstance().textRenderer.getWidth(text)
    wText.horizontalAlignment = horizontalAlignment
    wText.verticalAlignment = verticalAlignment
    wText.color = color
    wText.darkmodeColor = darkModeColor
    wText.init()
    this.add(wText, x, y, tWidth, height)
    return wText
}


@Environment(EnvType.CLIENT)
fun WPlainPanel.item(
    x: Int,
    y: Int,
    width: Int = 18,
    height: Int = 18,
    itemStack: ItemStack,
    init: WItem.() -> Unit = {}
): WItem {
    val item = WItem(itemStack)
    item.init()
    this.add(item, x, y, width, height)
    return item
}

@Environment(EnvType.CLIENT)
fun WPlainPanel.textField(
    x: Int,
    y: Int,
    width: Int = 18,
    height: Int = 18,
    text: String? = null,
    suggestion: Text? = null,
    init: WTextField.() -> Unit = {}
): WTextField {
    val textField = WTextField(suggestion)
    text?.let { textField.text = it }
    textField.init()
    this.add(textField, x, y, width, height)
    return textField
}

@Environment(EnvType.CLIENT)
fun WPlainPanel.botFace(
    x: Int,
    y: Int,
    width: Int = 18,
    height: Int = 18,
    code: Int? = null,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.CENTER,
    init: WidgetBotFace.() -> Unit = {}
): WidgetBotFace {
    val botFace = WidgetBotFace(code, horizontalAlignment)
    botFace.init()
    this.add(botFace, x, y, width, height)
    return botFace
}

@Environment(EnvType.CLIENT)
fun WPlainPanel.playerFace(
    x: Int,
    y: Int,
    width: Int = 18,
    height: Int = 18,
    uuid: UUID,
    name: String,
    init: WidgetPlayerFace.() -> Unit = {}
): WidgetPlayerFace {
    val playerFace = WidgetPlayerFace(uuid, name)
    playerFace.init()
    this.add(playerFace, x, y, width, height)
    return playerFace
}

@Environment(EnvType.CLIENT)
fun WPlainPanel.tooltipButton(
    x: Int,
    y: Int,
    width: Int = 18,
    icon: Icon? = null,
    label: Text? = null,
    textAlignment: HorizontalAlignment = HorizontalAlignment.CENTER,
    tooltip: Array<Text>,
    onClick: WidgetTooltipButton.() -> Unit = {},
    init: WidgetTooltipButton.() -> Unit = {}
): WidgetTooltipButton {
    val button = WidgetTooltipButton(icon, label, tooltip)
    button.init()
    button.alignment = textAlignment
    button.setOnClick { onClick.invoke(button) }
    this.add(button, x, y, width, 20)
    return button
}

@Environment(EnvType.CLIENT)
fun WPlainPanel.tooltipTextField(
    x: Int,
    y: Int,
    width: Int = 18,
    height: Int = 18,
    text: String? = null,
    suggestion: Text? = null,
    tooltip: Array<Text>,
    init: WidgetTooltipTextField.() -> Unit = {}
): WidgetTooltipTextField {
    val textField = WidgetTooltipTextField(suggestion, tooltip)
    text?.let { textField.text = it }
    textField.init()
    this.add(textField, x, y, width, height)
    return textField
}


@Environment(EnvType.CLIENT)
fun WPlainPanel.tooltipText(
    x: Int,
    y: Int,
    width: Int = 18,
    height: Int = 18,
    text: Text,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.LEFT,
    verticalAlignment: VerticalAlignment = VerticalAlignment.TOP,
    tooltip: Array<Text>,
    init: WidgetTooltipText.() -> Unit = {}
): WidgetTooltipText {
    val wText = WidgetTooltipText(text, tooltip)
    wText.init()
    wText.horizontalAlignment = horizontalAlignment
    wText.verticalAlignment = verticalAlignment
    this.add(wText, x, y, width, height)
    return wText
}

@Environment(EnvType.CLIENT)
fun WPlainPanel.colorBlock(
    x: Int,
    y: Int,
    width: Int = 18,
    height: Int = 18,
    color: Color,
    init: WidgetColorBlock.() -> Unit = {}
): WidgetColorBlock = colorBlock(x, y, width, height, color.toRgb(), init)

@Environment(EnvType.CLIENT)
fun WPlainPanel.colorBlock(
    x: Int,
    y: Int,
    width: Int = 18,
    height: Int = 18,
    color: Int,
    init: WidgetColorBlock.() -> Unit = {}
): WidgetColorBlock {
    val block = WidgetColorBlock(color)
    block.init()
    this.add(block, x, y, width, height)
    return block
}

@Environment(EnvType.CLIENT)
fun WPlainPanel.mahjongTile(
    x: Int,
    y: Int,
    width: Int = 18,
    height: Int = 18,
    mahjongTile: MahjongTile,
    direction: WidgetMahjongTile.TileDirection = WidgetMahjongTile.TileDirection.NORMAL,
    init: WidgetMahjongTile.() -> Unit = {}
): WidgetMahjongTile {
    val tile = WidgetMahjongTile(mahjongTile, direction)
    tile.init()
    this.add(tile, x, y, width, height)
    return tile
}