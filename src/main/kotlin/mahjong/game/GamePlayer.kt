package mahjong.game

import net.minecraft.entity.Entity
import net.minecraft.server.network.ServerPlayerEntity

interface GamePlayer {


    val entity: Entity


    val displayName: String
        get() = entity.displayName?.string ?: ""


    val name: String
        get() = entity.name.string


    val uuid: String
        get() = entity.uuidAsString


    val isRealPlayer: Boolean
        get() = entity is ServerPlayerEntity
}