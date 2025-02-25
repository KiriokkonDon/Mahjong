package mahjong.network

import mahjong.block.MahjongTableBlockEntity
import mahjong.client.gui.ui.MahjongTableWaitingScreen
import mahjong.client.gui.ui.RuleEditorScreen
import mahjong.game.GameManager
import mahjong.game.GameStatus
import mahjong.game.MahjongGame
import mahjong.game.game_logic.MahjongRule
import mahjong.game.game_logic.MahjongTableBehavior
import mahjong.game.player.MahjongBot
import mahjong.logger
import mahjong.scheduler.client.ClientScheduler
import mahjong.scheduler.server.ServerScheduler
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos


object MahjongTablePayloadListener : CustomPayloadListener<MahjongTablePayload> {

    override val id: CustomPayload.Id<MahjongTablePayload> = MahjongTablePayload.ID

    override val codec: PacketCodec<RegistryByteBuf, MahjongTablePayload> = MahjongTablePayload.CODEC

    override val channelType: ChannelType = ChannelType.Both

    @Environment(EnvType.CLIENT)
    override fun onClientReceive(
        payload: MahjongTablePayload,
        context: ClientPlayNetworking.Context,
    ) {
        val (behavior, pos, _) = payload
        val client = context.client()
        val world = client.world ?: return

        when (behavior) {
            MahjongTableBehavior.OPEN_TABLE_WAITING_GUI -> {
                ClientScheduler.scheduleDelayAction {
                    val mahjongTableBlockEntity =
                        world.getBlockEntity(pos) as MahjongTableBlockEntity? ?: return@scheduleDelayAction
                    client.setScreen(MahjongTableWaitingScreen(mahjongTable = mahjongTableBlockEntity))
                }
            }
            MahjongTableBehavior.OPEN_RULES_EDITOR_GUI -> {
                ClientScheduler.scheduleDelayAction {
                    val mahjongTableBlockEntity =
                        world.getBlockEntity(pos) as MahjongTableBlockEntity? ?: return@scheduleDelayAction
                    client.setScreen(RuleEditorScreen(mahjongTable = mahjongTableBlockEntity))
                }
            }
            else -> {
            }
        }
    }

    override fun onServerReceive(
        payload: MahjongTablePayload,
        context: ServerPlayNetworking.Context,
    ) {
        val (behavior, pos, extraData) = payload
        val player = context.player()
        val world = player.world as ServerWorld? ?: return

        when (behavior) {
            MahjongTableBehavior.JOIN -> syncBlockEntityWithGame(world = world, pos = pos) {
                if (status == GameStatus.WAITING) join(player)
            }
            MahjongTableBehavior.LEAVE -> syncBlockEntityWithGame(world = world, pos = pos) {
                if (status == GameStatus.WAITING) leave(player)
            }
            MahjongTableBehavior.READY -> syncBlockEntityWithGame(world = world, pos = pos) {
                if (status == GameStatus.WAITING) readyOrNot(player, ready = true)
            }
            MahjongTableBehavior.NOT_READY -> syncBlockEntityWithGame(world = world, pos = pos) {
                if (status == GameStatus.WAITING) readyOrNot(player, ready = false)
            }
            MahjongTableBehavior.START -> {
                val game = GameManager.getGame<MahjongGame>(world, pos)
                if (game != null && game.isHost(player) && game.status == GameStatus.WAITING) {
                    game.start()
                }
            }
            MahjongTableBehavior.KICK -> syncBlockEntityWithGame(world = world, pos = pos) {
                if (isHost(player) && status == GameStatus.WAITING) kick(index = extraData.toInt())
            }
            MahjongTableBehavior.ADD_BOT -> syncBlockEntityWithGame(world = world, pos = pos) {
                if (isHost(player) && status == GameStatus.WAITING) addBot()
            }
            MahjongTableBehavior.OPEN_RULES_EDITOR_GUI -> {
                val game = GameManager.getGame<MahjongGame>(world, pos)
                if (game != null && game.isHost(player) && game.status == GameStatus.WAITING) {
                    sendPayloadToPlayer(
                        player = player,
                        payload = MahjongTablePayload(
                            behavior = MahjongTableBehavior.OPEN_RULES_EDITOR_GUI,
                            pos = pos,
                            extraData = game.rule.toJsonString()
                        )
                    )
                }
            }
            MahjongTableBehavior.CHANGE_RULE -> syncBlockEntityWithGame(world = world, pos = pos) {
                if (isHost(player) && status == GameStatus.WAITING) changeRules(MahjongRule.fromJsonString(extraData))
            }
            else -> {
            }
        }
    }


    private fun syncBlockEntityWithGame(
        invokeOnNextTick: Boolean = true,
        world: ServerWorld,
        pos: BlockPos,
        apply: MahjongGame.() -> Unit = {},
    ) {
        val game = GameManager.getGame<MahjongGame>(world, pos) ?: return
        syncBlockEntityWithGame(invokeOnNextTick, game, apply)
    }


    fun syncBlockEntityWithGame(
        invokeOnNextTick: Boolean = true,
        game: MahjongGame,
        apply: MahjongGame.() -> Unit = {},
    ) {
        val syncAction = {
            game.apply()
            val world = game.world
            val pos = game.pos
            val blockEntity = world.getBlockEntity(pos) as MahjongTableBlockEntity?
            if (blockEntity != null) {
                syncBlockEntityDataWithGame(blockEntity, game)
            } else {
                logger.error("Cannot find a MahjongTableBlockEntity at (world=$world,pos=$pos)")
            }
        }
        if (invokeOnNextTick) {
            ServerScheduler.scheduleDelayAction { syncAction.invoke() }
        } else {
            syncAction.invoke()
        }
    }


    fun syncBlockEntityDataWithGame(
        blockEntity: MahjongTableBlockEntity,
        game: MahjongGame,
    ) {
        with(blockEntity) {
            game.also {
                repeat(4) { i ->
                    this.players[i] = it.players.getOrNull(i)?.uuid ?: ""
                    this.playerEntityNames[i] = it.players.getOrNull(i)?.entity?.name?.string ?: ""
                    this.bots[i] = it.players.getOrNull(i) is MahjongBot
                    this.ready[i] = it.players.getOrNull(i)?.ready ?: false
                    this.seat[i] = it.seat.getOrNull(i)?.uuid ?: ""
                    this.points[i] = it.seat.getOrNull(i)?.points ?: 0
                }
                this.rule = it.rule
                this.playing = it.status == GameStatus.PLAYING
                this.round = it.round
                this.dealer = it.seat.getOrNull(it.round.round)?.uuid ?: ""
            }
            this.markDirty()
        }
    }
}