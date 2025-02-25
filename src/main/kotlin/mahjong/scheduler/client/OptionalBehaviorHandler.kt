package mahjong.scheduler.client

import mahjong.client.gui.ui.MahjongGameBehaviorScreen
import mahjong.game.game_logic.ClaimTarget
import mahjong.game.game_logic.MahjongGameBehavior
import mahjong.game.game_logic.MahjongTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen

//Управление отображением экрана выбора действия (например, при запросе на Чи, Пон, Кан)
@Environment(EnvType.CLIENT)
object OptionalBehaviorHandler {

    private val client = MinecraftClient.getInstance()
    private var screen: Screen? = null

    private lateinit var behavior: MahjongGameBehavior
    private lateinit var hands: List<MahjongTile>
    private lateinit var target: ClaimTarget
    private lateinit var extraData: String

    var waiting: Boolean = false
        private set

    fun setScreen() {
        ClientScheduler.scheduleDelayAction {
            screen = MahjongGameBehaviorScreen(behavior, hands, target, extraData)
            client.setScreen(screen!!)
        }
    }

    private fun closeScreen() {
        CoroutineScope(Dispatchers.Default).launch {
            val nowScreen = screen
            if (nowScreen != null && client.currentScreen == nowScreen) {
                ClientScheduler.scheduleDelayAction { nowScreen.close() }
            }
        }
    }

    fun start(
        behavior: MahjongGameBehavior,
        hands: List<MahjongTile>,
        target: ClaimTarget,
        extraData: String
    ) {
        waiting = true
        this.behavior = behavior
        this.hands = hands
        this.target = target
        this.extraData = extraData
        setScreen()
    }

    fun cancel() {
        waiting = false
        closeScreen()
    }
}