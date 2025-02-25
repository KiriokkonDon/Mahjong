package mahjong.game

import net.minecraft.particle.ParticleEffect
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

interface GameBase<T : GamePlayer> {

    val players: MutableList<T>
    val name: Text
    val world: ServerWorld
    val pos: BlockPos
    var status: GameStatus
    fun join(player: ServerPlayerEntity)
    fun leave(player: ServerPlayerEntity)
    fun start(sync: Boolean = true)
    fun end(sync: Boolean = true)
    fun onBreak()
    fun onPlayerDisconnect(player: ServerPlayerEntity)
    fun onPlayerChangedWorld(player: ServerPlayerEntity)
    fun onServerStopping(server: MinecraftServer)

    fun isInGame(player: ServerPlayerEntity): Boolean {
        return getPlayer(player) != null
    }
    fun isHost(player: ServerPlayerEntity): Boolean

    fun getPlayer(player: ServerPlayerEntity): T? {
        return players.find { it.entity == player }
    }

    fun playSound(
        world: ServerWorld = this.world,
        pos: BlockPos = this@GameBase.pos,
        soundEvent: SoundEvent,
        category: SoundCategory = SoundCategory.VOICE,
        volume: Float = 1f,
        pitch: Float = 1f
    ) {
        world.playSound(
            null,
            pos,
            soundEvent,
            category,
            volume,
            pitch
        )
    }

    fun spawnParticles(
        particleEffect: ParticleEffect,
        x: Double = pos.x + 0.5,
        y: Double = pos.y + 1.5,
        z: Double = pos.z + 0.5,
        count: Int = 1,
        deltaX: Double = 0.0,
        deltaY: Double = 0.0,
        deltaZ: Double = 0.0,
        speed: Double = 0.0
    ) {
        world.spawnParticles(particleEffect, x, y, z, count, deltaX, deltaY, deltaZ, speed)
    }

}