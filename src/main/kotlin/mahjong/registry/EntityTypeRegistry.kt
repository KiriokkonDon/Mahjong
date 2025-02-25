package mahjong.registry

import mahjong.entity.*
import mahjong.id
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry

object EntityTypeRegistry {

    val seat: EntityType<SeatEntity> = EntityType.Builder.create(::SeatEntity, SpawnGroup.MISC)
        .dimensions(0f, 0f)
        .build()

    val dice: EntityType<DiceEntity> = EntityType.Builder.create(::DiceEntity, SpawnGroup.MISC)
        .dimensions(DiceEntity.DICE_WIDTH, DiceEntity.DICE_HEIGHT)
        .build()

    val mahjongTile: EntityType<MahjongTileEntity> = EntityType.Builder.create(::MahjongTileEntity, SpawnGroup.MISC)
        .dimensions(MahjongTileEntity.MAHJONG_TILE_WIDTH, MahjongTileEntity.MAHJONG_TILE_HEIGHT)
        .build()

    val mahjongScoringStick: EntityType<ScoringStickEntity> =
        EntityType.Builder.create(::ScoringStickEntity, SpawnGroup.MISC)
            .dimensions(
                ScoringStickEntity.MAHJONG_POINT_STICK_WIDTH,
                ScoringStickEntity.MAHJONG_POINT_STICK_HEIGHT
            ).build()

    val mahjongBot: EntityType<BotEntity> = EntityType.Builder.create(::BotEntity, SpawnGroup.MISC)
        .dimensions(BotEntity.MAHJONG_BOT_WIDTH, BotEntity.MAHJONG_BOT_HEIGHT)
        .build()

    fun register() {
        Registry.register(Registries.ENTITY_TYPE, id("seat"), seat)
        Registry.register(Registries.ENTITY_TYPE, id("dice"), dice)
        Registry.register(Registries.ENTITY_TYPE, id("mahjong_bot"), mahjongBot)
        Registry.register(Registries.ENTITY_TYPE, id("mahjong_tile"), mahjongTile)
        Registry.register(Registries.ENTITY_TYPE, id("mahjong_scoring_stick"), mahjongScoringStick)
    }

}