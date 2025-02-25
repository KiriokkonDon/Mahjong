package mahjong.scheduler.client

import mahjong.client.gui.ui.YakuSettlementScreen
import mahjong.game.game_logic.YakuSettlement
import mahjong.util.delayOnClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen

//экран отображения яку
object YakuSettleHandler {
    const val defaultTime = 10

    private val client = MinecraftClient.getInstance()
    private lateinit var screen: Screen
    private var jobCountdown: Job? = null

    var time = 0
        private set

    @Environment(EnvType.CLIENT)
    private fun setScreen(settlements: List<YakuSettlement>) {
        ClientScheduler.scheduleDelayAction {
            screen = YakuSettlementScreen(settlements = settlements)
            client.setScreen(screen)
        }
    }

    @Environment(EnvType.CLIENT)
    private fun closeScreen() {
        CoroutineScope(Dispatchers.Default).launch {
            if (client.currentScreen == screen) {
                ClientScheduler.scheduleDelayAction { screen.close() }
            }
        }
    }

    @Environment(EnvType.CLIENT)
    fun start(settlementList: List<YakuSettlement>) {
        jobCountdown?.cancel()
        jobCountdown = CoroutineScope(Dispatchers.Default).launch {
            time = defaultTime * settlementList.size
            setScreen(settlements = settlementList)
            repeat(times = time) {
                delayOnClient(1000)
                time--
                if (time <= 0) closeScreen()
            }
        }
    }
}