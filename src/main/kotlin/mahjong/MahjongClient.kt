package mahjong

import mahjong.client.ModConfig
import mahjong.client.gui.ui.ModConfigScreen
import mahjong.client.gui.ui.HudPositionEditorScreen
import mahjong.client.gui.ui.MahjongHud
import mahjong.client.gui.ui.YakuOverviewScreen
import mahjong.client.render.* // Импортируем классы рендеринга (отрисовки) для клиентской стороны
import mahjong.network.MahjongGamePayloadListener
import mahjong.network.MahjongTablePayloadListener
import mahjong.network.MahjongTileCodePayloadListener
import mahjong.registry.BlockEntityTypeRegistry
import mahjong.registry.EntityTypeRegistry
import mahjong.registry.ItemRegistry
import mahjong.scheduler.client.ClientScheduler
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import net.fabricmc.api.ClientModInitializer // Импорт интерфейса ClientModInitializer, необходим для клиентских модов
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.client.item.ClampedModelPredicateProvider
import net.minecraft.client.item.ModelPredicateProviderRegistry
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories
import net.minecraft.client.util.InputUtil
import net.minecraft.component.DataComponentTypes
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW

@Environment(EnvType.CLIENT)
object MahjongClient : ClientModInitializer {


    var playing = false
        set(value) {
            field = value
            hud?.refresh()
        }
    lateinit var config: ModConfig
        private set
    var hud: MahjongHud? = null

    // Горячие клавиши
    private val configKey: KeyBinding = registerKeyBinding( // Регистрация горячей клавиши для открытия GUI конфигурации
        translationKey = "key.$MOD_ID.open_config_gui",
        code = GLFW.GLFW_KEY_SEMICOLON, // Клавиша по умолчанию - ';' (точка с запятой)
    )
    val hudPositionEditorKey: KeyBinding = registerKeyBinding( // Горячая клавиша для редактора позиции HUD
        translationKey = "key.$MOD_ID.open_hud_position_editor",
    )
    val yakuOverviewKey: KeyBinding = registerKeyBinding(
        translationKey = "key.$MOD_ID.open_yaku_overview",
    )


    override fun onInitializeClient() {
        logger.info("Initializing client")
        ClientTickEvents.END_CLIENT_TICK.register(this::tick)
        ClientLifecycleEvents.CLIENT_STOPPING.register { ClientScheduler.onStopping() }

        // --- Entity Renderers (Отрисовщики сущностей) ---
        EntityRendererRegistry.register(EntityTypeRegistry.dice, ::DiceEntityRenderer)
        EntityRendererRegistry.register(EntityTypeRegistry.seat, ::SeatEntityRenderer)
        EntityRendererRegistry.register(EntityTypeRegistry.mahjongBot, ::BotEntityRenderer)
        EntityRendererRegistry.register(EntityTypeRegistry.mahjongScoringStick, ::ScoringStickEntityRenderer)
        EntityRendererRegistry.register(EntityTypeRegistry.mahjongTile, ::TileEntityRenderer)


        // --- BlockEntity Renderers (Отрисовщики тайловых сущностей блоков) ---
        BlockEntityRendererFactories.register(BlockEntityTypeRegistry.mahjongTable, ::TableBlockEntityRenderer)

        // --- Model Predicate Providers (Провайдеры предикатов моделей) ---
        // Используются для динамического изменения моделей предметов в зависимости от их NBT данных
        val modelPredicateProvider = ClampedModelPredicateProvider { stack, _, _, _ -> // Создаем провайдер предиката
            val code = stack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt()?.getInt("code") ?: 0
            code / 100f
        }
        ModelPredicateProviderRegistry.register( // Регистрируем провайдеры предикатов для предметов
            ItemRegistry.mahjongTile, // Предмет - плитка маджонга
            Identifier.of("code"),
            modelPredicateProvider
        )
        ModelPredicateProviderRegistry.register(
            ItemRegistry.mahjongScoringStick,
            Identifier.of("code"),
            modelPredicateProvider
        )


        // --- Packet Registration (Регистрация пакетов) ---
        MahjongTablePayloadListener.registerClient()
        MahjongGamePayloadListener.registerClient()
        MahjongTileCodePayloadListener.registerClient()


        // --- Config (Конфигурация) ---
        AutoConfig.register(ModConfig::class.java, ::GsonConfigSerializer) // Регистрируем AutoConfig для загрузки и сохранения конфигурации мода
        config = AutoConfig.getConfigHolder(ModConfig::class.java).config

        // --- HUD (Heads-Up Display - Отображение на экране) ---
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is TitleScreen && hud == null) hud = MahjongHud()
        }
        ScreenEvents.BEFORE_INIT.register { _, _, _, _ -> hud?.reposition() }

    }

    // --- saveConfig() ---
    fun saveConfig() = AutoConfig.getConfigHolder(ModConfig::class.java).save()

    // --- tick() ---
    private fun tick(client: MinecraftClient) { // Функция, вызываемая каждый клиентский тик (кадр)
        if (configKey.wasPressed()) client.setScreen(ModConfigScreen.build(null))
        if (hudPositionEditorKey.wasPressed()) hud?.also { client.setScreen(HudPositionEditorScreen(it)) }
        if (yakuOverviewKey.wasPressed()) client.setScreen(YakuOverviewScreen())
        ClientScheduler.tick(client)
    }

    // --- registerKeyBinding() ---
    private fun registerKeyBinding( // Функция для регистрации горячей клавиши
        translationKey: String,
        type: InputUtil.Type = InputUtil.Type.KEYSYM,
        code: Int = InputUtil.UNKNOWN_KEY.code,
        category: String = "key.category.$MOD_ID.main",
    ): KeyBinding = KeyBindingHelper.registerKeyBinding(KeyBinding(translationKey, type, code, category))
}