package mahjong.scheduler.client

import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

//Управление отображением таймера обратного отсчета на HUD игры.
// Используется ClientScheduler.scheduleDelayAction для отображения и скрытия таймера
object ClientCountdownTimeHandler {

    private val client = MinecraftClient.getInstance()
    private const val titleFadeInTime = 5
    private const val titleRemainTime = 10
    private const val titleFadeOutTime = 5


    var basicAndExtraTime: Pair<Int?, Int?> = null to null
        set(value) {
            if (value.first != null && value.second != null) {
                displayTime(timeBase = value.first!!, timeExtra = value.second!!)
            } else {
                if (OptionalBehaviorHandler.waiting) OptionalBehaviorHandler.cancel()
                client.inGameHud.clearTitle()
            }
            field = value
        }



    private fun displayTime(timeBase: Int, timeExtra: Int) {
        val base = if (timeBase > 0) "§a$timeBase" else ""
        val plus = if (timeBase > 0 && timeExtra > 0) "§e + " else ""
        val extra = if (timeExtra > 0) "§c$timeExtra" else ""
        val text = Text.of("$base$plus$extra")
        with(client.inGameHud) {
            setTitle(Text.of(""))
            setSubtitle(text)
            setTitleTicks(titleFadeInTime, titleRemainTime, titleFadeOutTime)
        }
    }
}