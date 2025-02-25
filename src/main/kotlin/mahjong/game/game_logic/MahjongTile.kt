package mahjong.game.game_logic

import mahjong.MOD_ID
import mahjong.id
import mahjong.util.TextFormatting
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.mahjong4j.tile.Tile
import org.mahjong4j.tile.TileType


enum class MahjongTile : TextFormatting {
    M1,
    M2,
    M3,
    M4,
    M5,
    M6,
    M7,
    M8,
    M9,

    P1,
    P2,
    P3,
    P4,
    P5,
    P6,
    P7,
    P8,
    P9,

    S1,
    S2,
    S3,
    S4,
    S5,
    S6,
    S7,
    S8,
    S9,

    EAST,
    SOUTH,
    WEST,
    NORTH,

    WHITE_DRAGON,
    GREEN_DRAGON,
    RED_DRAGON,

    M5_RED,
    P5_RED,
    S5_RED,

    UNKNOWN;


    val surfaceIdentifier: Identifier
        get() = id("textures/item/mahjong_tile/mahjong_tile_${name.lowercase()}.png")

    val isRed: Boolean
        get() = when (this) {
            M5_RED -> true
            P5_RED -> true
            S5_RED -> true
            else -> false
        }


    val code: Int = ordinal


    val mahjong4jTile: Tile
        get() {
            val tileCode = when (code) {
                M5_RED.code -> M5.code
                P5_RED.code -> P5.code
                S5_RED.code -> S5.code
                UNKNOWN.code -> M1.code
                else -> code
            }
            return Tile.valueOf(tileCode)
        }

    val sortOrder: Int
        get() = when (this) {
            M5_RED -> 4
            P5_RED -> 13
            S5_RED -> 22
            else -> code
        }


    val nextTile: MahjongTile
        get() {
            with(mahjong4jTile) {
                val nextTileCode = when (type) {
                    TileType.FONPAI -> if (this == Tile.PEI) Tile.TON.code else code + 1
                    TileType.SANGEN -> if (this == Tile.CHN) Tile.HAK.code else code + 1
                    else -> if (number == 9) code - 8 else code + 1
                }
                return values()[nextTileCode]
            }
        }


    val previousTile: MahjongTile
        get() {
            with(mahjong4jTile) {
                val previousTileCode = when (type) {
                    TileType.FONPAI -> if (this == Tile.TON) Tile.PEI.code else code - 1
                    TileType.SANGEN -> if (this == Tile.HAK) Tile.CHN.code else code - 1
                    else -> if (number == 1) code + 8 else code - 1
                }
                return values()[previousTileCode]
            }
        }

    override fun toText(): Text = when (mahjong4jTile.type) {
        TileType.MANZU -> Text.translatable("$MOD_ID.game.tile.man", mahjong4jTile.number)
        TileType.PINZU -> Text.translatable("$MOD_ID.game.tile.pin", mahjong4jTile.number)
        TileType.SOHZU -> Text.translatable("$MOD_ID.game.tile.sou", mahjong4jTile.number)
        TileType.FONPAI, TileType.SANGEN -> Text.translatable("$MOD_ID.game.tile.${name.lowercase()}")
        else -> Text.of("")
    }

    companion object {

        fun random(): MahjongTile = values().random()

        val normalWall = buildList {
            values().forEach { tile ->
                repeat(4) { this += tile }
                if (tile == RED_DRAGON) return@buildList
            }
        }


        val redFive3Wall = normalWall.toMutableList().apply {
            this -= M5
            this -= P5
            this -= S5
            this += M5_RED
            this += P5_RED
            this += S5_RED
        }


        val redFive4Wall = redFive3Wall.toMutableList().apply {
            this -= P5
            this += P5_RED
        }
    }
}