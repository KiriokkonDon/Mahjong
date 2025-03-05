package mahjong.game.player

import mahjong.entity.BotEntity
import mahjong.game.game_logic.ClaimTarget
import mahjong.game.game_logic.MahjongRule
import mahjong.game.game_logic.MahjongTile
import net.minecraft.network.packet.s2c.play.PositionFlag
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.*

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
            this.teleport(targetWorld, x, y, z, EnumSet.noneOf(PositionFlag::class.java), yaw, pitch)
            this.yaw = yaw
            this.pitch = pitch
            faceTable(tableCenterPos)
        }
    }

    override suspend fun askToAnkanOrKakan(
        canAnkanTiles: Set<MahjongTile>,
        canKakanTiles: Set<Pair<MahjongTile, ClaimTarget>>,
        rule: MahjongRule,
    ): MahjongTile? {
        val validAnkanTiles = canAnkanTiles.filter { tile -> hands.any { it.mahjongTile == tile } }.toSet()
        val validKakanTiles = canKakanTiles.filter { (tile, _) -> hands.any { it.mahjongTile == tile } }.toSet()

        val ankanChoice = validAnkanTiles.firstOrNull()
        if (ankanChoice != null) {
            return ankanChoice
        }

        val kakanChoice = validKakanTiles.firstOrNull()?.first
        if (kakanChoice != null) {
            return kakanChoice
        }

        return null
    }
}