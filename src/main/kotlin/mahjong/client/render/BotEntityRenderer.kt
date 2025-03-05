package mahjong.client.render

import mahjong.entity.BotEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.LivingEntityRenderer
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.util.Identifier

@Environment(EnvType.CLIENT)
class BotEntityRenderer(
    context: EntityRendererFactory.Context
) : LivingEntityRenderer<BotEntity, BipedEntityModel<BotEntity>>(
    context,
    BipedEntityModel(context.getPart(EntityModelLayers.PLAYER)),
    0.5f
) {

    override fun getTexture(entity: BotEntity): Identifier {
        val botTextureName = "mahjong_bot_${entity.botType}.png"
        return Identifier.of("mahjong", "textures/entity/$botTextureName")
    }

    override fun hasLabel(entity: BotEntity): Boolean {
        return entity.shouldRenderName() && super.hasLabel(entity)
    }
}