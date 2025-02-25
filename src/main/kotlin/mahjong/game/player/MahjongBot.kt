package mahjong.game.player

import mahjong.entity.BotEntity
import mahjong.game.game_logic.MahjongTile
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

class MahjongBot(
    val world: ServerWorld,
    pos: Vec3d,
    gamePos: BlockPos,
    val tableCenterPos: Vec3d,
    botNumber: Int
) : MahjongPlayerBase() {

    private val botNames = listOf("Akari", "Hiroki", "Yumi")

    override val entity: BotEntity = BotEntity(world = world).apply {
        code = MahjongTile.random().code
        isSpawnedByGame = true
        gameBlockPos = gamePos
        isInvisible = true
        refreshPositionAfterTeleport(pos)
        world.spawnEntity(this)
        faceTable(tableCenterPos)
        botType = botNumber
        botName = botNames.getOrNull(botNumber - 1) ?: "Bot $botNumber"
    }


    override var ready: Boolean = true

    override fun teleport(targetWorld: ServerWorld, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        with(entity) {
            if (this.world != targetWorld) moveToWorld(targetWorld)
            this.yaw = yaw
            this.pitch = pitch
            requestTeleport(x, y, z)
            faceTable(tableCenterPos)
        }
    }
}