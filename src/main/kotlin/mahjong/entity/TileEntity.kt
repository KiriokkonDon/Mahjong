package mahjong.entity

import mahjong.block.MahjongTableBlockEntity
import mahjong.game.GameManager
import mahjong.game.MahjongGame
import mahjong.game.game_logic.MahjongGameBehavior
import mahjong.game.game_logic.MahjongTile
import mahjong.game.player.MahjongPlayer
import mahjong.network.MahjongTileCodePayload
import mahjong.network.sendPayloadToServer
import mahjong.registry.EntityTypeRegistry
import mahjong.registry.ItemRegistry
import mahjong.scheduler.client.OptionalBehaviorHandler
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.mahjong4j.tile.Tile



fun List<MahjongTileEntity>.toMahjongTileList(): List<MahjongTile> = this.map { it.mahjongTile }


class MahjongTileEntity(
    type: EntityType<*> = EntityTypeRegistry.mahjongTile,
    world: World,
) : GameEntity(type, world) {

    constructor(world: World, code: Int) : this(world = world) {
        this.code = code
    }


    constructor(
        world: World,
        code: Int,
        gameBlockPos: BlockPos,
        isSpawnedByGame: Boolean,
        inGameTilePosition: TilePosition,
        gamePlayers: List<String>,
        canSpectate: Boolean,
        facing: TileFacing,
    ) : this(world = world) {
        this.gameBlockPos = gameBlockPos
        this.isSpawnedByGame = isSpawnedByGame
        this.code = code
        this.inGameTilePosition = inGameTilePosition
        this.gamePlayers = gamePlayers
        this.canSpectate = canSpectate
        this.facing = facing
    }


    private var spawnedByGameServerSideCode: Int = MahjongTile.UNKNOWN.code


    private var spawnedByGameClientSideCode: Int = MahjongTile.UNKNOWN.code


    var code: Int
        set(value) {
            if (isSpawnedByGame) {
                if (!world.isClient) spawnedByGameServerSideCode = value
                else {
                    if (value != MahjongTile.UNKNOWN.code) mahjongTable?.calculateRemainingTiles(value)
                    spawnedByGameClientSideCode = value
                }
            } else {
                dataTracker.set(CODE, value)
            }
        }
        get() = if (isSpawnedByGame) {
            if (!world.isClient) spawnedByGameServerSideCode
            else spawnedByGameClientSideCode
        } else {
            dataTracker[CODE]
        }


    var ownerUUID: String
        set(value) = dataTracker.set(OWNER_UUID, value)
        get() = dataTracker[OWNER_UUID]


    var gamePlayers: List<String>
        set(value) = dataTracker.set(GAME_PLAYERS, Json.encodeToString(value))
        get() = Json.decodeFromString(dataTracker[GAME_PLAYERS])


    var facing: TileFacing
        set(value) = dataTracker.set(FACING, Json.encodeToString(value))
        get() = Json.decodeFromString(dataTracker[FACING])


    var canSpectate: Boolean
        set(value) = dataTracker.set(GAME_CAN_SPECTATE, value)
        get() = dataTracker[GAME_CAN_SPECTATE]


    var inGameTilePosition: TilePosition
        set(value) = dataTracker.set(GAME_TILE_POSITION, Json.encodeToString(value))
        get() = Json.decodeFromString(dataTracker[GAME_TILE_POSITION])


    val mahjongTile: MahjongTile
        get() = MahjongTile.entries[code]


    val mahjong4jTile: Tile
        get() = mahjongTile.mahjong4jTile


    val mahjongTable: MahjongTableBlockEntity?
        get() = if (isSpawnedByGame) world.getBlockEntity(gameBlockPos) as MahjongTableBlockEntity? else null


    fun getCodeForPlayer(player: ServerPlayerEntity): Int {
        if (world.isClient) throw IllegalStateException("Cannot get code from client side")
        return if (!isSpawnedByGame) {
            throw IllegalStateException("This MahjongTileEntity must be spawned by game")
        } else when (inGameTilePosition) {
            TilePosition.WALL -> MahjongTile.UNKNOWN.code
            TilePosition.HAND ->
                when {
                    player.uuidAsString == ownerUUID -> code
                    player.uuidAsString in gamePlayers -> MahjongTile.UNKNOWN.code
                    canSpectate -> code
                    else -> MahjongTile.UNKNOWN.code
                }
            else -> code
        }
    }

    override fun onTrackedDataSet(data: TrackedData<*>) {
        super.onTrackedDataSet(data)


        if (FACING == data) boundingBox = calculateBoundingBox()


        if (!world.isClient || !isSpawnedByGame) return
        sendPayloadToServer(
            payload = MahjongTileCodePayload(id = id)
        )
    }


    override fun isCollidable(): Boolean = true

    override fun getDimensions(pose: EntityPose): EntityDimensions =
        if (facing == TileFacing.HORIZONTAL) {
            super.getDimensions(pose)
        } else {
            EntityDimensions.fixed(MAHJONG_TILE_HEIGHT, MAHJONG_TILE_DEPTH)
        }


    override fun interact(player: PlayerEntity, hand: Hand): ActionResult {
        if (!world.isClient) {
            player as ServerPlayerEntity
            if (!isSpawnedByGame) {
                if (player.isSneaking) {
                    val itemStack = ItemRegistry.mahjongTile.defaultStack.also {
                        val nbt = NbtCompound()
                        nbt.putInt("code", code)
                        it.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt))
                    }
                    player.giveItemStack(itemStack)
                    playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1f, 1f)
                    remove(RemovalReason.DISCARDED)
                } else {
                    facing = facing.next
                }
            } else if (inGameTilePosition == TilePosition.HAND && ownerUUID == player.uuidAsString) {
                val game = GameManager.getGame<MahjongGame>(player) ?: return ActionResult.FAIL
                val mjPlayer = game.getPlayer(player) as MahjongPlayer? ?: return ActionResult.FAIL
                if (MahjongGameBehavior.DISCARD in mjPlayer.waitingBehavior) {
                    if (this.mahjongTile !in mjPlayer.cannotDiscardTiles) {
                        mjPlayer.behaviorResult = MahjongGameBehavior.DISCARD to "$code"
                    }
                }
            }
        } else {
            if (OptionalBehaviorHandler.waiting) OptionalBehaviorHandler.setScreen()
        }
        return ActionResult.SUCCESS
    }

    override fun initDataTracker(builder: DataTracker.Builder) {
        super.initDataTracker(builder)
        builder.add(CODE, MahjongTile.UNKNOWN.code)
        builder.add(OWNER_UUID, "")
        builder.add(GAME_PLAYERS, Json.encodeToString(mutableListOf<String>()))
        builder.add(FACING, Json.encodeToString(TileFacing.HORIZONTAL))
        builder.add(GAME_CAN_SPECTATE, true)
        builder.add(GAME_TILE_POSITION, Json.encodeToString(TilePosition.WALL))
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        super.readCustomDataFromNbt(nbt)
        code = nbt.getInt("Code")
        ownerUUID = nbt.getString("OwnerUUID")
        gamePlayers = Json.decodeFromString(nbt.getString("GamePlayers"))
        facing = Json.decodeFromString(nbt.getString("Facing"))
        canSpectate = nbt.getBoolean("GameCanSpectate")
        inGameTilePosition = Json.decodeFromString(nbt.getString("GameTilePosition"))
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        super.writeCustomDataToNbt(nbt)
        nbt.putInt("Code", code)
        nbt.putString("OwnerUUID", ownerUUID)
        nbt.putString("GamePlayers", Json.encodeToString(gamePlayers))
        nbt.putString("Facing", Json.encodeToString(facing))
        nbt.putBoolean("GameCanSpectate", canSpectate)
        nbt.putString("GameTilePosition", Json.encodeToString(inGameTilePosition))
    }

    companion object {
        const val MAHJONG_TILE_SCALE = 0.15f
        const val MAHJONG_TILE_WIDTH = 1f / 16 * 12 * MAHJONG_TILE_SCALE
        const val MAHJONG_TILE_HEIGHT = 1f / 16 * 16 * MAHJONG_TILE_SCALE
        const val MAHJONG_TILE_DEPTH = 1f / 16 * 8 * MAHJONG_TILE_SCALE
        const val MAHJONG_TILE_SMALL_PADDING = 0.0025

        private val CODE: TrackedData<Int> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        private val OWNER_UUID: TrackedData<String> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.STRING)

        private val GAME_PLAYERS: TrackedData<String> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.STRING)

        private val FACING: TrackedData<String> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.STRING)

        private val GAME_CAN_SPECTATE: TrackedData<Boolean> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        private val GAME_TILE_POSITION: TrackedData<String> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.STRING)
    }
}


enum class TileFacing(
    val angleForDegreesQuaternionFromPositiveX: Float,
) {
    HORIZONTAL(0f),
    UP(90f),
    DOWN(-90f);

    val next: TileFacing
        get() = entries[(this.ordinal + 1) % entries.size]
}



enum class TilePosition {
    WALL, HAND, OTHER
}