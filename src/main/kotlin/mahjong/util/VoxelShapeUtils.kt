package mahjong.util

import net.minecraft.block.Block
import net.minecraft.util.shape.VoxelShape

//Определение моделей

fun boxBySize(
    posX: Int,
    posY: Int,
    posZ: Int,
    sizeX: Int,
    sizeY: Int,
    sizeZ: Int,
): VoxelShape {
    return boxBySize(
        posX.toDouble(),
        posY.toDouble(),
        posZ.toDouble(),
        sizeX.toDouble(),
        sizeY.toDouble(),
        sizeZ.toDouble()
    )
}


fun boxBySize(
    posX: Double,
    posY: Double,
    posZ: Double,
    sizeX: Double,
    sizeY: Double,
    sizeZ: Double,
): VoxelShape {
    return Block.createCuboidShape(
        posX,
        posY,
        posZ,
        posX + sizeX,
        posY + sizeY,
        posZ + sizeZ
    )
}