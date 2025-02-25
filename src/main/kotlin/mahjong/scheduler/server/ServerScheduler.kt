package mahjong.scheduler.server

import mahjong.game.GameManager
import mahjong.logger
import mahjong.scheduler.ActionBase
import mahjong.scheduler.DelayAction
import mahjong.scheduler.LoopAction
import mahjong.scheduler.RepeatAction
import net.minecraft.server.MinecraftServer

object ServerScheduler {

    private val queuedActions = mutableListOf<ActionBase>()
    private val loopActions = mutableListOf<LoopAction>()

    fun tick(server: MinecraftServer) {
        if (!server.isRunning) return


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

    fun removeQueuedAction(action: ActionBase): Boolean = queuedActions.remove(action)

    fun onStopping(server: MinecraftServer) {
        queuedActions.clear()
        loopActions.clear()
        GameManager.games.forEach { it.onServerStopping(server) }
        logger.info("${GameManager.games.size} games cleared")
    }
}