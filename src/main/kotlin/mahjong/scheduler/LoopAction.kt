package mahjong.scheduler

import mahjong.logger
import java.time.Instant

//действие, которое выполняется постоянно с заданным интервалом
class LoopAction(
    private val interval: Long,
    override val action: () -> Unit
) : ActionBase {
    override var stop: Boolean = false
    override var timeToAction: Long = Instant.now().toEpochMilli() + interval


    override fun tick(): Boolean {
        if (stop) return true
        if (Instant.now().toEpochMilli() >= timeToAction) {
            kotlin.runCatching { action.invoke() }.onFailure { logger.error("Error when invoking LoopAction", it) }
            resetTimer()
        }
        return false
    }

    fun resetTimer() {
        timeToAction = Instant.now().toEpochMilli() + interval
    }
}