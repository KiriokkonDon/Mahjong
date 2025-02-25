package mahjong.client

import mahjong.MOD_ID
import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config

private const val DEFAULT_COLOR_RED = 0x7f271c1d
private const val DEFAULT_COLOR_GREEN = 0x7f597d2a


@Config(name = MOD_ID)
data class ModConfig(
    var displayTableLabels: Boolean = true,
    val tileHints: TileHints = TileHints(),
    val quickActions: QuickActions = QuickActions()
) : ConfigData {

    //содержит параметры отображения HUD-подсказок для плиток
    data class TileHints(
        var displayHud: Boolean = true,
        val hudAttribute: HudAttribute = HudAttribute(0.0, 0.6, DEFAULT_COLOR_GREEN)
    ) : ConfigData

    //быстрое взаимодействие,
    // автоматическая сортировка плиток,
    // авто-взятие карт, авто-завершение игры и т.д
    data class QuickActions(
        var displayHudWhenPlaying: Boolean = true,
        val hudAttribute: HudAttribute = HudAttribute(0.0, 0.6, DEFAULT_COLOR_RED),
        var autoArrange: Boolean = true,
        var autoCallWin: Boolean = false,
        var noChiiPonKan: Boolean = false,
        var autoDrawAndDiscard: Boolean = false
    ) : ConfigData
}


data class HudAttribute(
    var x: Double,
    var y: Double,
    var backgroundColor: Int,
    var scale: Double = 1.0
) : ConfigData