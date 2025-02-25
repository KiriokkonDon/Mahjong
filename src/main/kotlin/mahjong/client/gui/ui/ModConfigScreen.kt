package mahjong.client.gui.ui

import mahjong.MOD_ID
import mahjong.MahjongClient
import mahjong.client.ModConfig
import mahjong.client.gui.config.*
import mahjong.game.game_logic.MahjongGameBehavior
import mahjong.network.MahjongGamePayload
import mahjong.network.sendPayloadToServer
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.option.KeyBinding
import net.minecraft.text.Text
import java.util.*

object ModConfigScreen {
    private val title = Text.translatable("config.$MOD_ID.title")


    private val displayTableLabelsText = Text.translatable("config.$MOD_ID.display_table_labels")
    private val hudBackgroundColorText = Text.translatable("config.$MOD_ID.hud.background_color")
    private val hudScaleText = Text.translatable("config.$MOD_ID.hud.scale")
    private val displayHudText = Text.translatable("config.$MOD_ID.hud.display")
    private val displayHudWhenPlayingText = Text.translatable("config.$MOD_ID.hud.display_when_playing")


    private val tileHintsText = Text.translatable("config.$MOD_ID.tile_hints")


    private val quickActionsText = Text.translatable("config.$MOD_ID.quick_actions")
    private val autoArrangeText = Text.translatable("config.$MOD_ID.quick_actions.auto_arrange")
    private val autoCallWinText = Text.translatable("config.$MOD_ID.quick_actions.auto_call_win")
    private val noChiiPonKanText = Text.translatable("config.$MOD_ID.quick_actions.no_chii_pon_kan")
    private val autoDrawAndDiscardText = Text.translatable("config.$MOD_ID.quick_actions.auto_draw_and_discard")
    private val autoResetTooltip = Text.translatable("config.$MOD_ID.tooltip.auto_reset")

    private val hudPositionEditorKey = MahjongClient.hudPositionEditorKey
    private val hudPositionEditorTooltip = Text.translatable(
        "config.$MOD_ID.tooltip.hud_position_editor",
        hudPositionEditorKey.boundKeyLocalizedText
    )

    private val yakuOverviewKey = MahjongClient.yakuOverviewKey
    private val yakuOverviewTooltip = Text.translatable(
        "config.$MOD_ID.tooltip.yaku_overview",
        yakuOverviewKey.boundKeyLocalizedText
    )

    fun build(parent: Screen? = null): Screen = getConfigBuilder(parent).build()

    private fun getConfigBuilder(parent: Screen? = null): ConfigBuilder {
        val config = MahjongClient.config
        val defaultConfig = ModConfig()
        return configBuilder(title = title, parent = parent, savingRunnable = { MahjongClient.saveConfig() }) {
            category(Text.of("general")) {
                booleanToggle(
                    text = displayTableLabelsText,
                    startValue = config.displayTableLabels,
                    defaultValue = { defaultConfig.displayTableLabels },
                ) { config.displayTableLabels = it }
                keyCodeField(
                    text = Text.translatable(hudPositionEditorKey.translationKey),
                    startKey = KeyBindingHelper.getBoundKeyOf(hudPositionEditorKey),
                    defaultKey = { hudPositionEditorKey.defaultKey },
                    tooltipSupplier = { Optional.of(arrayOf(hudPositionEditorTooltip)) },
                    saveConsumer = {
                        hudPositionEditorKey.setBoundKey(it)
                        KeyBinding.updateKeysByCode()
                        MinecraftClient.getInstance().options.write()
                    }
                )
                keyCodeField(
                    text = Text.translatable(yakuOverviewKey.translationKey),
                    startKey = KeyBindingHelper.getBoundKeyOf(yakuOverviewKey),
                    defaultKey = { yakuOverviewKey.defaultKey },
                    tooltipSupplier = { Optional.of(arrayOf(yakuOverviewTooltip)) },
                    saveConsumer = {
                        yakuOverviewKey.setBoundKey(it)
                        KeyBinding.updateKeysByCode()
                        MinecraftClient.getInstance().options.write()
                    }
                )
                subCategory(text = tileHintsText) {
                    booleanToggle(
                        text = displayHudText,
                        startValue = config.tileHints.displayHud,
                        defaultValue = { defaultConfig.tileHints.displayHud }
                    ) {
                        config.tileHints.displayHud = it
                        MahjongClient.hud?.refresh()
                    }
                    intSlider(
                        text = hudScaleText,
                        startValue = (config.tileHints.hudAttribute.scale * 100.0).toInt(),
                        minValue = 0,
                        maxValue = 500,
                        defaultValue = { (defaultConfig.tileHints.hudAttribute.scale * 100.0).toInt() },
                        textGetter = { Text.of("$it %") }
                    ) {
                        config.tileHints.hudAttribute.scale = it / 100.0
                        MahjongClient.hud?.refresh()
                    }
                    alphaColorField(
                        text = hudBackgroundColorText,
                        startColor = config.tileHints.hudAttribute.backgroundColor,
                        defaultColor = { defaultConfig.tileHints.hudAttribute.backgroundColor }
                    ) {
                        config.tileHints.hudAttribute.backgroundColor = it
                        MahjongClient.hud?.refresh()
                    }
                }
                subCategory(text = quickActionsText) {
                    booleanToggle(
                        text = displayHudWhenPlayingText,
                        startValue = config.quickActions.displayHudWhenPlaying,
                        defaultValue = { defaultConfig.quickActions.displayHudWhenPlaying }
                    ) {
                        config.quickActions.displayHudWhenPlaying = it
                        MahjongClient.hud?.refresh()
                    }
                    alphaColorField(
                        text = hudBackgroundColorText,
                        startColor = config.quickActions.hudAttribute.backgroundColor,
                        defaultColor = { defaultConfig.quickActions.hudAttribute.backgroundColor }
                    ) {
                        config.quickActions.hudAttribute.backgroundColor = it
                        MahjongClient.hud?.refresh()
                    }
                    booleanToggle(
                        text = autoArrangeText,
                        startValue = config.quickActions.autoArrange,
                        defaultValue = { defaultConfig.quickActions.autoArrange }
                    ) {
                        config.quickActions.autoArrange = it
                        val world = MinecraftClient.getInstance().world
                        if (world != null) {
                            sendPayloadToServer(
                                payload = MahjongGamePayload(
                                    behavior = MahjongGameBehavior.AUTO_ARRANGE,
                                    extraData = it.toString()
                                )
                            )
                        }
                    }
                    booleanToggle(
                        text = autoCallWinText,
                        startValue = config.quickActions.autoCallWin,
                        defaultValue = { defaultConfig.quickActions.autoCallWin },
                        tooltipSupplier = { Optional.of(arrayOf(autoResetTooltip)) }
                    ) { config.quickActions.autoCallWin = it }
                    booleanToggle(
                        text = noChiiPonKanText,
                        startValue = config.quickActions.noChiiPonKan,
                        defaultValue = { defaultConfig.quickActions.noChiiPonKan },
                        tooltipSupplier = { Optional.of(arrayOf(autoResetTooltip)) }
                    ) { config.quickActions.noChiiPonKan = it }
                    booleanToggle(
                        text = autoDrawAndDiscardText,
                        startValue = config.quickActions.autoDrawAndDiscard,
                        defaultValue = { defaultConfig.quickActions.autoDrawAndDiscard },
                        tooltipSupplier = { Optional.of(arrayOf(autoResetTooltip)) }
                    ) { config.quickActions.autoDrawAndDiscard = it }
                }
            }
        }
    }
}