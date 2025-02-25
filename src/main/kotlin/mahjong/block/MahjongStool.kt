package mahjong.block

import mahjong.entity.SeatEntity
import mahjong.util.boxBySize
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.BlockView
import net.minecraft.world.World


class MahjongStool(settings: Settings) : Block(settings) {

    override fun onUse(
        state: BlockState,
        world: World,
        pos: BlockPos,
        player: PlayerEntity,
        hit: BlockHitResult
    ): ActionResult {
        if (!world.isClient && SeatEntity.canSpawnAt(world as ServerWorld, pos)) {
            // Создаем сущность сиденья и сажаем игрока
            SeatEntity.spawnAt(world as ServerWorld, pos, player, sitOffsetY = 0.4)
            return ActionResult.SUCCESS // Успешное действие
        }
        return ActionResult.PASS // Ничего не делаем, если нельзя сесть
    }

    /** Определяет форму стула для столкновений и выделения */
    override fun getOutlineShape(
        state: BlockState?,
        world: BlockView?,
        pos: BlockPos?,
        context: ShapeContext?
    ): VoxelShape = SHAPE

    companion object {
        // Форма стула, созданная из нескольких частей
        private val SHAPE: VoxelShape = createStoolShape()

        /** Создает форму стула из нескольких воксельных частей */
        private fun createStoolShape(): VoxelShape {
            val bottom1 = boxBySize(3.0, 4.0, 5.0, 2.0, 2.0, 6.0) // Нижние части
            val bottom2 = boxBySize(11.0, 4.0, 5.0, 2.0, 2.0, 6.0)
            val bottom3 = boxBySize(5.0, 4.0, 3.0, 6.0, 2.0, 2.0)
            val bottom4 = boxBySize(5.0, 4.0, 11.0, 6.0, 2.0, 2.0)
            val pillar1 = boxBySize(3.0, 0.0, 3.0, 2.0, 8.0, 2.0) // Ножки
            val pillar2 = boxBySize(11.0, 0.0, 11.0, 2.0, 8.0, 2.0)
            val pillar3 = boxBySize(3.0, 0.0, 11.0, 2.0, 8.0, 2.0)
            val pillar4 = boxBySize(11.0, 0.0, 3.0, 2.0, 8.0, 2.0)
            val top = boxBySize(1.0, 8.0, 1.0, 14.0, 2.0, 14.0) // Сиденье
            return VoxelShapes.union(bottom1, bottom2, bottom3, bottom4, pillar1, pillar2, pillar3, pillar4, top)
        }
    }
}