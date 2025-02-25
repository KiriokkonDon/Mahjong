package mahjong.game

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

object GameManager {


    val games = mutableListOf<GameBase<*>>()


    fun isInAnyGame(player: ServerPlayerEntity): Boolean =
        getGameBy(player) != null


    fun getGameBy(player: ServerPlayerEntity): GameBase<*>? =
        games.find { it.getPlayer(player) != null }


    inline fun <reified T : GameBase<*>> getGame(player: ServerPlayerEntity): T? =
        games.filterIsInstance<T>().find { it.getPlayer(player) != null }


    inline fun <reified T : GameBase<*>> getGame(world: ServerWorld, pos: BlockPos): T? =
        games.filterIsInstance<T>().find { it.world == world && it.pos == pos }

    inline fun <reified T : GameBase<*>> getGameOrDefault(world: ServerWorld, pos: BlockPos, default: T): T =
        getGame(world, pos) ?: run { default.also { games += it } }
}