package mahjong.entity

import mahjong.registry.EntityTypeRegistry
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.data.DataTracker
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World


class SeatEntity(
    type: EntityType<SeatEntity> = EntityTypeRegistry.seat,
    world: World,
    private var sourceBlock: BlockPos? = null,
    private val sitOffsetY: Double = 0.3,
    private val stopSitOffsetY: Double = 0.8,
) : Entity(type, world) {

    init {
        isInvisible = true
        sourceBlock?.apply { setPos(x + 0.5, y + sitOffsetY, z + 0.5) }
    }

    override fun tick() {
        super.tick()
        if (sourceBlock == null) sourceBlock = blockPos
        if (!world.isClient && (passengerList.isEmpty() || world.isAir(sourceBlock))) remove(RemovalReason.DISCARDED)
    }

    override fun getPosWithYOffset(offset: Float): BlockPos {
        return super.getPosWithYOffset(0.0f)
    }

    override fun initDataTracker(builder: DataTracker.Builder?) {}

    override fun readCustomDataFromNbt(nbt: NbtCompound?) {}

    override fun writeCustomDataToNbt(nbt: NbtCompound?) {}


    override fun updatePassengerForDismount(passenger: LivingEntity): Vec3d =
        Vec3d(blockPos.x + 0.5, blockPos.y + stopSitOffsetY, blockPos.z + 0.5)

    companion object {

        fun canSpawnAt(
            world: ServerWorld,
            pos: BlockPos,
            height: Int = 2,
            checkEntity: Boolean = true,
        ): Boolean {
            val heightEnough =
                if (height <= 0) true
                else (1..height).all {
                    val blockState = world.getBlockState(pos.offset(Direction.UP, it))
                    blockState.getCollisionShape(world, pos).isEmpty
                }

            return if (checkEntity) {
                val seatEntitiesAtThisPos =
                    world.getEntitiesByType(EntityTypeRegistry.seat) { it.blockPos == pos && it.isAlive }
                seatEntitiesAtThisPos.isEmpty() && heightEnough
            } else {
                heightEnough
            }
        }


        fun spawnAt(
            world: ServerWorld,
            pos: BlockPos,
            entity: Entity,
            sitOffsetY: Double = 0.3,
            stopSitOffsetY: Double = 0.8,
        ) {
            val seatEntity = SeatEntity(
                world = world,
                sourceBlock = pos,
                sitOffsetY = sitOffsetY,
                stopSitOffsetY = stopSitOffsetY
            )
            world.spawnEntity(seatEntity)
            entity.startRiding(seatEntity, false)
        }


        fun forceSpawnAt(
            world: ServerWorld,
            pos: BlockPos,
            entity: Entity,
            sitOffsetY: Double = 0.3,
            stopSitOffsetY: Double = 0.8,
        ) {
            val seatEntitiesAtThisPos = world.getEntitiesByType(EntityTypeRegistry.seat) {
                it.blockPos == pos && it.isAlive
            }
            seatEntitiesAtThisPos.forEach { it.remove(RemovalReason.DISCARDED) }
            spawnAt(world, pos, entity, sitOffsetY, stopSitOffsetY)
        }
    }
}