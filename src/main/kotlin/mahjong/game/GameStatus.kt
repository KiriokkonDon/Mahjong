package mahjong.game

import mahjong.MOD_ID
import mahjong.util.TextFormatting
import net.minecraft.text.Text



enum class GameStatus : TextFormatting {
    WAITING,
    PLAYING;

    override fun toText(): Text = Text.translatable("$MOD_ID.game.status.${name.lowercase()}")
}