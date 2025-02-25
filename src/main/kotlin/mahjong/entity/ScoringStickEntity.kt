package mahjong.entity

import mahjong.game.game_logic.ScoringStick
import mahjong.registry.EntityTypeRegistry
import mahjong.registry.ItemRegistry
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.world.World

class ScoringStickEntity(
    type: EntityType<*> = EntityTypeRegistry.mahjongScoringStick,
    world: World
) : GameEntity(type, world) {


    var code: Int
        set(value) = dataTracker.set(CODE, value)
        get() = dataTracker[CODE]

    val scoringStick: ScoringStick
        get() = ScoringStick.entries[code]

    override fun isCollidable(): Boolean = true

    override fun interact(player: PlayerEntity, hand: Hand): ActionResult {
        if (!world.isClient) {
            player as ServerPlayerEntity
            if (!isSpawnedByGame && player.isSneaking) {

                val itemStack = ItemRegistry.mahjongScoringStick.defaultStack.also {
                    val nbt = NbtCompound()
                    nbt.putInt("code", code)
                    it.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt))
                }
                player.giveItemStack(itemStack)
                playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1f, 1f)
                remove(RemovalReason.DISCARDED)
            }
        }
        return ActionResult.SUCCESS
    }

    override fun initDataTracker(builder: DataTracker.Builder) {
        super.initDataTracker(builder)
        builder.add(CODE, ScoringStick.P100.code)
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        super.readCustomDataFromNbt(nbt)
        code = nbt.getInt("Code")
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        super.writeCustomDataToNbt(nbt)
        nbt.putInt("Code", code)
    }

    companion object {
        const val MAHJONG_POINT_STICK_SCALE = 0.4f
        const val MAHJONG_POINT_STICK_WIDTH = 1f / 16 * 16 * MAHJONG_POINT_STICK_SCALE
        const val MAHJONG_POINT_STICK_HEIGHT = 1f / 16 * 0.5f * MAHJONG_POINT_STICK_SCALE
        const val MAHJONG_POINT_STICK_DEPTH = 1f / 16 * 2.5f * MAHJONG_POINT_STICK_SCALE


        private val CODE: TrackedData<Int> =
            DataTracker.registerData(ScoringStickEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
    }
}