package mahjong.game.game_logic

import mahjong.MOD_ID
import mahjong.util.TextFormatting
import net.minecraft.text.Text



enum class DoubleYakuman : TextFormatting {
    DAISUSHI,
    SUANKO_TANKI,
    JUNSEI_CHURENPOHTO,
    KOKUSHIMUSO_JUSANMENMACHI
    ;

    override fun toText(): Text = Text.translatable("$MOD_ID.game.yaku.${name.lowercase()}")
}