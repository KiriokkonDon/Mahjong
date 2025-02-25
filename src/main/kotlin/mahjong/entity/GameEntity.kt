package mahjong.entity

import mahjong.block.MahjongTableBlockEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtHelper
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

abstract class GameEntity(
    type: EntityType<*>,
    world: World,
) : Entity(type, world) {


    override fun canHit(): Boolean = !isRemoved


    var gameBlockPos: BlockPos
        set(value) = dataTracker.set(GAME_BLOCK_POS, value)
        get() = dataTracker[GAME_BLOCK_POS]


    open var isSpawnedByGame: Boolean
        set(value) = dataTracker.set(SPAWNED_BY_GAME, value)
        get() = dataTracker[SPAWNED_BY_GAME]

    open fun autoRemove() {
        if (!world.isClient && isSpawnedByGame && isAlive) {
            val blockEntity = world.getBlockEntity(gameBlockPos)
            if (blockEntity !is MahjongTableBlockEntity) {
                remove(RemovalReason.DISCARDED)
            } else if (!blockEntity.playing) {
                remove(RemovalReason.DISCARDED)
            }
        }
    }

    override fun tick() {
        super.tick()
        autoRemove()
    }

    override fun initDataTracker(builder: DataTracker.Builder) {
        builder.add(GAME_BLOCK_POS, blockPos)
        builder.add(SPAWNED_BY_GAME, false)
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        gameBlockPos = NbtHelper.toBlockPos(nbt, "GameBlockPos").get()
        isSpawnedByGame = nbt.getBoolean("SpawnedByGame")
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.put("GameBlockPos", NbtHelper.fromBlockPos(gameBlockPos))
        nbt.putBoolean("SpawnedByGame", isSpawnedByGame)
    }

    companion object {
        private val GAME_BLOCK_POS: TrackedData<BlockPos> = DataTracker.registerData(
            GameEntity::class.java,
            TrackedDataHandlerRegistry.BLOCK_POS
        )
        private val SPAWNED_BY_GAME: TrackedData<Boolean> =
            DataTracker.registerData(GameEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)
    }
}