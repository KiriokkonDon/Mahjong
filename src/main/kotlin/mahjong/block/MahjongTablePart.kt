package mahjong.block

import net.minecraft.util.StringIdentifiable


enum class MahjongTablePart : StringIdentifiable {
    BOTTOM_CENTER, // Центральная нижняя часть
    BOTTOM_EAST,   // Восточная нижняя часть
    BOTTOM_WEST,   // Западная нижняя часть
    BOTTOM_SOUTH,  // Южная нижняя часть
    BOTTOM_NORTH,  // Северная нижняя часть
    BOTTOM_SOUTHEAST, BOTTOM_SOUTHWEST, BOTTOM_NORTHEAST, BOTTOM_NORTHWEST, // Углы нижнего слоя
    TOP_CENTER,    // Центральная верхняя часть
    TOP_EAST, TOP_WEST, TOP_SOUTH, TOP_NORTH, // Верхние края
    TOP_SOUTHEAST, TOP_SOUTHWEST, TOP_NORTHEAST, TOP_NORTHWEST; // Верхние углы

    override fun toString(): String = this.name.lowercase()
    override fun asString(): String = this.name.lowercase()
}