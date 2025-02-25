package mahjong.util

import mahjong.scheduler.client.ClientScheduler
import mahjong.scheduler.server.ServerScheduler
import kotlinx.coroutines.delay
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment


suspend fun delayOnServer(timeMills: Long) {
    var completed = false
    ServerScheduler.scheduleDelayAction(delay = timeMills) { completed = true }
    while (!completed) delay(10)
}


@Environment(EnvType.CLIENT)
suspend fun delayOnClient(timeMills: Long) {
    var completed = false
    ClientScheduler.scheduleDelayAction(delay = timeMills) { completed = true }
    while (!completed) delay(10)
}