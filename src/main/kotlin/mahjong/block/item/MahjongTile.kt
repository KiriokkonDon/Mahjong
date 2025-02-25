package mahjong.block.item

import mahjong.entity.MahjongTileEntity
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUsageContext
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World


class MahjongTile(settings: Settings) : Item(settings) {



    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val itemStack = user.getStackInHand(hand)
        if (user.world.isClient) return TypedActionResult.success(itemStack)

        if (!user.isSneaking) {
            val nbt = itemStack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt() ?: NbtCompound()
            val tileCode = nbt.getInt("code")
            nbt.putInt("code", (tileCode + 1) % 38)
            itemStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt))
            return TypedActionResult.success(itemStack)
        }
        return TypedActionResult.pass(itemStack)
    }


    override fun useOnBlock(context: ItemUsageContext): ActionResult {
        val player = context.player ?: return ActionResult.PASS
        if (context.world.isClient) return ActionResult.SUCCESS

        val itemStack = context.stack
        val nbt = itemStack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt() ?: NbtCompound()
        val tileCode = nbt.getInt("code")

        return if (player.isSneaking) {
            val world = context.world as ServerWorld
            MahjongTileEntity(world = world, code = tileCode).apply {
                val pos = context.hitPos
                refreshPositionAndAngles(pos.x, pos.y, pos.z, (context.playerYaw + 180f), 0f)
                world.spawnEntity(this)
            }
            if (!player.abilities.creativeMode) itemStack.decrement(1)
            ActionResult.CONSUME
        } else {
            nbt.putInt("code", (tileCode + 1) % 38)
            itemStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt))
            ActionResult.SUCCESS
        }
    }
}