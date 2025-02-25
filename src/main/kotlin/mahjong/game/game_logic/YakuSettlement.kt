package mahjong.game.game_logic

import mahjong.entity.toMahjongTileList
import mahjong.game.player.MahjongBot
import kotlinx.serialization.Serializable
import mahjong.game.player.MahjongPlayerBase
import org.mahjong4j.yaku.normals.NormalYaku
import org.mahjong4j.yaku.yakuman.Yakuman


@Serializable
data class YakuSettlement(
    val displayName: String,
    val uuid: String,
    val isRealPlayer: Boolean,
    val botCode: Int = MahjongTile.UNKNOWN.code,
    val yakuList: List<NormalYaku>,
    val yakumanList: List<Yakuman>,
    val doubleYakumanList: List<DoubleYakuman>,
    val nagashiMangan: Boolean = false,
    val redFiveCount: Int = 0,
    val riichi: Boolean,
    val winningTile: MahjongTile,
    val hands: List<MahjongTile>,
    val fuuroList: List<Pair<Boolean, List<MahjongTile>>>,
    val doraIndicators: List<MahjongTile>,
    val uraDoraIndicators: List<MahjongTile>,
    val fu: Int,
    val han: Int,
    val score: Int,
) {
    constructor(
        mahjongPlayer: MahjongPlayerBase,
        yakuList: List<NormalYaku>,
        yakumanList: List<Yakuman>,
        doubleYakumanList: List<DoubleYakuman>,
        nagashiMangan: Boolean = false,
        redFiveCount: Int = 0,
        winningTile: MahjongTile,
        fuuroList: List<Pair<Boolean, List<MahjongTile>>>,
        doraIndicators: List<MahjongTile>,
        uraDoraIndicators: List<MahjongTile>,
        fu: Int,
        han: Int,
        score: Int,
    ) : this(
        displayName = mahjongPlayer.displayName,
        uuid = mahjongPlayer.uuid,
        isRealPlayer = mahjongPlayer.isRealPlayer,
        botCode = if (mahjongPlayer is MahjongBot) mahjongPlayer.entity.code else MahjongTile.UNKNOWN.code,
        yakuList = yakuList,
        yakumanList = yakumanList,
        doubleYakumanList = doubleYakumanList,
        nagashiMangan = nagashiMangan,
        redFiveCount = redFiveCount,
        riichi = mahjongPlayer.riichi || mahjongPlayer.doubleRiichi,
        winningTile = winningTile,
        hands = mahjongPlayer.hands.toMahjongTileList(),
        fuuroList = fuuroList,
        doraIndicators = doraIndicators,
        uraDoraIndicators = uraDoraIndicators,
        fu = fu,
        han = han,
        score = score
    )

    companion object {

        fun nagashiMangan(
            mahjongPlayer: MahjongPlayerBase,
            doraIndicators: List<MahjongTile>,
            uraDoraIndicators: List<MahjongTile>,
            isDealer: Boolean
        ): YakuSettlement = YakuSettlement(
            mahjongPlayer = mahjongPlayer,
            yakuList = listOf(),
            yakumanList = listOf(),
            doubleYakumanList = listOf(),
            nagashiMangan = true,
            winningTile = MahjongTile.UNKNOWN,
            fuuroList = listOf(),
            doraIndicators = doraIndicators,
            uraDoraIndicators = uraDoraIndicators,
            fu = 0,
            han = 0,
            score = if (isDealer) 12000 else 8000
        )


        val NO_YAKU = YakuSettlement(
            displayName = "",
            uuid = "",
            isRealPlayer = false,
            botCode = MahjongTile.UNKNOWN.code,
            yakuList = emptyList(),
            yakumanList = emptyList(),
            doubleYakumanList = emptyList(),
            nagashiMangan = false,
            redFiveCount = 0,
            riichi = false,
            winningTile = MahjongTile.UNKNOWN,
            hands = emptyList(),
            fuuroList = emptyList(),
            doraIndicators = emptyList(),
            uraDoraIndicators = emptyList(),
            fu = 0,
            han = 0,
            score = 0
        )
    }
}