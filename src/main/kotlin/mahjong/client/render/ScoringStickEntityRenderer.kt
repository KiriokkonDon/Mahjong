package mahjong.client.render

import mahjong.entity.ScoringStickEntity
import mahjong.entity.ScoringStickEntity.Companion.MAHJONG_POINT_STICK_HEIGHT
import mahjong.entity.ScoringStickEntity.Companion.MAHJONG_POINT_STICK_SCALE
import mahjong.game.game_logic.ScoringStick
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
class ScoringStickEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<ScoringStickEntity>(context) {

    private val itemRenderer = context.itemRenderer

    override fun shouldRender(
        entity: ScoringStickEntity,
        frustum: Frustum,
        x: Double,
        y: Double,
        z: Double,
    ): Boolean {
        return !entity.isInvisible && super.shouldRender(entity, frustum, x, y, z)
    }

    override fun render(
        entity: ScoringStickEntity,
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
            RenderHelper.renderItem(
                itemRenderer = itemRenderer,
                matrices = this,
                stack = scoringSticks[entity.code],
                offsetX = 0.0,
                offsetY = MAHJONG_POINT_STICK_HEIGHT / 2.0 + (1f * MAHJONG_POINT_STICK_SCALE - MAHJONG_POINT_STICK_HEIGHT) / 2.0,
                offsetZ = 0.0,
                light = light,
                mode = ModelTransformationMode.HEAD,
                vertexConsumer = vertexConsumers
            )
            pop()
        }
    }

    override fun getTexture(entity: ScoringStickEntity): Identifier? = null

    companion object {
        private val scoringSticks = ScoringStick.entries.map { stick->
            ItemRegistry.mahjongScoringStick.defaultStack.also {
                val nbt = NbtCompound()
                nbt.putInt("code", stick.code)
                it.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt))
            }
        }
    }
}