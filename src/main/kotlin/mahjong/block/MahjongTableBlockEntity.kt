package mahjong.block

import mahjong.client.gui.ui.MahjongTableGui
import mahjong.client.gui.ui.MahjongTableWaitingScreen
import mahjong.entity.MahjongTileEntity
import mahjong.game.GameManager
import mahjong.game.MahjongGame
import mahjong.game.game_logic.MahjongRound
import mahjong.game.game_logic.MahjongRule
import mahjong.network.MahjongTablePayloadListener
import mahjong.registry.BlockEntityTypeRegistry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket
import net.minecraft.registry.RegistryWrapper
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import org.mahjong4j.tile.Tile
import java.util.concurrent.ConcurrentHashMap

class MahjongTableBlockEntity(
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(BlockEntityTypeRegistry.mahjongTable, pos, state) {
    private var gameInitialized = false // Флаг инициализированной игры
    val players = arrayListOf("", "", "", "") // Список игроков (UUID в виде строк)
    val playerEntityNames = arrayListOf("", "", "", "") // Имена сущностей игроков
    val bots = arrayListOf(false, false, false, false) // Является ли игрок ботом
    val ready = arrayListOf(false, false, false, false) // Готов ли игрок к игре
    var rule = MahjongRule() // Правила игры
    var playing = false // Идет ли игра в данный момент
    var round = MahjongRound() // Текущий раунд
    var seat = arrayListOf("", "", "", "") // Порядок посадки игроков
    var dealer = "" // UUID дилера (раздающего)
    var points = arrayListOf(0, 0, 0, 0) // Очки игроков


    val remainingTiles = ConcurrentHashMap<Int, Int>().apply {
        Tile.entries.forEach { this[it.code] = 0 }
    }

    //Подсчет оставшихся тайлов по коду, основываясь на текущих сущностях тайлов на столе
    @Environment(EnvType.CLIENT)
    fun calculateRemainingTiles(code: Int) {
        val tableCenter = with(this.pos) { Vec3d(x + 0.5, y + 1.0, z + 0.5) }
        val tiles = world?.getEntitiesByClass(
            MahjongTileEntity::class.java,
            Box.of(tableCenter, 3.0, 2.0, 3.0)
        ) { it.isSpawnedByGame && it.gameBlockPos == this.pos && it.mahjongTile.code == code } ?: return
        remainingTiles[code] = 4 - tiles.size // Обновление количества оставшихся тайлов
    }

    override fun markDirty() {
        super.markDirty()
        // Обновление блока на сервере при изменении данных
        if (this.hasWorld() && !this.world!!.isClient) {
            (world as ServerWorld).chunkManager.markForUpdate(getPos())
        }
    }

    override fun readNbt(nbt: NbtCompound, registryLookup: RegistryWrapper.WrapperLookup) {
        with(nbt) {
            repeat(4) {
                players[it] = getString("PlayerStringUUID$it")
                playerEntityNames[it] = getString("PlayerEntityName$it")
                bots[it] = getBoolean("Bot$it")
                ready[it] = getBoolean("PlayerReady$it")
                seat[it] = getString("Seat$it")
                points[it] = getInt("Point$it")
            }
            dealer = getString("Dealer")
            rule = MahjongRule.fromJsonString(getString("Rule"))
            playing = getBoolean("Playing")
            round = Json.decodeFromString(getString("Round"))
        }
        world?.isClient?.let { isClient ->
            if (isClient) {
                val screen = MinecraftClient.getInstance().currentScreen
                if (screen is MahjongTableWaitingScreen && (screen.description as MahjongTableGui).mahjongTable == this) {
                    if (!playing) {  // Обновление экрана ожидания, если игра не началась
                        screen.refresh()
                    } else {  // Закрытие экрана, если игра началась
                        screen.close()
                    }
                }
            }
        }
    }

    override fun writeNbt(nbt: NbtCompound, registryLookup: RegistryWrapper.WrapperLookup) {
        if (cachedState[MahjongTable.PART] == MahjongTablePart.BOTTOM_CENTER) {
            with(nbt) {
                repeat(4) {
                    putString("PlayerStringUUID$it", players[it])
                    putString("PlayerEntityName$it", playerEntityNames[it])
                    putBoolean("Bot$it", bots[it])
                    putBoolean("PlayerReady$it", ready[it])
                    putString("Seat$it", seat[it])
                    putInt("Point$it", points[it])
                }
                putString("Dealer", dealer)
                putString("Rule", rule.toJsonString())
                putBoolean("Playing", playing)
                putString("Round", Json.encodeToString(round))
            }
        }
    }

    override fun toInitialChunkDataNbt(registryLookup: RegistryWrapper.WrapperLookup): NbtCompound =
        NbtCompound().also { writeNbt(it, registryLookup) }

    override fun toUpdatePacket(): Packet<ClientPlayPacketListener> = BlockEntityUpdateS2CPacket.create(this)

    companion object {
        fun tick(world: World, pos: BlockPos, blockEntity: MahjongTableBlockEntity) {
            if (!world.isClient && !blockEntity.gameInitialized) {
                // Синхронизация данных блока с игровым состоянием
                MahjongTablePayloadListener.syncBlockEntityDataWithGame(
                    blockEntity = blockEntity,
                    game = GameManager.getGameOrDefault(
                        world = world as ServerWorld,
                        pos = pos,
                        default = MahjongGame(world, pos, blockEntity.rule)
                    )
                )
                blockEntity.gameInitialized = true
            }
        }
    }
}