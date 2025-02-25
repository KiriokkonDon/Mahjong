package mahjong.scheduler.client

import mahjong.client.gui.ui.ScoreSettlementScreen
import mahjong.game.game_logic.ScoreSettlement
import mahjong.util.delayOnClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen

// Управление отображением экрана результатов подсчета очков (ScoreSettlementScreen)
object ScoreSettleHandler {
    const val defaultTime = 5

    private val client = MinecraftClient.getInstance()
    private lateinit var screen: Screen
    private var jobCountdown: Job? = null

    var time = 0
        private set


    @Environment(EnvType.CLIENT)
    private fun setScreen(settlement: ScoreSettlement) {
        ClientScheduler.scheduleDelayAction {
            screen = ScoreSettlementScreen(settlement = settlement)
            client.setScreen(screen)
        }
    }


    @Environment(EnvType.CLIENT)
    fun closeScreen() {
        CoroutineScope(Dispatchers.Default).launch {
            if (client.currentScreen == screen) {
                ClientScheduler.scheduleDelayAction { screen.close() }
            }
        }
    }

    @Environment(EnvType.CLIENT)
    fun start(settlement: ScoreSettlement) {
        jobCountdown?.cancel()
        jobCountdown = CoroutineScope(Dispatchers.Default).launch {
            time = defaultTime
            setScreen(settlement = settlement)
            repeat(times = time) {
                delayOnClient(1000)
                time--
                if (time <= 0) closeScreen()
            }
        }
    }
}