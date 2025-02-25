package mahjong.game.game_logic

import mahjong.MOD_ID
import mahjong.util.TextFormatting
import kotlinx.serialization.Serializable
import net.minecraft.text.MutableText
import net.minecraft.text.Text


@Serializable
enum class MahjongGameBehavior : TextFormatting {
    CHII,
    PON_OR_CHII,
    PON,
    KAN,
    MINKAN,
    ANKAN,
    ANKAN_OR_KAKAN,
    KAKAN,
    CHAN_KAN,
    RIICHI,
    DOUBLE_RIICHI,
    RON,
    TSUMO,
    KYUUSHU_KYUUHAI,

    EXHAUSTIVE_DRAW,
    DISCARD,
    SKIP,
    GAME_START,
    GAME_OVER,
    SCORE_SETTLEMENT,
    YAKU_SETTLEMENT,
    COUNTDOWN_TIME,
    AUTO_ARRANGE,
    MACHI,
    ;

    val translateKey = "$MOD_ID.game.behavior.${name.lowercase()}"
    override fun toText(): MutableText = Text.translatable(translateKey)
}