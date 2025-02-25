package mahjong.network.event

import mahjong.game.GameManager
import mahjong.game.MahjongGame
import mahjong.network.MahjongTablePayloadListener
import net.minecraft.server.network.ServerPlayerEntity


fun onPlayerDisconnect(player: ServerPlayerEntity) {
    if (!player.world.isClient && GameManager.isInAnyGame(player)) {
        when (val game = GameManager.getGameBy(player) ?: return) {
            is MahjongGame -> {
                MahjongTablePayloadListener.syncBlockEntityWithGame(game = game) {
                    onPlayerDisconnect(player)
                }
            }
        }
    }
}