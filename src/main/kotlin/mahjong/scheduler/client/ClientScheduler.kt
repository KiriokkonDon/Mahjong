package mahjong.scheduler.client

import mahjong.scheduler.ActionBase
import mahjong.scheduler.DelayAction
import mahjong.scheduler.LoopAction
import mahjong.scheduler.RepeatAction
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient

//выполнения действий на клиентской стороне
@Environment(EnvType.CLIENT)
object ClientScheduler {

    private val queuedActions = mutableListOf<ActionBase>()
    private val loopActions = mutableListOf<LoopAction>()

    private var doesWorldExist: Boolean = false

    //1 tick = 50 ms
    fun tick(client: MinecraftClient) {
        if ((client.window == null) == doesWorldExist) {
            doesWorldExist = (client.world != null)
            loopActions.forEach { it.resetTimer() }
            queuedActions.filterIsInstance<RepeatAction>().forEach { it.resetTimer() }
        }
        if (client.world == null || client.player == null) {
            queuedActions.clear()
            return
        }

        val copyOfQueuedAction = queuedActions.toList()
        val copyOfLoopAction = loopActions.toList()
        copyOfQueuedAction.forEach { if (it.tick()) queuedActions.remove(it) }
        copyOfLoopAction.forEach { it.tick() }
    }

    fun scheduleDelayAction(delay: Long = 0, action: () -> Unit): DelayAction =
        DelayAction(delay, action).also { queuedActions += it }


    fun scheduleRepeatAction(times: Int, interval: Long = 0, action: () -> Unit): RepeatAction =
        RepeatAction(times, interval, action).also { queuedActions += it }

    fun scheduleLoopAction(interval: Long = 0, action: () -> Unit): LoopAction =
        LoopAction(interval, action).also { loopActions += it }

    fun onStopping() {
        queuedActions.clear()
        loopActions.clear()
    }
}