package mahjong.client.gui.gui_widgets

import io.github.cottonmc.cotton.gui.widget.TooltipBuilder
import io.github.cottonmc.cotton.gui.widget.WButton
import io.github.cottonmc.cotton.gui.widget.icon.Icon
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.text.Text


class WidgetTooltipButton(
    icon: Icon? = null,
    label: Text? = null,
    var tooltip: Array<Text>
) : WButton(icon, label) {
    @Environment(EnvType.CLIENT)
    override fun addTooltip(tooltip: TooltipBuilder) {
        tooltip.add(*this.tooltip)
    }
}