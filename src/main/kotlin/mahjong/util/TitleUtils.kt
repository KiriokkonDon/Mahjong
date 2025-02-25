package mahjong.util

import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket
import net.minecraft.network.packet.s2c.play.TitleS2CPacket
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text



fun ServerPlayerEntity.sendTitles(
    title: Text = Text.of(""),
    subtitle: Text? = null,
) {
    with(networkHandler) {
        sendPacket(TitleS2CPacket(title))
        subtitle?.also { sendPacket(SubtitleS2CPacket(it)) }
    }
}


fun Collection<ServerPlayerEntity>.sendTitles(
    title: Text = Text.of(""),
    subtitle: Text? = null
) {
    forEach { it.sendTitles(title, subtitle) }
}