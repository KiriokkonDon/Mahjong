package mahjong.util

import com.mojang.authlib.GameProfile
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.font.TextRenderer.TextLayerType
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.PlayerSkinDrawer
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumerProvider

import net.minecraft.client.render.item.ItemRenderer
import net.minecraft.client.render.model.json.ModelTransformationMode

import net.minecraft.client.texture.TextureManager
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RotationAxis
import net.minecraft.world.LightType
import net.minecraft.world.World
import java.io.FileNotFoundException

@Environment(EnvType.CLIENT)
object RenderHelper {

    // Отрисовывка ItemStack
    fun renderItem(
        itemRenderer: ItemRenderer,
        matrices: MatrixStack,
        offsetX: Double,
        offsetY: Double,
        offsetZ: Double,
        stack: ItemStack,
        mode: ModelTransformationMode = ModelTransformationMode.GROUND,
        overlay: Int = OverlayTexture.DEFAULT_UV,
        light: Int,
        vertexConsumer: VertexConsumerProvider,
        world: World? = null,
        seed: Int = 0,
    ) {
        with(matrices) {
            push()
            translate(offsetX, offsetY, offsetZ)
            itemRenderer.renderItem(
                stack,
                mode,
                light,
                overlay,
                matrices,
                vertexConsumer,
                world,
                seed
            )
            pop()
        }
    }

    //Отрисовывает текстовую метку в 3D пространстве
    fun renderLabel(
        textRenderer: TextRenderer,
        matrices: MatrixStack,
        offsetX: Double,
        offsetY: Double,
        offsetZ: Double,
        text: Text,
        color: Int,
        scale: Float = 0.02f,
        backgroundColor: Int = (.4f * 255f).toInt() shl 24,
        light: Int,
        vertexConsumers: VertexConsumerProvider,
        shadow: Boolean = false,
        textLayerType: TextLayerType = TextLayerType.NORMAL,
    ) {
        with(matrices) {
            push()
            val client = MinecraftClient.getInstance()
            val offset = -textRenderer.getWidth(text) / 2f
            val matrix4f = matrices.peek().positionMatrix
            translate(offsetX, offsetY, offsetZ)
            scale(scale, scale, scale)
            multiply(client.entityRenderDispatcher.rotation)
            multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180f))
            textRenderer.draw(
                text,
                offset,
                0f,
                color,
                shadow,
                matrix4f,
                vertexConsumers,
                textLayerType,
                backgroundColor,
                light
            )
            pop()
        }
    }

    //Отрисовывает лицо игрока в 2D интерфейс
    fun renderPlayerFace(
        context: DrawContext,
        gameProfile: GameProfile,
        x: Int,
        y: Int,
        size: Int,
    ) {
        val client = MinecraftClient.getInstance()
        val upsideDown = false

        val skinTextures = client.skinProvider.getSkinTextures(gameProfile)
        val texture = skinTextures.texture

        PlayerSkinDrawer.draw(context, texture, x, y, size, true, upsideDown) // Always render hat (set hatVisible to true)
    }



    // Отрисовывает лицо бота в 2D интерфейсе
    fun renderBotFace(
        context: DrawContext,
        textureId: Identifier,
        x: Int,
        y: Int,
        size: Int,
    ) {
        try {
            val textureManager: TextureManager = MinecraftClient.getInstance().textureManager
            textureManager.getTexture(textureId) // Ensure texture is loaded

            // UV координаты для области головы в стандартном скине (верхняя часть лица)
            val u = 8
            val v = 8
            val regionWidth = 8
            val regionHeight = 8
            val textureWidth = 64 // Полная ширина текстуры скина
            val textureHeight = 64 // Полная высота текстуры скина

            // Используем drawTexture с указанием UV координат и размеров области головы
            context.drawTexture(
                textureId,
                x,
                y,
                u.toFloat(),
                v.toFloat(),
                size,
                size,
                textureWidth,
                textureHeight
            )

        } catch (e: FileNotFoundException) {
            // Handle texture not found error
            println("Texture not found: $textureId")
        } catch (e: Exception) {
            // Handle other exceptions
            e.printStackTrace()
        }
    }


    //уровень освещения
    fun getLightLevel(world: World, pos: BlockPos): Int {
        val bLight = world.lightingProvider.get(LightType.BLOCK).getLightLevel(pos)
        val sLight = world.lightingProvider.get(LightType.SKY).getLightLevel(pos)
        return LightmapTextureManager.pack(bLight, sLight)
    }
}