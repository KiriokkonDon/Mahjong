package mahjong.game.game_logic

import mahjong.MOD_ID
import mahjong.util.TextFormatting
import net.minecraft.text.Text



enum class ExhaustiveDraw : TextFormatting {
    NORMAL,
    KYUUSHU_KYUUHAI,
    SUUFON_RENDA,
    SUUCHA_RIICHI,
    SUUKAIKAN,
    ;

    val translateKey = "$MOD_ID.game.exhaustive_draw.${name.lowercase()}"
    override fun toText(): Text = Text.translatable(translateKey)
}