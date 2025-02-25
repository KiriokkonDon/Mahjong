package mahjong.network

import mahjong.MahjongClient
import mahjong.client.gui.gui_widgets.WidgetTileHints
import mahjong.game.GameManager
import mahjong.game.MahjongGame
import mahjong.game.game_logic.MahjongGameBehavior
import mahjong.game.game_logic.MahjongTile
import mahjong.game.game_logic.ScoreSettlement
import mahjong.game.game_logic.YakuSettlement
import mahjong.game.player.MahjongPlayer
import mahjong.scheduler.client.ClientCountdownTimeHandler
import mahjong.scheduler.client.OptionalBehaviorHandler
import mahjong.scheduler.client.ScoreSettleHandler
import mahjong.scheduler.client.YakuSettleHandler
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload


object MahjongGamePayloadListener : CustomPayloadListener<MahjongGamePayload> {

    override val id: CustomPayload.Id<MahjongGamePayload> = MahjongGamePayload.ID

    override val codec: PacketCodec<RegistryByteBuf, MahjongGamePayload> = MahjongGamePayload.CODEC

    override val channelType: ChannelType = ChannelType.Both

    @Environment(EnvType.CLIENT)
    override fun onClientReceive(
        payload: MahjongGamePayload,
        context: ClientPlayNetworking.Context,
    ) {
        val (behavior, hands, target, extraData) = payload

        when (behavior) {
            MahjongGameBehavior.MACHI -> {
                WidgetTileHints.machiOfTarget = Json.decodeFromString(extraData)
            }

            MahjongGameBehavior.COUNTDOWN_TIME -> {
                val times = Json.decodeFromString<Pair<Int?, Int?>>(extraData)
                ClientCountdownTimeHandler.basicAndExtraTime = times
            }

            MahjongGameBehavior.DISCARD -> {
                val skippable = extraData.toBoolean()
                if (skippable && MahjongClient.config.quickActions.autoDrawAndDiscard) {
                    sendPayloadToServer(
                        payload = MahjongGamePayload(behavior = MahjongGameBehavior.SKIP)
                    )
                }
            }

            MahjongGameBehavior.GAME_START -> {
                MahjongClient.playing = true
            }

            MahjongGameBehavior.GAME_OVER -> {
                MahjongClient.playing = false
                OptionalBehaviorHandler.cancel()
            }

            MahjongGameBehavior.SCORE_SETTLEMENT -> {
                with(MahjongClient.config.quickActions) {
                    autoCallWin = false
                    noChiiPonKan = false
                    autoDrawAndDiscard = false
                    MahjongClient.saveConfig()
                }
                val settlement = Json.decodeFromString<ScoreSettlement>(extraData)
                ScoreSettleHandler.start(settlement = settlement)
            }

            MahjongGameBehavior.YAKU_SETTLEMENT -> {
                val settlements = Json.decodeFromString<List<YakuSettlement>>(extraData)
                YakuSettleHandler.start(settlementList = settlements)
            }

            MahjongGameBehavior.AUTO_ARRANGE -> {
                sendPayloadToServer(
                    payload = MahjongGamePayload(
                        behavior = behavior,
                        extraData = MahjongClient.config.quickActions.autoArrange.toString()
                    )
                )
            }

            else -> {
                val quickActions = MahjongClient.config.quickActions
                when (behavior) {
                    MahjongGameBehavior.RON, MahjongGameBehavior.TSUMO ->
                        if (quickActions.autoCallWin) {
                            sendPayloadToServer(
                                payload = MahjongGamePayload(behavior = behavior)
                            )
                            return
                        }

                    MahjongGameBehavior.CHII, MahjongGameBehavior.PON_OR_CHII, MahjongGameBehavior.PON,
                    MahjongGameBehavior.ANKAN_OR_KAKAN, MahjongGameBehavior.MINKAN,
                    ->
                        if (quickActions.noChiiPonKan) {
                            sendPayloadToServer(
                                payload = MahjongGamePayload(behavior = MahjongGameBehavior.SKIP)
                            )
                            return
                        }

                    else -> {}
                }
                OptionalBehaviorHandler.start(behavior, hands, target, extraData)
            }
        }
    }

    override fun onServerReceive(
        payload: MahjongGamePayload,
        context: ServerPlayNetworking.Context,
    ) {
        val (behavior, _, _, extraData) = payload
        val player = context.player()

        val game = GameManager.getGame<MahjongGame>(player) ?: return
        val mjPlayer = game.getPlayer(player) as MahjongPlayer? ?: return

        when (behavior) {
            MahjongGameBehavior.MACHI -> {
                val tile = Json.decodeFromString<MahjongTile>(extraData)
                val machiAndHanOrigin = with(game) { mjPlayer.getMachiAndHan(tile) }
                val machiAndHan = machiAndHanOrigin.keys
                    .map { MahjongTile.entries[it.mahjong4jTile.code] }
                    .associateWith {
                        machiAndHanOrigin[it] to with(game) {
                            mjPlayer.isFuriten(
                                tile = it,
                                machi = machiAndHanOrigin.keys.toList()
                            )
                        }
                    }

                sendPayloadToPlayer(
                    player = player,
                    payload = MahjongGamePayload(
                        behavior = MahjongGameBehavior.MACHI,
                        extraData = Json.encodeToString(machiAndHan)
                    )
                )
            }

            MahjongGameBehavior.AUTO_ARRANGE -> {
                mjPlayer.autoArrangeHands = extraData.toBoolean()
            }

            in mjPlayer.waitingBehavior -> {
                mjPlayer.behaviorResult = behavior to extraData
            }

            else -> {}
        }
    }
}