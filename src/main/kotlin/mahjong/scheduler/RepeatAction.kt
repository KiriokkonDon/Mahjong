package mahjong.scheduler

import mahjong.logger
import java.time.Instant

//выполняется заданное количество раз с заданным интервалом
class RepeatAction(
    val times: Int,
    private val interval: Long,
    override val action: () -> Unit
) : ActionBase {
    override var stop: Boolean = false
    override var timeToAction: Long = Instant.now().toEpochMilli() + interval
    var count: Int = 0

    override fun tick(): Boolean {
        if (stop) return true
        if (Instant.now().toEpochMilli() >= timeToAction) {
            kotlin.runCatching { action.invoke() }.onFailure { logger.error("Error when invoking RepeatAction", it) }
            count++
            if (count < times) resetTimer()
            else return true
        }
        return false
    }

    fun resetTimer() {
        timeToAction = Instant.now().toEpochMilli() + interval
    }
}