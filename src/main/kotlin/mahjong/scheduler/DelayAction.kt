package mahjong.scheduler

import mahjong.logger
import java.time.Instant

//действие, которое выполняется один раз через заданную задержку.
class DelayAction(
    delay: Long,
    override val action: () -> Unit
) : ActionBase {
    override var stop: Boolean = false
    override var timeToAction: Long = Instant.now().toEpochMilli() + delay

    override fun tick(): Boolean {
        return when {
            stop -> true
            Instant.now().toEpochMilli() >= timeToAction -> {
                kotlin.runCatching { action.invoke() }.onFailure { logger.error("Error when invoking DelayAction", it) }
                true
            }
            else -> false
        }
    }

}