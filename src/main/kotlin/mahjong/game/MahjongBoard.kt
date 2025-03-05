package mahjong.game

import mahjong.entity.ScoringStickEntity
import mahjong.entity.ScoringStickEntity.Companion.MAHJONG_POINT_STICK_DEPTH
import mahjong.entity.ScoringStickEntity.Companion.MAHJONG_POINT_STICK_HEIGHT
import mahjong.entity.ScoringStickEntity.Companion.MAHJONG_POINT_STICK_WIDTH
import mahjong.entity.MahjongTileEntity
import mahjong.entity.MahjongTileEntity.Companion.MAHJONG_TILE_DEPTH
import mahjong.entity.MahjongTileEntity.Companion.MAHJONG_TILE_HEIGHT
import mahjong.entity.MahjongTileEntity.Companion.MAHJONG_TILE_SMALL_PADDING
import mahjong.entity.MahjongTileEntity.Companion.MAHJONG_TILE_WIDTH
import mahjong.entity.TileFacing
import mahjong.entity.TilePosition
import mahjong.game.game_logic.*
import mahjong.scheduler.server.ServerScheduler
import mahjong.util.delayOnServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mahjong.game.player.MahjongPlayerBase
import net.minecraft.entity.Entity
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import org.mahjong4j.GeneralSituation
import org.mahjong4j.hands.Kantsu
import org.mahjong4j.tile.Tile
import org.mahjong4j.tile.TileType


class MahjongBoard(
    val game: MahjongGame,
) {

    val allTiles = mutableListOf<MahjongTileEntity>()


    val wall = mutableListOf<MahjongTileEntity>()


    val deadWall = mutableListOf<MahjongTileEntity>()


    var kanCount = 0
        public set


    val discards: MutableList<MahjongTileEntity> = mutableListOf()


    private val discardsCount: Int
        get() = game.players.sumOf { it.discardedTiles.size }

    private val noFuuro: Boolean
        get() = game.players.none { it.fuuroList.size > 0 }


    val isFirstRound: Boolean
        get() = discardsCount <= 4 && noFuuro


    val isHoutei: Boolean
        get() = wall.size <= 4


    val isSuufonRenda: Boolean
        get() {
            if (discards.size < 4) return false
            val discards = discards.asReversed()
            val fonTile = discards[0]
            if (fonTile.mahjong4jTile.type != TileType.FONPAI) return false
            repeat(4) { if (discards[it].code != fonTile.code) return false }
            return true
        }


    private val bakaze: Tile
        get() = game.round.wind.tile


    val doraIndicators: List<MahjongTileEntity>
        get() = List(kanCount + 1) {
            val doraIndicatorIndex = (4 - it) * 2 + kanCount
            deadWall[doraIndicatorIndex]
        }

    // Вычисляемое свойство, возвращающее список дора (плиток, добавляющих очки).

    val uraDoraIndicators: List<MahjongTileEntity>
        get() = List(kanCount + 1) {
            val uraDoraIndicatorIndex = (4 - it) * 2 + 1 + kanCount
            deadWall[uraDoraIndicatorIndex]
        }


    private val doraList: List<Tile>
        get() = doraIndicators.map { it.mahjongTile.nextTile.mahjong4jTile }


    private val uraDoraList: List<Tile>
        get() = uraDoraIndicators.map { it.mahjongTile.nextTile.mahjong4jTile }


    private fun flipDoraIndicators() {
        doraIndicators.forEach {
            if (it.facing != TileFacing.UP) it.facing = TileFacing.UP
            if (it.inGameTilePosition != TilePosition.OTHER) it.inGameTilePosition = TilePosition.OTHER
        }
    }

    //Оценка руки
    val generalSituation: GeneralSituation
        get() = GeneralSituation(
            isFirstRound,
            isHoutei,
            bakaze,
            doraList,
            uraDoraList
        )

    // Метод, очищающий игровое поле
    fun clear() {
        kanCount = 0
        discards.clear()
        wall.clear()
        deadWall.clear()
        val tilesToRemove = allTiles.toList()
        ServerScheduler.scheduleDelayAction {
            tilesToRemove.forEach { if (!it.isRemoved) it.remove(Entity.RemovalReason.DISCARDED) }
        }
        allTiles.clear()
    }

    /**
     * Генерирует все плитки [allTiles],
     * и добавляет все [allTiles] в [wall], расставляя их по позициям
     * */

    // Метод, генерирующий все плитки и размещающий их в стене.
    fun generateAllTilesAndSpawnWall() {
        val tableCenterPos = game.tableCenterPos
        val world = game.world
        allTiles.apply {
            when (game.rule.redFive) {
                MahjongRule.RedFive.NONE -> MahjongTile.normalWall
                MahjongRule.RedFive.THREE -> MahjongTile.redFive3Wall
                MahjongRule.RedFive.FOUR -> MahjongTile.redFive4Wall
            }.shuffled().forEach {
                this += MahjongTileEntity(
                    world = world,
                    code = it.code,
                    gameBlockPos = game.pos,
                    isSpawnedByGame = true,
                    inGameTilePosition = TilePosition.WALL,
                    gamePlayers = game.players.map { player -> player.uuid },
                    canSpectate = game.rule.spectate,
                    facing = TileFacing.DOWN
                )
            }
        }
        wall.apply {
            this += allTiles // Добавляем все плитки в стену.
            // Размещаем плитки по позициям и поворачиваем их
            forEachIndexed { index, tile ->
                // В каждом направлении 34 плитки (17 стопок * 2 слоя * 4 направления = 136).
                // Рассчитываем позицию и ориентацию плитки
                val directionOffset = 1.0
                val topOrBottom = (1 - index % 2)
                val yOffset =
                    (topOrBottom * MAHJONG_TILE_DEPTH).toDouble() + (if (topOrBottom == 1) MAHJONG_TILE_SMALL_PADDING else 0.0)
                val startingPos = (17.0 * MAHJONG_TILE_WIDTH) / 2.0 - MAHJONG_TILE_HEIGHT
                val stackNum = (index / 2) % 17
                val stackWidth = stackNum * (MAHJONG_TILE_WIDTH + MAHJONG_TILE_SMALL_PADDING)
                when (index / 34) {
                    0 -> { // Восток
                        tableCenterPos.add(directionOffset, yOffset, -startingPos + stackWidth).apply {
                            tile.refreshPositionAfterTeleport(this)
                            tile.yaw = -90f
                        }
                    }
                    1 -> { // Юг
                        tableCenterPos.add(startingPos - stackWidth, yOffset, directionOffset).apply {
                            tile.refreshPositionAfterTeleport(this)
                            tile.yaw = 0f
                        }
                    }
                    2 -> { // Запад
                        tableCenterPos.add(-directionOffset, yOffset, startingPos - stackWidth).apply {
                            tile.refreshPositionAfterTeleport(this)
                            tile.yaw = 90f
                        }
                    }
                    else -> { // Север
                        tableCenterPos.add(-startingPos + stackWidth, yOffset, -directionOffset).apply {
                            tile.refreshPositionAfterTeleport(this)
                            tile.yaw = 180f
                        }
                    }
                }
            }
            ServerScheduler.scheduleDelayAction { this.forEach { world.spawnEntity(it) } }
        }
        game.playSound(soundEvent = SoundEvents.ENTITY_ITEM_PICKUP)
    }

    /**
     * Убирает из [wall] последние 7 стопок и добавляет в [deadWall],
     * Немного сдвигает мёртвую стену, чтобы отделить её от [wall]
     * */
    // Метод, формирующий мертвую стену.
    fun assignDeadWall() {
        with(deadWall) {
            // Перемещаем последние 14 плиток из стены в мертвую стену.
            for (index in 0 until 14) {
                this += wall.last()
                wall.removeLast()
            }
            reverse() // Переворачиваем мертвую стену (чтобы плитки шли в правильном порядке)
            forEach {
                val gap = MAHJONG_TILE_SMALL_PADDING * 20
                when (it.horizontalFacing) { // В зависимости от направления плитки, сдвигаем ее
                    Direction.EAST -> it.refreshPositionAfterTeleport(it.x, it.y, it.z + gap)
                    Direction.SOUTH -> it.refreshPositionAfterTeleport(it.x - gap, it.y, it.z)
                    Direction.WEST -> it.refreshPositionAfterTeleport(it.x, it.y, it.z - gap)
                    Direction.NORTH -> it.refreshPositionAfterTeleport(it.x + gap, it.y, it.z)
                    else -> {
                    }
                }
            }
            // Выравниваем мертвую стену по направлению последней стопки
            val direction = deadWall.last().horizontalFacing
            reversed().let {
                it.forEachIndexed { index, mahjongTile ->
                    with(mahjongTile) {
                        // Если плитка смотрит в другую сторону, поворачиваем ее
                        if (this.horizontalFacing != direction) {
                            yaw = direction.asRotation()
                            if (index % 2 == 0) { // Нижний слой
                                // Плитки в мертвой стене лежат парами (одна над другой).  Нужно сдвинуть обе плитки
                                it.forEach { tile ->
                                    when (direction) {
                                        Direction.EAST -> tile.refreshPositionAfterTeleport(
                                            tile.x,
                                            tile.y,
                                            tile.z + MAHJONG_TILE_WIDTH // Сдвигаем вправо
                                        )
                                        Direction.SOUTH -> tile.refreshPositionAfterTeleport(
                                            tile.x - MAHJONG_TILE_WIDTH,  // Сдвигаем вниз
                                            tile.y,
                                            tile.z
                                        )
                                        Direction.WEST -> tile.refreshPositionAfterTeleport(
                                            tile.x,
                                            tile.y,
                                            tile.z - MAHJONG_TILE_WIDTH // Сдвигаем влево
                                        )
                                        Direction.NORTH -> tile.refreshPositionAfterTeleport(
                                            tile.x + MAHJONG_TILE_WIDTH, // Сдвигаем вверх
                                            tile.y,
                                            tile.z
                                        )
                                        else -> {
                                        }
                                    }
                                }
                            }
                            val posY = if (index % 2 == 0) it[0].y else it[1].y
                            val offset = (MAHJONG_TILE_WIDTH + MAHJONG_TILE_SMALL_PADDING) * (index / 2)
                            when (direction) {
                                Direction.EAST -> mahjongTile.refreshPositionAfterTeleport(
                                    it[0].x,
                                    posY,
                                    it[0].z - offset
                                )
                                Direction.SOUTH -> mahjongTile.refreshPositionAfterTeleport(
                                    it[0].x + offset,
                                    posY,
                                    it[0].z
                                )
                                Direction.WEST -> mahjongTile.refreshPositionAfterTeleport(
                                    it[0].x,
                                    posY,
                                    it[0].z + offset
                                )
                                Direction.NORTH -> mahjongTile.refreshPositionAfterTeleport(
                                    it[0].x - offset,
                                    posY,
                                    it[0].z
                                )
                                else -> {
                                }
                            }
                        }
                    }
                }
            }
            // Открываем индикаторы дора
            flipDoraIndicators()
        }
    }


    // Метод, раздающий плитки игрокам в начале раунда
    suspend fun assignWallAndHands(dicePoints: Int) {
        withContext(Dispatchers.Default) {
            val directionIndex = (4 - ((dicePoints % 4 - 1) + game.round.round) % 4) // Определяем, с какого направления начинать раздачу
            val startingStackIndex = 2 * dicePoints // Определяем, с какой стопки начинать раздачу
            val dealer = game.seat[game.round.round] // Определяем дилера (игрока, начинающего раунд)
            val newWall = MutableList(wall.size) {
                // Вычисляем индекс плитки в исходной стене, основываясь на направлении, стопке и порядке раздачи
                val tileIndex = (directionIndex * 34 + startingStackIndex + it) % wall.size
                wall[tileIndex]
            }
            // Раздаем плитки игрокам
            game.seat.forEach { mjPlayer ->
                // Дилер получает 14 плиток, остальные - по 13
                val tileAmount = if (mjPlayer == dealer) 14 else 13
                repeat(tileAmount) {
                    val tile = newWall.removeFirst() // Берем первую плитку из нового списка стены
                    tile.isInvisible = true // Делаем плитку невидимой (чтобы игроки не видели руки друг друга)
                    mjPlayer.drawTile(tile)
                    if (it == 13) sortHands(player = mjPlayer, lastTile = tile)
                    else sortHands(player = mjPlayer)
                }
            }
            // Заменяем старую стену новой (с учетом порядка раздачи)
            wall.clear()
            wall += newWall
            repeat(4) { times ->
                if (game.seat.size == 0) return@repeat
                val seatIndex = (game.round.round + times) % 4
                game.seat[seatIndex].hands.forEach { it.isInvisible = false }
                game.playSound(soundEvent = SoundEvents.ENTITY_ITEM_PICKUP, volume = 0.3f, pitch = 2.0f)
                delayOnServer(250)
            }
        }
    }

    // Метод, сортирующий руку игрока и расставляющий плитки перед ним
    fun sortHands(
        player: MahjongPlayerBase,
        lastTile: MahjongTileEntity? = null,
    ) {
        if (player !in game.seat) return
        val seatIndex = game.seat.indexOf(player)
        val tableCenterPos = game.tableCenterPos
        with(player) {
            val tileAmount = hands.size
            if (player.autoArrangeHands) hands.sortBy { it.mahjongTile.sortOrder }
            lastTile?.let {
                hands -= it
                hands += it
            }
            // Расставляем плитки перед игроком
            hands.forEachIndexed { index, hTile ->
                // Рассчитываем позицию и ориентацию плитки
                val directionOffset = 1.0 + MAHJONG_TILE_DEPTH + MAHJONG_TILE_HEIGHT
                val fuuroOffset =
                    if (fuuroList.size < 3) 0.0 else (fuuroList.size - 2.0) * (MAHJONG_TILE_WIDTH)
                val sticksOffset =
                    if (sticks.size < 3) 0.0 else (sticks.size - 2.0) * (MAHJONG_POINT_STICK_DEPTH)
                val startingPos =
                    (tileAmount * MAHJONG_TILE_WIDTH + (tileAmount - 1) * MAHJONG_TILE_SMALL_PADDING) / 2.0 + fuuroOffset + sticksOffset
                val stackOffset =
                    index * (MAHJONG_TILE_WIDTH + MAHJONG_TILE_SMALL_PADDING) + if (hTile == lastTile) MAHJONG_TILE_SMALL_PADDING * 15.0 else 0.0
                when (seatIndex) { // В зависимости от места игрока, определяем позицию и ориентацию плиток. Направления отсчитываются против часовой стрелки: восток, юг, запад, север
                    0 -> { // Восток
                        tableCenterPos.add(directionOffset, 0.0, startingPos - stackOffset).apply {
                            hTile.refreshPositionAfterTeleport(this)
                            hTile.yaw = -90f
                        }
                    }
                    3 -> { // Юг
                        tableCenterPos.add(-startingPos + stackOffset, 0.0, directionOffset).apply {
                            hTile.refreshPositionAfterTeleport(this)
                            hTile.yaw = 0f
                        }
                    }
                    2 -> { // Запад
                        tableCenterPos.add(-directionOffset, 0.0, -startingPos + stackOffset).apply {
                            hTile.refreshPositionAfterTeleport(this)
                            hTile.yaw = 90f
                        }
                    }
                    else -> { // Север
                        tableCenterPos.add(startingPos - stackOffset, 0.0, -directionOffset).apply {
                            hTile.refreshPositionAfterTeleport(this)
                            hTile.yaw = 180f
                        }
                    }
                }
            }
        }
    }


    // Метод, сортирующий сброшенные плитки игрока и расставляющий их перед ним
    fun sortDiscardedTilesForDisplay(
        player: MahjongPlayerBase, // Игрок, чьи сброшенные плитки нужно отсортировать
        openDoorPlayer: MahjongPlayerBase, // Игрок, открывший дверь (влияет на расположение сбросов)
    ) {
        if (player !in game.seat) return
        val seatIndex = game.seat.indexOf(player)
        val tableCenterPos = game.tableCenterPos
        // Вычисляем различные смещения, необходимые для правильного расположения плиток
        val halfWidthOfSixTiles = (MAHJONG_TILE_WIDTH * 6 / 2.0)
        val paddingFromCenter =
            halfWidthOfSixTiles + MAHJONG_TILE_HEIGHT / 2.0 + MAHJONG_TILE_HEIGHT / 4.0
        val basicOffset = halfWidthOfSixTiles - MAHJONG_TILE_WIDTH / 2.0
        val halfTileWidthAndHalfTileHeight = (MAHJONG_TILE_HEIGHT + MAHJONG_TILE_WIDTH) / 2.0
        val startingPos = when (seatIndex) { // Начальная позиция первой сброшенной плитки. Направления отсчитываются против часовой стрелки: восток, юг, запад, север
            0 -> tableCenterPos.add(paddingFromCenter, 0.0, basicOffset) // Восток
            3 -> tableCenterPos.add(-basicOffset, 0.0, paddingFromCenter) // Юг
            2 -> tableCenterPos.add(-paddingFromCenter, 0.0, -basicOffset) // Запад
            else -> tableCenterPos.add(basicOffset, 0.0, -paddingFromCenter) // Север
        }
        val tileOffset = when (seatIndex) { // Смещение для обычных плиток (по горизонтали)
            0 -> Vec3d(0.0, 0.0, -MAHJONG_TILE_WIDTH.toDouble())
            3 -> Vec3d(MAHJONG_TILE_WIDTH.toDouble(), 0.0, 0.0)
            2 -> Vec3d(0.0, 0.0, MAHJONG_TILE_WIDTH.toDouble())
            else -> Vec3d(-MAHJONG_TILE_WIDTH.toDouble(), 0.0, 0.0)
        }
        val riichiTileOffset = when (seatIndex) { // Смещение для плитки, сброшенной при объявлении риичи (по горизонтали)
            0 -> Vec3d(0.0, 0.0, -halfTileWidthAndHalfTileHeight)
            3 -> Vec3d(halfTileWidthAndHalfTileHeight, 0.0, 0.0)
            2 -> Vec3d(0.0, 0.0, halfTileWidthAndHalfTileHeight)
            else -> Vec3d(-halfTileWidthAndHalfTileHeight, 0.0, 0.0)
        }
        val lineOffset = when (seatIndex) {  // Смещение между рядами сброшенных плиток (по вертикали)
            0 -> Vec3d(MAHJONG_TILE_HEIGHT.toDouble() + MAHJONG_TILE_SMALL_PADDING, 0.0, 0.0)
            3 -> Vec3d(0.0, 0.0, MAHJONG_TILE_HEIGHT.toDouble() + MAHJONG_TILE_SMALL_PADDING)
            2 -> Vec3d(-MAHJONG_TILE_HEIGHT.toDouble() - MAHJONG_TILE_SMALL_PADDING, 0.0, 0.0)
            else -> Vec3d(0.0, 0.0, -MAHJONG_TILE_HEIGHT.toDouble() - MAHJONG_TILE_SMALL_PADDING)
        }
        val smallGapOffset = when (seatIndex) { // Небольшое смещение между плитками в ряду
            0 -> Vec3d(0.0, 0.0, -MAHJONG_TILE_SMALL_PADDING)
            3 -> Vec3d(MAHJONG_TILE_SMALL_PADDING, 0.0, 0.0)
            2 -> Vec3d(0.0, 0.0, MAHJONG_TILE_SMALL_PADDING)
            else -> Vec3d(-MAHJONG_TILE_SMALL_PADDING, 0.0, 0.0)
        }
        val tileRot = when (seatIndex) { // Ориентация плиток (угол поворота)
            0 -> -90f
            3 -> 0f
            2 -> 90f
            else -> 180f
        }
        var nowPos = startingPos
        // Определяем плитку, сброшенную при объявлении риичи
        val riichiSengenTile = player.riichiSengenTile?.let {
            if (it !in player.discardedTilesForDisplay) {
                val indexOfRiichiSengenTile = player.discardedTiles.indexOf(it)  // Находим индекс плитки риичи в общем списке сбросов игрока
                player.discardedTiles.let { tiles ->
                    tiles.find { tile -> // Находим первую плитку, сброшенную после плитки риичи
                        tiles.indexOf(tile) > indexOfRiichiSengenTile
                    }
                }
            } else it
        }
        // Размещаем сброшенные плитки
        player.discardedTilesForDisplay.forEachIndexed { index, tileEntity ->
            val firstTileInThisLine = index % 6 == 0
            val isRiichiTile = tileEntity == riichiSengenTile
            val lastTileIsRiichiTile =
                if (index == 0) false else player.discardedTilesForDisplay[index - 1] == riichiSengenTile
            val lineCount = index / 6
            if (lineCount > 0 && firstTileInThisLine) {
                if (!(openDoorPlayer == player && index >= 18)) {
                    nowPos = startingPos
                    repeat(lineCount) { nowPos = nowPos.add(lineOffset) }
                }
            }
            nowPos =
                if (firstTileInThisLine) {
                    if (isRiichiTile) nowPos.add(riichiTileOffset).subtract(tileOffset)
                    else nowPos
                } else {
                    if (isRiichiTile || lastTileIsRiichiTile) nowPos.add(riichiTileOffset)
                    else nowPos.add(tileOffset)
                }
            nowPos = if (firstTileInThisLine) nowPos else nowPos.add(smallGapOffset)
            val yRot = if (isRiichiTile) tileRot - 90 else tileRot
            tileEntity.refreshPositionAndAngles(nowPos.x, nowPos.y, nowPos.z, yRot, 0f)
            tileEntity.facing = TileFacing.UP
        }
    }

    /**
     * Упорядочивает открытые наборы (фуро: чи, пон, кан) указанного игрока.
     * Расставляет их перед игроком, ближе к правому углу стола.
     * Корректирует позицию фуро, чтобы учесть положение палочек-счетчиков (хонба).
     */
    fun sortFuuro(
        player: MahjongPlayerBase, // Игрок, чьи фуро нужно упорядочить
    ) {
        // Важно: координаты сущности (entity) в Minecraft указывают на центр нижней части сущности
        if (player !in game.seat) return
        val seatIndex = game.seat.indexOf(player)
        val tableCenterPos = game.tableCenterPos
        val halfTableLengthNoBorder = 0.5 + 15.0 / 16.0 // Половина длины стола без учета рамки (бортика)
        val halfHeightOfTile = MAHJONG_TILE_HEIGHT / 2.0 // Половина высоты плитки маджонга
        val startingPos = when (seatIndex) { // Вычисляем начальную позицию для размещения фуро (правый угол стола перед игроком)
            // Направления (восток, юг, запад, север) здесь указаны относительно *центра стола*, т.е. как игрок видит стол перед собой
            0 -> tableCenterPos.add(halfTableLengthNoBorder - halfHeightOfTile, 0.0, -halfTableLengthNoBorder) // Восток
            3 -> tableCenterPos.add(halfTableLengthNoBorder, 0.0, halfTableLengthNoBorder - halfHeightOfTile) // Юг
            2 -> tableCenterPos.add(-halfTableLengthNoBorder + halfHeightOfTile, 0.0, halfTableLengthNoBorder) // Запад
            else -> tableCenterPos.add(-halfTableLengthNoBorder, 0.0, -halfTableLengthNoBorder + halfHeightOfTile) // Север
        }.let {
            // После определения начальной позиции угла, проверяем, есть ли там палочки-счетчики (хонба)

            // Ищем последнюю палочку в первой стопке палочек игрока.  `null`, если палочек нет
            val lastStickOfFirstStack =
                player.sticks.findLast { stick -> player.sticks.indexOf(stick) < MahjongGame.STICKS_PER_STACK }
                    ?: return@let it

            // Если палочки есть
            val stickPos = lastStickOfFirstStack.pos
            val offset = stickPos.subtract(it)
            val halfDepthOfStick = MAHJONG_POINT_STICK_DEPTH / 2.0
            val pos = when (seatIndex) { // Вычисляем новую позицию угла, учитывая только смещение, параллельное направлению палочек
                0 -> it.add(offset.multiply(0.0, 0.0, 1.0))    // Восток: учитываем только смещение по Z
                3 -> it.add(offset.multiply(1.0, 0.0, 0.0))    // Юг: учитываем только смещение по X
                2 -> it.add(offset.multiply(0.0, 0.0, 1.0))    // Запад: учитываем только смещение по Z
                else -> it.add(offset.multiply(1.0, 0.0, 0.0)) // Север: учитываем только смещение по X
            }
            when (seatIndex) { // Добавляем половину глубины палочки, чтобы выровнять фуро по краю палочек
                0 -> pos.add(0.0, 0.0, halfDepthOfStick)
                3 -> pos.add(-halfDepthOfStick, 0.0, 0.0)
                2 -> pos.add(0.0, 0.0, -halfDepthOfStick)
                else -> pos.add(halfDepthOfStick, 0.0, 0.0)
            }
        }
        val tileGap = MAHJONG_TILE_SMALL_PADDING
        val verticalTileOffset = when (seatIndex) { // Смещение для вертикально расположенных плиток (относительно края стола, справа налево)
            0 -> Vec3d(0.0, 0.0, MAHJONG_TILE_WIDTH.toDouble() + tileGap)
            3 -> Vec3d(-MAHJONG_TILE_WIDTH.toDouble() - tileGap, 0.0, 0.0)
            2 -> Vec3d(0.0, 0.0, -MAHJONG_TILE_WIDTH.toDouble() - tileGap)
            else -> Vec3d(MAHJONG_TILE_WIDTH.toDouble() + tileGap, 0.0, 0.0)
        }
        val halfVerticalTileOffset = when (seatIndex) { // Смещение для половины вертикально расположенной плитки (относительно края стола, справа налево)
            0 -> verticalTileOffset.multiply(1.0, 1.0, 0.5)
            3 -> verticalTileOffset.multiply(0.5, 1.0, 1.0)
            2 -> verticalTileOffset.multiply(1.0, 1.0, 0.5)
            else -> verticalTileOffset.multiply(0.5, 1.0, 1.0)
        }
        val horizontalTileOffset = when (seatIndex) { // Смещение для горизонтально расположенных плиток (относительно края стола, справа налево)
            0 -> Vec3d(0.0, 0.0, MAHJONG_TILE_HEIGHT.toDouble() + tileGap)
            3 -> Vec3d(-MAHJONG_TILE_HEIGHT.toDouble() - tileGap, 0.0, 0.0)
            2 -> Vec3d(0.0, 0.0, -MAHJONG_TILE_HEIGHT.toDouble() - tileGap)
            else -> Vec3d(MAHJONG_TILE_HEIGHT.toDouble() + tileGap, 0.0, 0.0)
        }
        val halfHorizontalTileOffset = when (seatIndex) { // Смещение для половины горизонтально расположенной плитки (относительно края стола, справа налево)
            0 -> horizontalTileOffset.multiply(1.0, 1.0, 0.5)
            3 -> horizontalTileOffset.multiply(0.5, 1.0, 1.0)
            2 -> horizontalTileOffset.multiply(1.0, 1.0, 0.5)
            else -> horizontalTileOffset.multiply(0.5, 1.0, 1.0)
        }
        val kakanOffset = when (seatIndex) { // Смещение для плитки, добавленной при объявлении кана
            0 -> Vec3d(-MAHJONG_TILE_WIDTH.toDouble() - tileGap, 0.0, 0.0)
            3 -> Vec3d(0.0, 0.0, -MAHJONG_TILE_WIDTH.toDouble() - tileGap)
            2 -> Vec3d(MAHJONG_TILE_WIDTH.toDouble() + tileGap, 0.0, 0.0)
            else -> Vec3d(0.0, 0.0, MAHJONG_TILE_WIDTH.toDouble() + tileGap)
        }
        val halfGapBetweenHeightAndWidth = (MAHJONG_TILE_HEIGHT - MAHJONG_TILE_WIDTH) / 2.0
        val horizontalTileGravityOffset = when (seatIndex) { // Смещение, учитывающее "притяжение" горизонтально расположенных плиток (относительно края стола, справа налево)
            0 -> Vec3d(halfGapBetweenHeightAndWidth, 0.0, 0.0)
            3 -> Vec3d(0.0, 0.0, halfGapBetweenHeightAndWidth)
            2 -> Vec3d(-halfGapBetweenHeightAndWidth, 0.0, 0.0)
            else -> Vec3d(0.0, 0.0, -halfGapBetweenHeightAndWidth)
        }

        val tileRot = when (seatIndex) { // Поворот плиток
            0 -> -90f
            3 -> 0f
            2 -> 90f
            else -> 180f
        }
        var nowPos = startingPos
        var tileCount = 0
        var lastTile: MahjongTileEntity? = null
        var lastClaimTile: MahjongTileEntity? = null
        // Внутренняя функция (локальная функция) для размещения каждой плитки в фуро
        fun placeEachTile(fuuro: Fuuro) {

            val isKakan = fuuro.mentsu is Kakantsu

            // Список плиток, которые нужно разместить
            val tiles =
                if (isKakan) fuuro.tileMjEntities.toMutableList()  // Для какана порядок плиток не меняется.
                else fuuro.tileMjEntities.sortedByDescending { it.mahjongTile.sortOrder }.toMutableList() // Для остальных наборов сортируем по убыванию кода плитки

            // Плитка, добавленная при какане, всегда идет последней.  Удаляем ее из списка и добавим позже
            val kakanTile = if (isKakan) tiles.removeLast() else null


            tiles -= fuuro.claimTile

            // Возвращаем "украденную" плитку на правильную позицию в списке
            when (fuuro.claimTarget) {
                ClaimTarget.RIGHT -> tiles.add(0, fuuro.claimTile) // Украдена справа: добавляем в начало
                ClaimTarget.LEFT -> tiles.add(fuuro.claimTile) // Украдена слева: добавляем в конец
                ClaimTarget.ACROSS -> tiles.add(1, fuuro.claimTile) // Украдена напротив: добавляем в середину
                else -> {}
            }

            // Размещаем каждую плитку
            tiles.forEach {
                // Является ли текущая плитка "украденной"
                val isClaimTile = it == fuuro.claimTile

                // Была ли предыдущая плитка расположена горизонтально
                val isLastTileHorizontal = lastTile != null && lastClaimTile != null && lastTile == lastClaimTile

                // Вычисляем позицию для текущей плитки
                nowPos = when {

                    tileCount == 0 -> {
                        if (isClaimTile) nowPos.add(halfHorizontalTileOffset) // Если "украденная" - горизонтальное смещение
                        else nowPos.add(halfVerticalTileOffset) // Иначе - вертикальное смещение
                    }
                    // Текущая плитка "украдена", ИЛИ предыдущая плитка была горизонтальной
                    isClaimTile || isLastTileHorizontal -> {
                        if (isClaimTile && isLastTileHorizontal) nowPos.add(horizontalTileOffset) // Если и текущая "украдена", и предыдущая горизонтальна - горизонтальное смещение
                        else nowPos.add(halfHorizontalTileOffset).add(halfVerticalTileOffset) // Иначе - сначала горизонтальное, потом вертикальное
                    }
                    else -> nowPos.add(verticalTileOffset)
                }

                val pos = if (!isClaimTile) nowPos else nowPos.add(horizontalTileGravityOffset)
                val yRot = // Угол поворота плитки
                    if (it == fuuro.claimTile)
                        when (fuuro.claimTarget) {
                            ClaimTarget.RIGHT -> tileRot + 90
                            ClaimTarget.LEFT -> tileRot - 90
                            ClaimTarget.ACROSS -> tileRot + 90
                            else -> tileRot
                        }
                    else tileRot


                it.refreshPositionAndAngles(pos.x, pos.y, pos.z, yRot, 0f)
                it.facing = TileFacing.UP

                lastTile = it
                if (isClaimTile) lastClaimTile = it
                tileCount++
            }

            // Если есть плитка, добавленная при какане, размещаем ее над "украденной" плиткой
            kakanTile?.let {
                val claimTile = fuuro.claimTile
                val claimTilePos = fuuro.claimTile.pos
                val pos = claimTilePos.add(kakanOffset)
                it.refreshPositionAndAngles(pos.x, pos.y, pos.z, claimTile.yaw, 0f)
                it.facing = TileFacing.UP
            }
        }

        // Размещаем наборы фуро в соответствии с порядком их объявления
        player.fuuroList.forEach { fuuro ->
            if (fuuro.mentsu is Kantsu && fuuro.claimTarget == ClaimTarget.SELF) {
                // Если это *закрытый* кан (сделан из собственных плиток).
                // Закрытые каны размещаются проще, т.к. все плитки стоят вертикально
                fuuro.tileMjEntities.forEachIndexed { index, tileMjEntity ->
                    val isLastTileHorizontal = lastTile != null && lastClaimTile != null && lastTile == lastClaimTile


                    nowPos = when {
                        tileCount == 0 -> nowPos.add(halfVerticalTileOffset)
                        isLastTileHorizontal -> nowPos.add(halfHorizontalTileOffset).add(halfVerticalTileOffset)
                        else -> nowPos.add(verticalTileOffset)
                    }


                    tileMjEntity.refreshPositionAndAngles(nowPos.x, nowPos.y, nowPos.z, tileRot, 0f)
                    // index == 1 or 2:  средние две плитки лицом вверх, остальные - лицом вниз
                    tileMjEntity.facing = if (index == 1 || index == 2) TileFacing.UP else TileFacing.DOWN

                    lastTile = tileMjEntity
                    tileCount++
                }
            } else { // Открытый кан, чи или пон
                placeEachTile(fuuro = fuuro)
            }
        }
    }

    /**
     * Игрок берет плитку из мертвой стены (после объявления кана).
     *
     */
    fun drawRinshanTile(player: MahjongPlayerBase): MahjongTileEntity {
        // Определяем, какую плитку взять из мертвой стены (в зависимости от четности kanCount)
        val tile = if (kanCount % 2 == 0) deadWall[deadWall.size - 2] else deadWall[deadWall.size - 1]
        val lastWallTile = wall.removeLast()
        val direction = deadWall.last().horizontalFacing
        val baseTile = deadWall[1]
        val basePos = baseTile.pos
        val tilePos = when (direction) {
            Direction.EAST -> basePos.add(0.0, 0.0, -MAHJONG_TILE_WIDTH.toDouble())
            Direction.SOUTH -> basePos.add(MAHJONG_TILE_WIDTH.toDouble(), 0.0, 0.0)
            Direction.WEST -> basePos.add(0.0, 0.0, MAHJONG_TILE_WIDTH.toDouble())
            Direction.NORTH -> basePos.add(-MAHJONG_TILE_WIDTH.toDouble(), 0.0, 0.0)
            else -> basePos
        }
        lastWallTile.refreshPositionAfterTeleport(tilePos)
        lastWallTile.yaw = direction.asRotation()
        deadWall.add(0, lastWallTile)
        deadWall -= tile
        kanCount++
        flipDoraIndicators()
        player.drawTile(tile)
        // Если количество канов нечетное, опускаем последнюю плитку стены вниз (чтобы она не висела в воздухе)
        if (kanCount % 2 == 1) {
            val nowLastWallTile = wall.last()
            nowLastWallTile.refreshPositionAfterTeleport(
                nowLastWallTile.pos.add(
                    0.0,
                    -MAHJONG_TILE_DEPTH.toDouble(),
                    0.0
                )
            )
        }
        return tile
    }

    /**
     * Размещает палочку риичи данного игрока.
     * Вызывается только при объявлении риичи.
     * Одна палочка риичи помещается в центр стола, остальные - в зону фуро.
     */
    fun putRiichiStick(player: MahjongPlayerBase) {
        if (player !in game.seat) return
        val seatIndex = game.seat.indexOf(player)
        val tableCenterPos = game.tableCenterPos
        val halfWidthOfSixTiles = (MAHJONG_TILE_WIDTH * 6 / 2.0)
        val paddingFromCenter =
            halfWidthOfSixTiles - MAHJONG_POINT_STICK_DEPTH / 2.0
        val stickPos = when (seatIndex) { // Направления: восток, юг, запад, север (против часовой стрелки, если смотреть из центра стола)
            0 -> tableCenterPos.add(paddingFromCenter, 0.0, 0.0)
            3 -> tableCenterPos.add(0.0, 0.0, paddingFromCenter)
            2 -> tableCenterPos.add(-paddingFromCenter, 0.0, 0.0)
            else -> tableCenterPos.add(0.0, 0.0, -paddingFromCenter)
        }
        // Ориентация палочки риичи
        val stickYaw = when (seatIndex) {
            0 -> -90f
            3 -> 0f
            2 -> 90f
            else -> 180f
        }
        // Создаем и добавляем сущность палочки риичи (1000 очков)
        player.sticks += ScoringStickEntity(world = game.world).apply {
            code = ScoringStick.P1000.code
            gameBlockPos = game.pos
            isSpawnedByGame = true
            refreshPositionAfterTeleport(stickPos)
            yaw = stickYaw
            ServerScheduler.scheduleDelayAction { world.spawnEntity(this) }
        }
    }

    /**
     * Перемещает указанную палочку [stick] в зону для палочек в позицию [index].
     * Зона для палочек находится там же, где и фуро.
     */
    private fun moveStickTo(player: MahjongPlayerBase, index: Int, stick: ScoringStickEntity) {
        if (player !in game.seat) return
        val seatIndex = game.seat.indexOf(player)
        val tableCenterPos = game.tableCenterPos
        val halfTableLengthNoBorder = 0.5 + 15.0 / 16.0
        val halfWidthOfStick = MAHJONG_POINT_STICK_WIDTH / 2.0
        val halfDepthOfStick = MAHJONG_POINT_STICK_DEPTH / 2.0
        val startingPos = when (seatIndex) {
            0 -> tableCenterPos.add(
                halfTableLengthNoBorder - halfWidthOfStick,
                0.0,
                -halfTableLengthNoBorder + halfDepthOfStick
            ) // Восток
            3 -> tableCenterPos.add(
                halfTableLengthNoBorder - halfDepthOfStick,
                0.0,
                halfTableLengthNoBorder - halfWidthOfStick
            ) // Юг
            2 -> tableCenterPos.add(
                -halfTableLengthNoBorder + halfWidthOfStick,
                0.0,
                halfTableLengthNoBorder - halfDepthOfStick
            ) // Запад
            else -> tableCenterPos.add(
                -halfTableLengthNoBorder + halfDepthOfStick,
                0.0,
                -halfTableLengthNoBorder + halfWidthOfStick
            ) // Север
        }
        val stickGap = MAHJONG_TILE_SMALL_PADDING
        val stickOffset = when (seatIndex) {
            0 -> Vec3d(0.0, 0.0, MAHJONG_POINT_STICK_DEPTH + stickGap)
            3 -> Vec3d(-MAHJONG_POINT_STICK_DEPTH - stickGap, 0.0, 0.0)
            2 -> Vec3d(0.0, 0.0, -MAHJONG_POINT_STICK_DEPTH - stickGap)
            else -> Vec3d(MAHJONG_POINT_STICK_DEPTH + stickGap, 0.0, 0.0)
        }
        val stackOffset = // Смещение по высоте, если палочки складываются в несколько стопок
            Vec3d(0.0, MAHJONG_POINT_STICK_HEIGHT + stickGap, 0.0)
        val stickYaw = when (seatIndex) {
            0 -> -90f
            3 -> 0f
            2 -> 90f
            else -> 180f
        } - 90f
        val stackIndex = (index / MahjongGame.STICKS_PER_STACK).toDouble()
        val stickIndex = (index % MahjongGame.STICKS_PER_STACK).toDouble()
        val offsetXZ = stickOffset.multiply(stickIndex, stickIndex, stickIndex)
        val offsetY = stackOffset.multiply(stackIndex, stackIndex, stackIndex)
        val pos = startingPos.add(offsetXZ).add(offsetY)
        stick.yaw = stickYaw
        stick.refreshPositionAfterTeleport(pos)
    }

    /**
     * Перемещает указанную палочку [stick] в конец зоны для палочек
     */
    private fun moveStickToLast(player: MahjongPlayerBase, stick: ScoringStickEntity) =
        moveStickTo(player = player, stick = stick, index = player.sticks.lastIndex + 1)

    /**
     * Переставляет палочки данного игрока (сортирует по значению и размещает)
     */
    fun resortSticks(player: MahjongPlayerBase) =
        player.sticks.sortedBy { it.code }
            .forEachIndexed { index, stick ->
                moveStickTo(
                    player = player,
                    index = index,
                    stick = stick
                )
            }

    /**
     * Добавляет палочку хонба (100 очков) данному игроку.
     * Вызывается только при ничьей (рюкёку).
     * Палочка хонба помещается рядом с последней палочкой игрока.
     */
    fun addHonbaStick(player: MahjongPlayerBase) {
        if (player !in game.seat) return
        player.sticks += ScoringStickEntity(world = game.world).apply {
            code = ScoringStick.P100.code
            gameBlockPos = game.pos
            isSpawnedByGame = true
            moveStickToLast(player = player, stick = this)
            ServerScheduler.scheduleDelayAction { world.spawnEntity(this) }
        }
    }

    /**
     * Убирает все палочки хонба (100 очков) у данного игрока.
     */
    fun removeHonbaSticks(player: MahjongPlayerBase) {
        if (player !in game.seat) return
        player.sticks.filter { it.scoringStick == ScoringStick.P100 }.forEach {
            it.remove(Entity.RemovalReason.DISCARDED)
            player.sticks -= it
        }
        resortSticks(player)
    }
}