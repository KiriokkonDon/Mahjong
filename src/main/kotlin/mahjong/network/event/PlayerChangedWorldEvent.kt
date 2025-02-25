package mahjong.network.event

import mahjong.game.GameManager
import mahjong.game.MahjongGame
import mahjong.network.MahjongTablePayloadListener
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld


fun onPlayerChangedWorld(player: PlayerEntity, origin: ServerWorld, destination: ServerWorld) {
    if (origin == destination) return
    if (!player.world.isClient && GameManager.isInAnyGame(player as ServerPlayerEntity)) {
        when (val game = GameManager.getGameBy(player) ?: return) {
            is MahjongGame -> {
                MahjongTablePayloadListener.syncBlockEntityWithGame(game = game) {
                    onPlayerChangedWorld(player)
                }
            }
        }
    }
}