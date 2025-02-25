package mahjong.game.game_logic

import mahjong.entity.MahjongTileEntity
import org.mahjong4j.hands.Mentsu


class Fuuro(
    val mentsu: Mentsu,
    val tileMjEntities: List<MahjongTileEntity>,
    val claimTarget: ClaimTarget,
    val claimTile: MahjongTileEntity
)