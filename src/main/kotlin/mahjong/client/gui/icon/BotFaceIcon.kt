package mahjong.client.gui.icon


import com.mojang.authlib.GameProfile
import io.github.cottonmc.cotton.gui.widget.icon.Icon
import mahjong.util.RenderHelper
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.util.DefaultSkinHelper
import net.minecraft.util.Identifier


class BotFaceIcon(
    val code: Int,
) : Icon {
    @Environment(EnvType.CLIENT)
    override fun paint(context: DrawContext, x: Int, y: Int, size: Int) {
        val textureId = getBotSkinTexture()

        context.drawTexture(
            textureId, x, y, 8F, 8F, 8, 8,
            64, 64
        )
    }

    @Environment(EnvType.CLIENT)
    private fun getBotSkinTexture(): Identifier {
        val client = MinecraftClient.getInstance()
        val profile = GameProfile(null, "Bot$code")
        val skinProvider = client.skinProvider
        return skinProvider.getSkinTextures(profile).texture ?: DefaultSkinHelper.getTexture()
    }
}
