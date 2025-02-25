package mahjong.client.gui.ui

import mahjong.MOD_ID
import net.minecraft.text.Text

enum class YakuTab(
    val title: Text,
    val items: List<YakuItem>,
) {
    Han1(
        title = Text.translatable("$MOD_ID.gui.yaku_overview.han", 1),
        items = listOf(
            YakuItem.Riichi,
            YakuItem.Tanyao,
            YakuItem.Tsumo,
            YakuItem.Jikaze,
            YakuItem.Bakaze,
            YakuItem.Sangen,
            YakuItem.Pinfu,
            YakuItem.Ipeiko,
            YakuItem.Chankan,
            YakuItem.Rinshankaihoh,
            YakuItem.Haitei,
            YakuItem.Houtei,
            YakuItem.Ippatsu,
            YakuItem.Dora,
            YakuItem.RedFive,
        )
    ),
    Han2(
        title = Text.translatable("$MOD_ID.gui.yaku_overview.han", 2),
        items = listOf(
            YakuItem.DoubleRiichi,
            YakuItem.Sanshokudohko,
            YakuItem.Sankantsu,
            YakuItem.Toitoiho,
            YakuItem.Sananko,
            YakuItem.Shosangen,
            YakuItem.Honrohtoh,
            YakuItem.Chitoitsu,
            YakuItem.Chanta,
            YakuItem.Ikkitsukan,
            YakuItem.Sanshokudohjun,
        )
    ),
    Han3(
        title = Text.translatable("$MOD_ID.gui.yaku_overview.han", 3),
        items = listOf(
            YakuItem.Ryanpeiko,
            YakuItem.Junchan,
            YakuItem.Honitsu,
        )
    ),
    Han6(
        title = Text.translatable("$MOD_ID.gui.yaku_overview.han", 6),
        items = listOf(
            YakuItem.Chinitsu,
        )
    ),
    Mangan(
        title = Text.translatable("$MOD_ID.game.score.mangan"),
        items = listOf(
            YakuItem.NagashiMangan,
        )
    ),
    Yakuman(
        title = Text.translatable("$MOD_ID.game.score.yakuman_1x"),
        items = listOf(
            YakuItem.Tenho,
            YakuItem.Chiho,
            YakuItem.Daisangen,
            YakuItem.Suanko,
            YakuItem.Tsuiso,
            YakuItem.Ryuiso,
            YakuItem.Chinroto,
            YakuItem.Kokushimuso,
            YakuItem.Shosushi,
            YakuItem.Sukantsu,
            YakuItem.Churenpohto,
        )
    ),
    DoubleYakuman(
        title = Text.translatable("$MOD_ID.game.score.yakuman_2x"),
        items = listOf(
            YakuItem.SuankoTanki,
            YakuItem.KokushimusoJusanmenmachi,
            YakuItem.JunseiChurenpohto,
            YakuItem.Daisushi,
        )
    ),
    Draw(
        title = Text.translatable("$MOD_ID.gui.yaku_overview.draw"),
        items = listOf(
            YakuItem.SuufonRenda,
            YakuItem.Suukaikan,
            YakuItem.KyuushuKyuuhai,
            YakuItem.SuuchaRiichi,
        )
    ),
    ;
}