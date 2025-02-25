package mahjong.client.render

import mahjong.entity.MahjongTileEntity
import mahjong.entity.MahjongTileEntity.Companion.MAHJONG_TILE_DEPTH
import mahjong.entity.MahjongTileEntity.Companion.MAHJONG_TILE_HEIGHT
import mahjong.entity.TileFacing
import mahjong.game.game_logic.MahjongTile
import mahjong.registry.ItemRegistry
import mahjong.util.RenderHelper
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.render.Frustum
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis

@Environment(EnvType.CLIENT)
class TileEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<MahjongTileEntity>(context) {

    private val itemRenderer = context.itemRenderer

    override fun shouldRender(entity: MahjongTileEntity, frustum: Frustum, x: Double, y: Double, z: Double): Boolean {
        return !entity.isInvisible && super.shouldRender(entity, frustum, x, y, z)
    }

    override fun render(
        entity: MahjongTileEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        with(matrices) {
            push()
            multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.yaw + 180))
            multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.facing.angleForDegreesQuaternionFromPositiveX))
            var offsetY = 0.0
            var offsetZ = 0.0
            when (entity.facing) {
                TileFacing.HORIZONTAL -> offsetY = MAHJONG_TILE_HEIGHT / 2.0
                TileFacing.UP -> offsetZ = -MAHJONG_TILE_DEPTH / 2.0
                TileFacing.DOWN -> offsetZ = MAHJONG_TILE_DEPTH / 2.0
            }
            RenderHelper.renderItem(
                itemRenderer = itemRenderer,
                matrices = this,
                stack = mahjongTiles[entity.code],
                offsetX = 0.0,
                offsetY = offsetY,
                offsetZ = offsetZ,
                light = light,
                vertexConsumer = vertexConsumers,
                mode = ModelTransformationMode.HEAD
            )
            pop()
        }
    }

    override fun getTexture(entity: MahjongTileEntity): Identifier? = null

    companion object {
        @Environment(EnvType.CLIENT)
        val mahjongTiles = MahjongTile.entries.map { tile ->
            ItemRegistry.mahjongTile.defaultStack.also {
                val nbt = NbtCompound()
                nbt.putInt("code", tile.code)
                it.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt))
            }
        }
    }
}