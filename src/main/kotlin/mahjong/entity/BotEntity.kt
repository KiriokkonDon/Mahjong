package mahjong.entity

import mahjong.block.MahjongTableBlockEntity
import mahjong.registry.EntityTypeRegistry
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.Text
import net.minecraft.util.Arm
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraft.util.math.MathHelper

class BotEntity(
    type: EntityType<BotEntity> = EntityTypeRegistry.mahjongBot,
    world: World
) : LivingEntity(type, world) {

    var code: Int
        set(value) = dataTracker.set(CODE, value)
        get() = dataTracker[CODE]

    var botType: Int
        set(value) = dataTracker.set(BOT_TYPE, value)
        get() = dataTracker[BOT_TYPE]

    var botName: String
        set(value) = dataTracker.set(BOT_NAME, value)
        get() = dataTracker[BOT_NAME]

    var isSpawnedByGame: Boolean = false
    var gameBlockPos: BlockPos? = null

    init {
        isInvulnerable = true
    }

    override fun isCollidable(): Boolean = !isInvisible && !isRemoved

    override fun canHit(): Boolean = isCollidable()

    fun autoRemove() {
        if (!world.isClient && isSpawnedByGame && isAlive) {
            val blockEntity = world.getBlockEntity(gameBlockPos)
            if (blockEntity !is MahjongTableBlockEntity) {
                remove(RemovalReason.DISCARDED)
            }
        }
    }

    override fun shouldRenderName(): Boolean = true

    override fun initDataTracker(builder: DataTracker.Builder) {
        super.initDataTracker(builder)
        builder.add(CODE, 0)
        builder.add(BOT_TYPE, 1)
        builder.add(BOT_NAME, "")
    }

    override fun getMainArm(): Arm {
        return Arm.RIGHT // Возвращаем правую руку как значение по умолчанию
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        super.readCustomDataFromNbt(nbt)
        code = nbt.getInt("Code")
        isSpawnedByGame = nbt.getBoolean("IsSpawnedByGame")
        if (nbt.contains("GameBlockPos")) {
            gameBlockPos = BlockPos.fromLong(nbt.getLong("GameBlockPos"))
        }
        botType = nbt.getInt("BotType")
        botName = nbt.getString("BotName")
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        super.writeCustomDataToNbt(nbt)
        nbt.putInt("Code", code)
        nbt.putBoolean("IsSpawnedByGame", isSpawnedByGame)
        gameBlockPos?.let { nbt.putLong("GameBlockPos", it.asLong()) }
        nbt.putInt("BotType", botType)
        nbt.putString("BotName", botName)
    }

    override fun getArmorItems(): Iterable<ItemStack> {
        return listOf() // No armor for the bot
    }

    override fun getDisplayName(): Text {
        return Text.literal(botName)
    }

    fun getEyeHeight(pose: EntityPose, dimensions: EntityDimensions): Float {
        return 1.62f // Default player eye height
    }

    override fun getEquippedStack(slot: EquipmentSlot): ItemStack {
        return ItemStack.EMPTY // No equipped items
    }

    override fun equipStack(slot: EquipmentSlot, stack: ItemStack) {
        // Do nothing, as this bot doesn’t equip items
    }

    fun faceTable(tablePos: Vec3d) {
        val dx = tablePos.x - this.x
        val dz = tablePos.z - this.z
        val yawRadians = MathHelper.atan2(dz.toDouble(), dx.toDouble())
        var yaw = MathHelper.wrapDegrees((yawRadians * 180.0 / Math.PI).toFloat()) - 90f

        // Корректировка для правильного направления
        if (yaw < 0) yaw += 360f

        this.setYaw(yaw)
        this.headYaw = yaw
        this.bodyYaw = yaw

        // Принудительное обновление вращения
        this.updatePositionAndAngles(this.x, this.y, this.z, yaw, this.pitch)
    }

    companion object {
        const val MAHJONG_BOT_WIDTH = 0.6f
        const val MAHJONG_BOT_HEIGHT = 1.8f

        private val CODE: TrackedData<Int> =
            DataTracker.registerData(BotEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        private val BOT_TYPE: TrackedData<Int> =
            DataTracker.registerData(BotEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        private val BOT_NAME: TrackedData<String> =
            DataTracker.registerData(BotEntity::class.java, TrackedDataHandlerRegistry.STRING)

        // Добавьте эту функцию в companion object
        fun createLivingAttributes(): DefaultAttributeContainer.Builder {
            return LivingEntity.createLivingAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0) // Пример: 20 единиц здоровья
            // Вы можете добавить другие атрибуты, если нужно, например, скорость передвижения
        }
    }
}