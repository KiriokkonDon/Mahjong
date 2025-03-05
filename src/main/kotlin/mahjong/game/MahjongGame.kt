package mahjong.game

import mahjong.MOD_ID
import mahjong.block.MahjongStool
import mahjong.entity.*
import mahjong.game.game_logic.*
import mahjong.game.player.MahjongBot
import mahjong.game.player.MahjongPlayer
import mahjong.logger
import mahjong.network.MahjongGamePayload
import mahjong.network.MahjongTablePayloadListener
import mahjong.network.sendPayloadToPlayer
import mahjong.scheduler.client.ScoreSettleHandler
import mahjong.scheduler.client.YakuSettleHandler
import mahjong.scheduler.server.ServerScheduler
import mahjong.util.delayOnServer
import mahjong.util.plus
import mahjong.util.sendTitles
import kotlinx.coroutines.*
import mahjong.entity.BotEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mahjong.game.player.MahjongPlayerBase
import mahjong.registry.SoundRegistry
import net.minecraft.entity.Entity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.HoverEvent
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import org.mahjong4j.PersonalSituation
import org.mahjong4j.hands.Kantsu
import kotlin.math.abs



class MahjongGame(
    override val world: ServerWorld,
    override val pos: BlockPos,
    var rule: MahjongRule = MahjongRule(),
) : GameBase<MahjongPlayerBase> {

    private var botCounter = 1

    override val name = Text.translatable("$MOD_ID.game.riichi_mahjong")

    override var status = GameStatus.WAITING

    private val isPlaying: Boolean
        get() = status == GameStatus.PLAYING

    override val players = ArrayList<MahjongPlayerBase>(4)

    private val realPlayers: List<MahjongPlayer>
        get() = players.filterIsInstance<MahjongPlayer>()

    private val botPlayers: List<MahjongBot>
        get() = players.filterIsInstance<MahjongBot>()

    private val board = MahjongBoard(this)


    val tableCenterPos = Vec3d(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5)

    var seat = mutableListOf<MahjongPlayerBase>()

    // Свойство: список игроков, упорядоченных от дилера (по часовой стрелке)
    private val seatOrderFromDealer: List<MahjongPlayerBase>
        get() = List(4) {
            val dealerIndex = round.round
            seat[(dealerIndex + it) % 4]
        }

    var round: MahjongRound = MahjongRound()

    private var jobRound: Job? = null
    private var jobWaitForStart: Job? = null
    private var dicePoints = 0

    //Метод изменения правил
    fun changeRules(rule: MahjongRule) {
        this.rule = rule
        players.forEachIndexed { index, mjPlayer ->
            if (mjPlayer is MahjongPlayer) {
                if (index == 0) {
                    mjPlayer.sendMessage(
                        text = PREFIX + Text.translatable("$MOD_ID.game.message.you_changed_the_rules")
                    )
                } else {
                    mjPlayer.ready = false
                    mjPlayer.sendMessage(
                        text = PREFIX + Text.translatable("$MOD_ID.game.message.host_changed_the_rules")
                    )
                }
            }
        }
    }

    //Метод добавления бота
    fun addBot() {
        if (botCounter <= 3) {
            val bot = MahjongBot(
                world = world,
                pos = tableCenterPos,
                gamePos = pos,
                tableCenterPos = tableCenterPos,
                botNumber = botCounter
            )
            players += bot
            botCounter++
        } else {
            logger.warn("Достигнуто максимальное количество ботов (3).")

        }
    }


    //Устанавливает готовность игрока
    fun readyOrNot(player: ServerPlayerEntity, ready: Boolean) {
        getPlayer(player)?.ready = ready
    }

    //Кикнуть игрока
    fun kick(index: Int) {
        if (index in players.indices) {
            val player = players.removeAt(index)
            if (player is MahjongPlayer) {
                player.sendMessage(PREFIX + Text.translatable("$MOD_ID.game.message.be_kick"))
            } else {
                player.entity.remove(Entity.RemovalReason.KILLED)
            }
        }
    }

    //Присоединение игрока
    override fun join(player: ServerPlayerEntity) {
        if (GameManager.isInAnyGame(player) || isInGame(player)) return
        players += MahjongPlayer(entity = player)
        if (isHost(player)) players[0].ready = true
    }

    //Выход игрока из игры
    override fun leave(player: ServerPlayerEntity) {
        if (!GameManager.isInAnyGame(player) || !isInGame(player)) return
        if (isHost(player)) {
            players.find { it is MahjongPlayer && it.entity != player }.apply {
                if (this != null) {
                    players.remove(this)
                    players.add(0, this)
                    this.ready = true
                } else {
                    botPlayers.forEach { it.entity.remove(Entity.RemovalReason.DISCARDED) }
                    players.clear()
                }
            }
        }
        players.removeIf { it.entity == player }
    }

    //Метод очищения стола
    private fun clearStuffs(clearRiichiSticks: Boolean = true) {
        players.forEach {
            if (clearRiichiSticks) {
                val riichiSticks = it.sticks.filter { stick -> stick.scoringStick == ScoringStick.P1000 }
                ServerScheduler.scheduleDelayAction {
                    riichiSticks.forEach { stick ->
                        stick.remove(Entity.RemovalReason.DISCARDED)
                        it.sticks -= stick
                    }
                }
            }
            it.riichi = false
            it.doubleRiichi = false
            it.hands.clear()
            it.fuuroList.clear()
            it.discardedTiles.clear()
            it.discardedTilesForDisplay.clear()
            it.riichiSengenTile = null
        }
        dicePoints = 0
        board.clear()
    }

    //Показывает заголовок раунда
    private fun showRoundsTitle() {
        val windText = round.wind.toText()
        val countersText = Text.translatable("$MOD_ID.game.repeat_counter", round.honba).formatted(Formatting.YELLOW)
        realPlayers.map { it.entity }.sendTitles(
            title = Text.translatable("$MOD_ID.game.round.title", windText, round.round + 1)
                .formatted(Formatting.GOLD)
                .formatted(Formatting.BOLD),
            subtitle = Text.literal("§c - ") + countersText + "§c - "
        )
    }

    //Начинает раунд
    private fun startRound(clearRiichiSticks: Boolean = true) {
        if (status != GameStatus.PLAYING) return
        val handler = CoroutineExceptionHandler { _, exception ->
            logger.warn("Something happened, I hope you can report it.", exception)
        }
        jobRound?.cancel()
        jobRound = CoroutineScope(Dispatchers.IO).launch(handler) {
            clearStuffs(clearRiichiSticks = clearRiichiSticks)
            showRoundsTitle()
            syncMahjongTable()
            board.generateAllTilesAndSpawnWall()
            val dealer = seatOrderFromDealer[0]
            var dealerRemaining = false
            var clearNextRoundRiichiSticks = true
            var roundExhaustiveDraw: ExhaustiveDraw? = null
            delayOnServer(0)
            players.forEach { board.resortSticks(it) }
            delayOnServer(1000)

            val dices = rollDice()
            val openDoorPlayerSeatIndexFromDealer = ((dicePoints - 1) % 4 + round.round) % 4
            val openDoorPlayer = seatOrderFromDealer[openDoorPlayerSeatIndexFromDealer]
            board.assignWallAndHands(dicePoints = dicePoints)
            board.assignDeadWall()
            delayOnServer(500)


            dices.forEach { it.remove(Entity.RemovalReason.DISCARDED) }
            delayOnServer(1000)

            var nextPlayer: MahjongPlayerBase = dealer
            var drawTile = true
            var drewTile: Boolean
            val cannotDiscardTiles = mutableListOf<MahjongTile>()


            roundLoop@ while (isPlaying) { //Цикл раунда

                val player = nextPlayer
                val seatIndex = seat.indexOf(player)
                val isDealer = dealer == player
                var timeoutTile = player.hands.last()

                if (drawTile) {
                    val lastTile =
                        if (isDealer && player.discardedTiles.size == 0) player.hands.last()
                        else board.wall
                            .removeFirst()
                            .also {
                                player.drawTile(it)
                                board.sortHands(player = player, lastTile = it)
                            }
                    drewTile = true
                    if (player is MahjongBot) delayOnServer(500)

                    //Может ли игрок объявить цумо
                    val canWin = player.canWin(
                        winningTile = lastTile.mahjongTile,
                        isWinningTileInHands = true,
                        rule = rule,
                        generalSituation = board.generalSituation,
                        personalSituation = player.getPersonalSituation(isTsumo = true)
                    )
                    if (canWin && player.askToTsumo()) { //Если игрок может и хочет объявить цумо
                        player.tsumo(tile = lastTile)
                        if (isDealer) dealerRemaining = true
                        break@roundLoop
                    } else {

                        if (player.isKyuushuKyuuhai() && player.askToKyuushuKyuuhai()) {
                            roundExhaustiveDraw = ExhaustiveDraw.KYUUSHU_KYUUHAI
                            break@roundLoop
                        }

                        var finalRinshanTile: MahjongTileEntity? = null
                        kanLoop@ while ((player.canKakan || player.canAnkan) && !board.isHoutei && board.kanCount < 4) {
                            val tileToAnkanOrKakan = player.askToAnkanOrKakan(rule)
                            logger.info("Player chose tile for Kan: $tileToAnkanOrKakan, canAnkan: ${player.canAnkan}, canKakan: ${player.canKakan}")
                            if (tileToAnkanOrKakan != null) {
                                val isAnkan = tileToAnkanOrKakan in player.tilesCanAnkan.toList().toMahjongTileList()
                                val tileEntityToAnkanOrKakan = player.hands.find { it.mahjongTile == tileToAnkanOrKakan }
                                if (tileEntityToAnkanOrKakan == null) {
                                    logger.warn("Tile $tileToAnkanOrKakan not found in ${player.displayName}'s hands: ${player.hands.map { it.mahjongTile }}")
                                    continue@roundLoop // Пропускаем ход, если тайл не в руке
                                }

                                if (isAnkan) {
                                    player.ankan(tileEntityToAnkanOrKakan)
                                } else {
                                    player.kakan(tileEntityToAnkanOrKakan)
                                }

                                if (isAnkan) player.ankan(tileEntityToAnkanOrKakan) {
                                    it.playSoundAtHandsMiddle(soundEvent = SoundRegistry.kan)
                                } else player.kakan(tileEntityToAnkanOrKakan) {
                                    it.playSoundAtHandsMiddle(soundEvent = SoundRegistry.kan)
                                }

                                board.kanCount++

                                ServerScheduler.scheduleDelayAction {
                                    board.sortFuuro(player)
                                    syncMahjongTable()
                                }


                                //board.sortFuuro(player = player)

                                //Проверяем, могут ли другие игроки объявить рон по плитке, использованной для кана
                                //Список игроков которые могут украсть кан
                                val canChankanList =
                                    if (isAnkan) canChanAnkanList(tileEntityToAnkanOrKakan, player)
                                    else canChanKakanList(tileEntityToAnkanOrKakan, player) //Если кан открытый
                                if (canChankanList.isNotEmpty()) { //Если несколько игроков могут объявить рон
                                    if (canChankanList.size > 1) {
                                        canChankanList.ron(
                                            target = player,
                                            isChankan = true,
                                            tile = tileEntityToAnkanOrKakan
                                        )
                                        if (dealer in canChankanList) dealerRemaining = true
                                        break@roundLoop
                                    } else { //Если только один игрок может объявить рон
                                        val canChanKanPlayer = canChankanList[0]
                                        if (canChanKanPlayer.askToRon(tileEntityToAnkanOrKakan, canChanKanPlayer.asClaimTarget(player))) {
                                            mutableListOf(canChanKanPlayer).ron(target = player, isChankan = true, tile = tileEntityToAnkanOrKakan)
                                            if (dealer == canChanKanPlayer) dealerRemaining = true
                                            break@roundLoop
                                        }
                                    }
                                }
                                val rinshanTile = board.drawRinshanTile(player = player)
                                board.sortHands(player = player, lastTile = rinshanTile)

                                val rinshanKaihoh = player.canWin(
                                    winningTile = rinshanTile.mahjongTile,
                                    isWinningTileInHands = true,
                                    rule = rule,
                                    generalSituation = board.generalSituation,
                                    personalSituation = player.getPersonalSituation(
                                        isTsumo = true,
                                        isRinshanKaihoh = true
                                    )
                                )
                                if (rinshanKaihoh) { //Если игрок может объявить цумо
                                    player.tsumo(isRinshanKaihoh = true, tile = rinshanTile)
                                    if (isDealer) dealerRemaining = true
                                    break@roundLoop
                                }
                                //Проверяем, не нужно ли объявить ничью из-за четырех канов
                                val isFourKanAbort =
                                    if (board.kanCount == 3) {
                                        val playerKanCount =
                                            player.fuuroList.count { it.mentsu is Kantsu }
                                        playerKanCount != 3 //Если у игрока не 3 кана, объявляем ничью
                                    } else false
                                if (isFourKanAbort) {
                                    roundExhaustiveDraw = ExhaustiveDraw.SUUKAIKAN
                                    break@roundLoop
                                }
                                finalRinshanTile = rinshanTile
                            } else {

                                break@kanLoop
                            }
                        }

                        timeoutTile = finalRinshanTile ?: lastTile
                    }
                } else {
                    drewTile = false
                    drawTile = true
                }


                val riichiSengen = //Проверяем, может ли игрок объявить риичи
                    if (!board.isHoutei && player.isRiichiable) {
                        player.askToRiichi()
                    } else null

                //Если игрок объявил риичи или дабл риичи, добавляем его плитки в список недоступных для сброса
                if (player.riichi || player.doubleRiichi) {
                    cannotDiscardTiles += player.hands.toMahjongTileList()
                    cannotDiscardTiles.removeAll { it == timeoutTile.mahjongTile }
                }

                //Игрок решает, какую плитку сбросить
                val tileToDiscard =
                    riichiSengen ?: player.askToDiscardTile(
                        timeoutTile = timeoutTile.mahjongTile,
                        cannotDiscardTiles = cannotDiscardTiles,
                        skippable = drewTile
                    )
                val tileDiscarded = player.discardTile(tileToDiscard)
                if (tileDiscarded == null) {
                    cancel("Физическая сущность карты, которую нужно сбросить, исчезла; возможно, стол для маджонга был разрушен.")
                    return@launch
                }
                board.discards += tileDiscarded
                board.sortDiscardedTilesForDisplay(player = player, openDoorPlayer = openDoorPlayer)
                board.sortHands(player = player)
                cannotDiscardTiles.clear()


                if (board.isSuufonRenda) {
                    roundExhaustiveDraw = ExhaustiveDraw.SUUFON_RENDA
                    break
                }

                //Проверяем, могут ли другие игроки объявить рон по сброшенной плитке
                val canRonList = canRonList(tile = tileDiscarded, player)
                if (canRonList.isNotEmpty()) {
                    if (canRonList.size > 1) {
                        canRonList.ron(target = player, tile = tileDiscarded)
                        if (dealer in canRonList) dealerRemaining = true
                        break@roundLoop
                    } else {
                        val canRonPlayer = canRonList[0]
                        if (canRonPlayer.askToRon(tileDiscarded, canRonPlayer.asClaimTarget(player))) {
                            mutableListOf(canRonPlayer).ron(target = player, tile = tileDiscarded)
                            if (dealer == canRonPlayer) dealerRemaining = true
                            break@roundLoop
                        }
                    }
                }
                //Если игрок объявил риичи
                if (riichiSengen != null) {
                    player.riichi(riichiSengenTile = tileDiscarded, isFirstRound = board.isFirstRound)
                    board.sortDiscardedTilesForDisplay(player = player, openDoorPlayer = openDoorPlayer)
                    board.putRiichiStick(player = player)
                    player.playSoundAtHandsMiddle(SoundRegistry.riichi)
                    if (players.count { it.riichi || it.doubleRiichi } == 4) { //Если все игроки объявили риичи, объявляем ничью
                        roundExhaustiveDraw = ExhaustiveDraw.SUUCHA_RIICHI
                        break
                    }
                }
                //Проверяем, могут ли другие игроки объявить минкан (открытый кан) или пон по сброшенной плитке
                val canMinKanOrPonList = canMinKanOrPonList(
                    tile = tileDiscarded,
                    seatIndex = seatIndex,
                    discardedPlayer = player
                )

                var someoneKanOrPon = false
                if (canMinKanOrPonList.isNotEmpty()) { //Если есть игроки, которые могут объявить минкан или пон
                    val canMinKanOrPonPlayer = canMinKanOrPonList[0]
                    val seatIndexOfCanMinkanOrPonPlayer = seat.indexOf(canMinKanOrPonPlayer)
                    val claimTarget = when (abs(seatIndexOfCanMinkanOrPonPlayer - seatIndex)) {

                        1 -> ClaimTarget.RIGHT
                        2 -> ClaimTarget.ACROSS
                        else -> ClaimTarget.LEFT
                    }

                    someoneKanOrPon = when (canMinKanOrPonPlayer.askToMinkanOrPon(
                        tileDiscarded,
                        canMinKanOrPonPlayer.asClaimTarget(player),
                        rule
                    )) {
                        MahjongGameBehavior.PON -> {
                            canMinKanOrPonPlayer.pon(tileDiscarded, claimTarget, player) {
                                it.playSoundAtHandsMiddle(soundEvent = SoundRegistry.pon)
                            }
                            board.sortFuuro(player = canMinKanOrPonPlayer)
                            nextPlayer = canMinKanOrPonPlayer
                            drawTile = false
                            cannotDiscardTiles += tileDiscarded.mahjongTile
                            true
                        }
                        MahjongGameBehavior.MINKAN -> { //Если игрок хочет объявить минкан
                            canMinKanOrPonPlayer.minkan(tileDiscarded, claimTarget, player) {
                                it.playSoundAtHandsMiddle(soundEvent = SoundRegistry.kan)
                            }

                            board.sortFuuro(player = canMinKanOrPonPlayer)
                            val rinshanTile = board.drawRinshanTile(player = canMinKanOrPonPlayer)

                            board.sortHands(player = canMinKanOrPonPlayer, lastTile = rinshanTile)


                            val rinshanKaiHoh = canMinKanOrPonPlayer.canWin(
                                winningTile = rinshanTile.mahjongTile,
                                isWinningTileInHands = true,
                                rule = rule,
                                generalSituation = board.generalSituation,
                                personalSituation = canMinKanOrPonPlayer.getPersonalSituation(
                                    isTsumo = true,
                                    isRinshanKaihoh = true
                                )
                            )


                            if (rinshanKaiHoh) { //Если игрок может обьявить цумо
                                player.tsumo(isRinshanKaihoh = true, tile = rinshanTile)
                                if (canMinKanOrPonPlayer == dealer) dealerRemaining = true
                                break@roundLoop
                            }

                            val isFourKanAbort = //Проверяем нужно ли обьявлять ничью из за 4 канов
                                if (board.kanCount == 3) {
                                    val playerKanCount =
                                        canMinKanOrPonPlayer.fuuroList.count { it.mentsu is Kantsu }
                                    playerKanCount != 3
                                } else false
                            if (isFourKanAbort) {
                                roundExhaustiveDraw = ExhaustiveDraw.SUUKAIKAN
                                break@roundLoop
                            }
                            nextPlayer = canMinKanOrPonPlayer
                            drawTile = false
                            true
                        }

                        else -> false
                    }
                }
                //Получаем список игроков, которые могут обьявить пон
                val canPonList = canPonList(tile = tileDiscarded, discardedPlayer = player)
                    .toMutableList()
                    .also { it -= canMinKanOrPonList.toSet() }

                //Получаем список игроков, которые могут обьявить чи
                val canChiiList = canChiiList(tile = tileDiscarded, seatIndex = seatIndex, discardedPlayer = player)
                    .toMutableList()


                var someonePonOrChii = false
                if (!someoneKanOrPon && canPonList.isNotEmpty()) { //Если никто не обьявил кан или пон, и есть игроки которые могут обьявить пон
                    var ponOrChiiResult = false

                    repeat(4) {
                        val seatIndexOfPonOrChiiPlayer = (seatIndex + it) % 4
                        val ponOrChiiPlayer = seat[seatIndexOfPonOrChiiPlayer]
                        if (ponOrChiiPlayer in canPonList) {
                            if (ponOrChiiPlayer in canChiiList) {
                                val tilePairToPonOrChii =
                                    ponOrChiiPlayer.askToPonOrChii(
                                        tileDiscarded,
                                        ponOrChiiPlayer.asClaimTarget(player)
                                    )
                                if (tilePairToPonOrChii != null) { //Если игрок решил обьявить чи/пон
                                    if (tilePairToPonOrChii.first == tilePairToPonOrChii.second) {
                                        ponOrChiiPlayer.pon( //Если игрок решил обьявить пон
                                            tileDiscarded,
                                            ClaimTarget.LEFT,
                                            player
                                        ){ here ->
                                            here.playSoundAtHandsMiddle(soundEvent = SoundRegistry.pon)
                                        }
                                        cannotDiscardTiles += tileDiscarded.mahjongTile
                                    } else { //Если игрок решил обьявить чи
                                        ponOrChiiPlayer.chii(
                                            tileDiscarded,
                                            tilePairToPonOrChii,
                                            player
                                        ){ here ->
                                            here.playSoundAtHandsMiddle(soundEvent = SoundRegistry.chii)
                                        } //Проверяем не блокирует ли чи другие тайлы, которые могут принести больше очков
                                        val tileDiscardedCode = tileDiscarded.mahjong4jTile.code
                                        val tileDiscardedNumber = tileDiscarded.mahjong4jTile.number
                                        val tileCodeList = mutableListOf(
                                            tileDiscardedCode,
                                            tilePairToPonOrChii.first.mahjong4jTile.code,
                                            tilePairToPonOrChii.second.mahjong4jTile.code
                                        ).also { list -> list.sort() }
                                        val indexOfTileDiscarded = tileCodeList.indexOf(tileDiscardedCode)
                                        if (indexOfTileDiscarded == 0 && tileDiscardedNumber + 3 < 9)
                                            cannotDiscardTiles += tileDiscarded.mahjongTile.nextTile.nextTile.nextTile
                                        if (indexOfTileDiscarded == 2 && tileDiscardedNumber - 3 > 1)
                                            cannotDiscardTiles += tileDiscarded.mahjongTile.previousTile.previousTile.previousTile
                                    }
                                    board.sortFuuro(player = ponOrChiiPlayer)
                                    nextPlayer = ponOrChiiPlayer
                                    drawTile = false
                                    ponOrChiiResult = true
                                    return@repeat
                                } else {
                                    canChiiList -= ponOrChiiPlayer
                                }
                            } else {
                                val claimTarget = when (seatIndex) {
                                    (seatIndexOfPonOrChiiPlayer + 1) % 4 -> ClaimTarget.RIGHT
                                    (seatIndexOfPonOrChiiPlayer + 2) % 4 -> ClaimTarget.ACROSS
                                    (seatIndexOfPonOrChiiPlayer + 3) % 4 -> ClaimTarget.LEFT
                                    else -> ClaimTarget.SELF
                                }
                                if (ponOrChiiPlayer.askToPon(tileDiscarded, claimTarget)) {
                                    ponOrChiiPlayer.pon(tileDiscarded, claimTarget, player){ here ->
                                        here.playSoundAtHandsMiddle(soundEvent = SoundRegistry.pon)
                                    }
                                    board.sortFuuro(player = ponOrChiiPlayer)
                                    nextPlayer = ponOrChiiPlayer
                                    drawTile = false
                                    cannotDiscardTiles += tileDiscarded.mahjongTile
                                    ponOrChiiResult = true
                                    return@repeat
                                }
                            }
                        }
                    }
                    someonePonOrChii = ponOrChiiResult
                }

                var someoneChii = false
                if (!someoneKanOrPon && !someonePonOrChii && canChiiList.isNotEmpty()) { // Если никто не объявил кан, пон И есть игроки, которые могут обьявить чи
                    var chiiResult = false
                    val canChiiPlayer = canChiiList[0]
                    val askToChiiResult =
                        canChiiPlayer.askToChii(tileDiscarded, canChiiPlayer.asClaimTarget(player))
                    if (askToChiiResult != null) {
                        val tileDiscardedCode = tileDiscarded.mahjong4jTile.code
                        val tileDiscardedNumber = tileDiscarded.mahjong4jTile.number
                        val tileCodeList = mutableListOf(
                            tileDiscardedCode,
                            askToChiiResult.first.mahjong4jTile.code,
                            askToChiiResult.second.mahjong4jTile.code
                        ).also { it.sort() }
                        val indexOfTileDiscarded = tileCodeList.indexOf(tileDiscardedCode)
                        canChiiPlayer.chii(tileDiscarded, askToChiiResult, player)
                        {
                            it.playSoundAtHandsMiddle(soundEvent = SoundRegistry.chii)
                        }
                        board.sortFuuro(player = canChiiPlayer)
                        nextPlayer = canChiiPlayer
                        drawTile = false
                        cannotDiscardTiles += tileDiscarded.mahjongTile
                        if (indexOfTileDiscarded == 0 && tileDiscardedNumber + 3 < 9)
                            cannotDiscardTiles += tileDiscarded.mahjongTile.nextTile.nextTile.nextTile
                        if (indexOfTileDiscarded == 2 && tileDiscardedNumber - 3 > 1)
                            cannotDiscardTiles += tileDiscarded.mahjongTile.previousTile.previousTile.previousTile
                        chiiResult = true
                    }
                    someoneChii = chiiResult
                }
                // Если никто из игроков не смог (или не захотел) объявить кан, пон или чи:
                if (canMinKanOrPonList.isEmpty() && canPonList.isEmpty() && canChiiList.isEmpty()) {
                    delayOnServer(MIN_WAITING_TIME)
                }
                // Если никто не перехватил ход (не объявил кан, пон или чи):
                if (!someoneKanOrPon && !someonePonOrChii && !someoneChii) {
                    nextPlayer = seat[(seatIndex + 1) % 4]
                }


                if (board.wall.size == 0) { // Если в стене не осталось плиток
                    roundExhaustiveDraw = ExhaustiveDraw.NORMAL // Объявляем обычную ничью (исчерпание стены)
                    break@roundLoop
                }
            }
            // Обработка результатов раунда (после выхода из цикла)
            if (roundExhaustiveDraw != null) {
                dealerRemaining = if (roundExhaustiveDraw == ExhaustiveDraw.NORMAL) {
                    val nagashiPlayers = canNagashiManganList()
                    if (nagashiPlayers.isNotEmpty()) nagashiPlayers.nagashiMangan()
                    dealer.isTenpai
                } else {
                    true
                }
                clearNextRoundRiichiSticks = false
                roundDraw(roundExhaustiveDraw)
            }

            delayOnServer(3000L)
            // Проверяем, нужно ли начинать следующий раунд
            if (!round.isAllLast(rule)) {
                if (dealerRemaining) {  // Если дилер остался
                    board.addHonbaStick(player = dealer) // Добавляем палочку хонба (счетчик повторов раунда)
                    round.honba++
                } else { // Если дилер сменился
                    board.removeHonbaSticks(player = dealer) // Убираем палочки хонба у бывшего дилера
                    round.nextRound() // Переходим к следующему раунду (меняется ветер/номер раунда)
                }
                delayOnServer(100)
                startRound(clearRiichiSticks = clearNextRoundRiichiSticks)
            } else {
                if (players.none { it.points >= rule.minPointsToWin }) {
                    if (dealerRemaining) {
                        board.addHonbaStick(player = dealer)
                        round.honba++
                        startRound(clearRiichiSticks = clearNextRoundRiichiSticks)
                    } else {
                        board.removeHonbaSticks(player = dealer)
                        val finalRound = rule.length.finalRound
                        if (round.wind == finalRound.first && round.round == finalRound.second) {
                            showGameResult()
                            end()
                        } else {
                            round.nextRound()
                            startRound(clearRiichiSticks = clearNextRoundRiichiSticks)
                        }
                    }
                } else {
                    showGameResult()
                    end()
                }
            }
        }
    }

    // Функция для обработки ничьей
    private suspend fun roundDraw(draw: ExhaustiveDraw) {
        val scoreList = buildList {
            if (draw != ExhaustiveDraw.NORMAL) { // Если ничья не из-за исчерпания стены
                players.forEach {
                    val riichiStickPoints = if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0 // Если игрок в риичи, он теряет 1000 очков
                    this += ScoreItem(
                        mahjongPlayer = it,
                        scoreOrigin = it.points,
                        scoreChange = -riichiStickPoints
                    )
                    it.points -= riichiStickPoints
                    if (draw == ExhaustiveDraw.KYUUSHU_KYUUHAI && it.isKyuushuKyuuhai()) { // Если ничья из-за 9 терминалов и игрок ее обьявил. Со старта 9 разных 1 или 9 или ветров.
                        it.openHands()
                    } else it.closeHands()
                }
            } else { // Если ничья из-за исчерпания стены
                val tenpaiCount = players.count { it.isTenpai } // Считаем количество игроков в темпае (готовы к победе)
                if (tenpaiCount == 0) {
                    players.forEach {
                        this += ScoreItem(
                            mahjongPlayer = it,
                            scoreOrigin = it.points,
                            scoreChange = 0
                        )
                        it.closeHands()
                    }
                } else { // Если есть игроки в темпае. То есть тебе остался 1 тайтл до победы, но тайлы в колоде кончаються
                    val notenCount = 4 - tenpaiCount   //Количество игроков не в темпае
                    val notenBappu = 3000 / notenCount //Штраф за "не темпай"
                    val bappuGet = 3000 / tenpaiCount //Награда за "темпай"
                    players.forEach {
                        if (it.isTenpai) {
                            val riichiStickPoints =
                                if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0 //Если игрок обьявил риичи, теряет 1000 очков
                            this += ScoreItem(
                                mahjongPlayer = it,
                                scoreOrigin = it.points,
                                scoreChange = bappuGet - riichiStickPoints
                            )
                            it.points += bappuGet
                            it.points -= riichiStickPoints
                            it.openHands()
                        } else {
                            this += ScoreItem(
                                mahjongPlayer = it,
                                scoreOrigin = it.points,
                                scoreChange = -notenBappu
                            )
                            it.points -= notenBappu
                            it.closeHands()
                        }
                    }
                }
            }
        }
        delayOnServer(3000)
        realPlayers.forEach { //Отправляем всем реальным игрокам информацию о результатах
            sendPayloadToPlayer(
                player = it.entity,
                payload = MahjongGamePayload(
                    behavior = MahjongGameBehavior.SCORE_SETTLEMENT,
                    extraData = Json.encodeToString(
                        ScoreSettlement(
                            titleTranslateKey = draw.translateKey,
                            scoreList = scoreList
                        )
                    )
                )
            )
        }
        delayOnServer(ScoreSettleHandler.defaultTime * 1000L)
    }
    // Функция для отображения результатов игры
    private fun showGameResult() {
        val scoreList = players.map {
            ScoreItem(
                mahjongPlayer = it,
                scoreOrigin = it.points,
                scoreChange = 0
            )
        }

        realPlayers.forEach {
            sendPayloadToPlayer(
                player = it.entity,
                payload = MahjongGamePayload(
                    behavior = MahjongGameBehavior.SCORE_SETTLEMENT,
                    extraData = Json.encodeToString(
                        ScoreSettlement(
                            titleTranslateKey = "$MOD_ID.game.game_over",
                            scoreList = scoreList
                        )
                    )
                )
            )


            val mahjongText = (Text.translatable("$MOD_ID.game.riichi_mahjong") + ":")
                .formatted(Formatting.YELLOW)
                .formatted(Formatting.BOLD)
            val ruleTooltip = Text.literal("").also {
                rule.toTexts().forEachIndexed { index, text ->
                    if (index > 0) it.append("\n")
                    it.append(text)
                }
            }
            val ruleHoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, ruleTooltip)
            val ruleStyle = Style.EMPTY.withColor(Formatting.GREEN).withHoverEvent(ruleHoverEvent)
            val ruleText = (Text.literal("§a[") + Text.translatable("$MOD_ID.game.rules") + "§a]").fillStyle(ruleStyle)
            val scoreText = Text.translatable("$MOD_ID.game.score")
            it.sendMessage(
                Text.literal("§2------------------------------------------")
                        + "\n" + mahjongText
                        + "\n§7 - " + ruleText
                        + "\n§a"
                        + "\n§6" + scoreText + ":"
            )
            //Сортируем игроков по убыванию
            players.sortedByDescending { player -> player.points }.forEachIndexed { index, mjPlayer ->
                val displayNameText = if (mjPlayer.isRealPlayer) {
                    Text.literal(mjPlayer.displayName)
                } else {
                    Text.translatable("entity.$MOD_ID.mahjong_bot")
                }.formatted(Formatting.AQUA)
                it.sendMessage(Text.literal("§7 - §e${index + 1}. ") + displayNameText + "  §c${mjPlayer.points}")
            }
            it.sendMessage(Text.of("§2------------------------------------------"))
        }
    }


    // Метод, проверяющий, может ли игрок объявить ничью по девяти разным терминалам/единицам
    private fun MahjongPlayerBase.isKyuushuKyuuhai(): Boolean = board.isFirstRound && numbersOfYaochuuhaiTypes >= 9

    // Метод, определяющий, с какой стороны игрок "украл" плитку (для чи, пона, кана)
    private fun MahjongPlayerBase.asClaimTarget(target: MahjongPlayerBase): ClaimTarget {
        val seatIndex = seat.indexOf(this)
        return when (target) {
            seat[(seatIndex + 1) % 4] -> ClaimTarget.RIGHT // Справа (следующий игрок)
            seat[(seatIndex + 2) % 4] -> ClaimTarget.ACROSS  // Напротив
            seat[(seatIndex + 3) % 4] -> ClaimTarget.LEFT // Слева (предыдущий игрок)
            else -> ClaimTarget.SELF // Сам (используется для закрытых канов)
        }
    }

    // Метод, возвращающий список игроков, которые могут объявить пон по сброшенной плитке
    private fun canPonList(
        tile: MahjongTileEntity,
        discardedPlayer: MahjongPlayerBase,
    ): List<MahjongPlayerBase> =
        if (!board.isHoutei) players.filter { it != discardedPlayer && it.canPon(tile) }
        else emptyList()

    // Метод, возвращающий список игроков, которые могут объявить открытый кан (минкан) или пон
    private fun canMinKanOrPonList(
        tile: MahjongTileEntity,
        discardedPlayer: MahjongPlayerBase,
        seatIndex: Int = seat.indexOf(discardedPlayer),
    ): List<MahjongPlayerBase> =
        if (board.kanCount < 4 && !board.isHoutei)
            players.filter { it != discardedPlayer && it.canMinkan(tile) && it != seat[(seatIndex + 1) % 4] } // Проверяем, может ли игрок объявить минкан (и не является следующим игроком)
        else emptyList()

    // Метод, возвращающий список игроков, которые могут объявить чи по сброшенной плитке
    private fun canChiiList(
        tile: MahjongTileEntity,
        discardedPlayer: MahjongPlayerBase,
        seatIndex: Int = seat.indexOf(discardedPlayer),
    ): List<MahjongPlayerBase> =
        if (!board.isHoutei) players.filter { it != discardedPlayer && it.canChii(tile) && it == seat[(seatIndex + 1) % 4] }
        else emptyList()

    // Метод, возвращающий список игроков, которые могут объявить рон по сброшенной плитке
    private fun canRonList(
        tile: MahjongTileEntity,
        discardedPlayer: MahjongPlayerBase,
        isChanKan: Boolean = false, // Является ли это объявлением рона по плитке, использованной для кана
    ): List<MahjongPlayerBase> = players.filter {
        if (it == discardedPlayer) return@filter false
        if (it.discardedTiles.isEmpty() && it.isMenzenchin) return@filter false
        val canWin = it.canWin(
            winningTile = tile.mahjongTile,
            isWinningTileInHands = false,
            rule = rule,
            generalSituation = board.generalSituation,
            personalSituation = it.getPersonalSituation(isChankan = isChanKan)
        )
        val isFuriten = it.isFuriten(tile = tile, discards = board.discards) // Проверяем, находится ли игрок в фуритэне
        (canWin && !isFuriten)
    }

    // Метод, возвращающий список игроков, которые могут объявить рон по плитке, использованной для открытого кана (какан)
    private fun canChanKakanList(
        kanTile: MahjongTileEntity,
        discardedPlayer: MahjongPlayerBase,
    ): List<MahjongPlayerBase> =
        canRonList(tile = kanTile, isChanKan = true, discardedPlayer = discardedPlayer)

    // Метод, возвращающий список игроков, которые могут объявить рон по плитке, использованной для *закрытого* кана (анкан).
    // (В этом случае рон возможен, *только* если игрок собирает кокуси мусо - 13 сирот)
    private fun canChanAnkanList(
        kanTile: MahjongTileEntity,
        discardedPlayer: MahjongPlayerBase,
    ): List<MahjongPlayerBase> =
        canChanKakanList(
            kanTile = kanTile,
            discardedPlayer = discardedPlayer
        ).filter { it.isKokushimuso(kanTile.mahjong4jTile) }

    // Метод, возвращающий список игроков, у которых нагаси манган (все сброшенные плитки - терминалы/единицы)
    private fun canNagashiManganList(): List<MahjongPlayerBase> =
        if (board.wall.isEmpty()) {
            players.filter {
                val discardedTilesNoCall = it.discardedTiles == it.discardedTilesForDisplay // Проверяем, что игрок не делал вызовов (чи, пон, кан)
                val discardedTilesAllYaochu = it.discardedTiles.all { tile -> tile.mahjong4jTile.isYaochu } // Проверяем, что все сброшенные плитки - терминалы/единицы
                discardedTilesNoCall && discardedTilesAllYaochu
            }
        } else listOf()

    // Метод, обрабатывающий объявление рона несколькими игроками
    private suspend fun List<MahjongPlayerBase>.ron(
        target: MahjongPlayerBase,
        isChankan: Boolean = false,
        tile: MahjongTileEntity,
    ) {
        val yakuSettlementList = mutableListOf<YakuSettlement>()
        val scoreList = mutableListOf<ScoreItem>()
        val honbaScore = round.honba * 300
        var totalScore = honbaScore
        val allRiichiStickQuantity = players.sumOf { it.riichiStickAmount }
        val extraScore = allRiichiStickQuantity * ScoringStick.P1000.point + honbaScore
        val seatOrderFromTarget = List(4) {
            val targetIndex = seat.indexOf(target)
            seat[(targetIndex + it) % 4]
        }
        val atamahanePlayer = seatOrderFromTarget.find { it in this } //Определяем первого игрока, который мог обьявить рон
        this.forEach {
            it.playSoundAtHandsMiddle(soundEvent = SoundRegistry.ron) // Перебираем всех игроков, объявивших рон
            it.openHands()
            val isDealer = it == seatOrderFromDealer[0]
            val isAtamahanePlayer = it == atamahanePlayer
            val settlement = it.calcYakuSettlementForWin(
                winningTile = tile.mahjongTile,
                isWinningTileInHands = false,
                rule = rule,
                generalSituation = board.generalSituation,
                personalSituation = it.getPersonalSituation(isChankan = isChankan),
                doraIndicators = board.doraIndicators.map { entity -> entity.mahjongTile },
                uraDoraIndicators = board.uraDoraIndicators.map { entity -> entity.mahjongTile }
            )
            yakuSettlementList += settlement
            val riichiStickPoints = //Если игрок обьявил риичи, то нужно компенсировать 1000 очков
                if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0
            val basicScore = (settlement.score * if (isDealer) 1.5 else 1.0).toInt()
            val score = basicScore - riichiStickPoints + if (isAtamahanePlayer) extraScore else 0
            scoreList += ScoreItem(
                mahjongPlayer = it,
                scoreOrigin = it.points,
                scoreChange = score
            )
            it.points += score
            totalScore += basicScore
        }
        target.also {
            val riichiStickPoints =
                if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0
            scoreList += ScoreItem(
                mahjongPlayer = it,
                scoreOrigin = it.points,
                scoreChange = -(totalScore + riichiStickPoints)
            )
            it.points -= (totalScore + riichiStickPoints)
        }
        val remainingPlayers = players.toMutableList().also { it -= this.toSet(); it -= target }
        remainingPlayers.forEach {
            val riichiStickPoints = //Если игрок обьявил риичи, нужно забрать 1000 очков
                if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0
            scoreList += ScoreItem(mahjongPlayer = it, scoreOrigin = it.points, scoreChange = -riichiStickPoints)
            it.points -= riichiStickPoints
        }
        val scoreSettlement = ScoreSettlement(
            titleTranslateKey = MahjongGameBehavior.RON.translateKey,
            scoreList = scoreList
        )
        realPlayers.sendSettlePacketAndDelay(yakuSettlementList, scoreSettlement)
    }
    //Обрабатывает ситуацию, когда игрок выигрывает, взяв плитку из стены (объявляет "цумо").
    private suspend fun MahjongPlayerBase.tsumo(
        isRinshanKaihoh: Boolean = false,
        tile: MahjongTileEntity,
    ) {
        playSoundAtHandsMiddle(soundEvent = SoundRegistry.tsumo)
        val yakuSettlementList = mutableListOf<YakuSettlement>()
        val scoreList = mutableListOf<ScoreItem>()
        val allRiichiStickQuantity = players.sumOf { it.riichiStickAmount }
        val honbaScore = round.honba * 300
        val playerRiichiStickPoints =
            if (this.riichi || this.doubleRiichi) ScoringStick.P1000.point else 0
        val extraScore = allRiichiStickQuantity * ScoringStick.P1000.point + honbaScore
        val tsumoPlayerIsDealer = this == seatOrderFromDealer[0]
        val settlement = this.calcYakuSettlementForWin(
            winningTile = tile.mahjongTile,
            isWinningTileInHands = true,
            rule = rule,
            generalSituation = board.generalSituation,
            personalSituation = this.getPersonalSituation(isTsumo = true, isRinshanKaihoh = isRinshanKaihoh),
            doraIndicators = board.doraIndicators.map { entity -> entity.mahjongTile },
            uraDoraIndicators = board.uraDoraIndicators.map { entity -> entity.mahjongTile }
        )
        yakuSettlementList += settlement
        val basicScore = settlement.score
        val score = basicScore - playerRiichiStickPoints + extraScore
        scoreList += ScoreItem(
            mahjongPlayer = this,
            scoreOrigin = this.points,
            scoreChange = score
        )
        this.points += score
        players.filter { it != this }.forEach {
            val riichiStickPoints =
                if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0
            if (tsumoPlayerIsDealer) {
                val averageScore = (basicScore + honbaScore) / 3
                val itsScore = averageScore + riichiStickPoints
                scoreList += ScoreItem(
                    mahjongPlayer = it,
                    scoreOrigin = it.points,
                    scoreChange = -itsScore
                )
                it.points -= itsScore
            } else {
                val isDealer = it == seatOrderFromDealer[0]
                if (isDealer) {
                    val halfScore = basicScore / 2
                    val itsScore = halfScore + honbaScore / 3 + riichiStickPoints
                    scoreList += ScoreItem(
                        mahjongPlayer = it,
                        scoreOrigin = it.points,
                        scoreChange = -itsScore
                    )
                    it.points -= itsScore
                } else {
                    val quartScore = basicScore / 4
                    val itsScore = quartScore + honbaScore / 3 + riichiStickPoints
                    scoreList += ScoreItem(
                        mahjongPlayer = it,
                        scoreOrigin = it.points,
                        scoreChange = -itsScore
                    )
                    it.points -= itsScore
                }
            }
        }
        val scoreSettlement = ScoreSettlement(
            titleTranslateKey = MahjongGameBehavior.TSUMO.translateKey,
            scoreList = scoreList
        )
        realPlayers.sendSettlePacketAndDelay(
            yakuSettlementList = yakuSettlementList,
            scoreSettlement = scoreSettlement
        )
    }
    //Обрабатывает ситуацию "нагаси манган" (nagashi mangan). Это редкая ситуация, когда у игрока все сброшенные плитки - терминалы/единицы, и никто не объявил рон по его сбросам
    private suspend fun List<MahjongPlayerBase>.nagashiMangan() {
        val yakuSettlementList = mutableListOf<YakuSettlement>()
        val scoreList = mutableListOf<ScoreItem>()
        val atamahanePlayer = seatOrderFromDealer.find { it in this }
        val allRiichiStickQuantity = players.sumOf { it.riichiStickAmount }
        val honbaScore = round.honba * 300
        val extraScore = allRiichiStickQuantity * ScoringStick.P1000.point + honbaScore
        val originalScoreList = hashMapOf<String, Int>().apply {
            players.forEach { this[it.uuid] = it.points }
        }
        val dealer = seatOrderFromDealer[0]
        this.forEach {
            val yakuSettlement = YakuSettlement.nagashiMangan(
                mahjongPlayer = it,
                doraIndicators = board.doraIndicators.map { entity -> entity.mahjongTile },
                uraDoraIndicators = board.uraDoraIndicators.map { entity -> entity.mahjongTile },
                isDealer = it == dealer
            )
            yakuSettlementList += yakuSettlement
            val basicScore = yakuSettlement.score
            val score = basicScore + if (it == atamahanePlayer) extraScore else 0
            it.points += score
            players.filter { player -> player != it }.forEach { player ->
                player.points -= (basicScore / 3)
                if (it == atamahanePlayer) player.points -= (honbaScore / 3)
            }
        }
        players.forEach {
            val originalScore = originalScoreList[it.uuid]!!
            val scoreChange = it.points - originalScore
            scoreList += ScoreItem(
                mahjongPlayer = it,
                scoreOrigin = originalScore,
                scoreChange = -scoreChange
            )
        }
        val scoreSettlement = ScoreSettlement(
            titleTranslateKey = MahjongGameBehavior.TSUMO.translateKey,
            scoreList = scoreList
        )
        realPlayers.sendSettlePacketAndDelay(
            yakuSettlementList = yakuSettlementList,
            scoreSettlement = scoreSettlement
        )
    }
    // Отправляет игрокам информацию о результатах раунда
    private suspend fun List<MahjongPlayer>.sendSettlePacketAndDelay(
        yakuSettlementList: List<YakuSettlement>? = null,
        scoreSettlement: ScoreSettlement? = null,
    ) {
        if (yakuSettlementList != null) {
            this.forEach {
                sendPayloadToPlayer(
                    player = it.entity,
                    payload = MahjongGamePayload(
                        behavior = MahjongGameBehavior.YAKU_SETTLEMENT,
                        extraData = Json.encodeToString(yakuSettlementList)
                    )
                )
            }
            val yakuSettlementTime = YakuSettleHandler.defaultTime * 1000L * yakuSettlementList.size
            delayOnServer(yakuSettlementTime)
        }
        if (scoreSettlement != null) {
            this.forEach {
                sendPayloadToPlayer(
                    player = it.entity,
                    payload = MahjongGamePayload(
                        behavior = MahjongGameBehavior.SCORE_SETTLEMENT,
                        extraData = Json.encodeToString(scoreSettlement)
                    )
                )
            }
            val scoreSettlementTime = ScoreSettleHandler.defaultTime * 1000L
            delayOnServer(scoreSettlementTime)
        }
    }

    private fun MahjongPlayer.gameOver() {
        if (!isHost(this.entity)) ready = false
        cancelWaitingBehavior = true

        sendPayloadToPlayer(
            player = this.entity,
            payload = MahjongGamePayload(behavior = MahjongGameBehavior.GAME_OVER)
        )
    }
    //Имитирует бросок двух игральных костей
    private suspend fun rollDice(): List<DiceEntity> {
        val dices = List(2) {
            DiceEntity(
                world = world,
                pos = Vec3d(pos.x + 0.5, pos.y.toDouble() + 1.5, pos.z + 0.5),
                yaw = (-180 until 180).random().toFloat()
            ).apply {
                isSpawnedByGame = true
                gameBlockPos = this@MahjongGame.pos
                val sin = MathHelper.sin(yaw * 0.017453292F - 11)
                val cos = MathHelper.cos(yaw * 0.017453292F - 11)
                val range = if (it == 0) (15..90) else (-90..-15)
                val randomMoveXPercentage = ((range).random()) / 100.0
                val randomMoveZPercentage = ((range).random()) / 100.0
                setVelocity(0.1 * cos * randomMoveXPercentage, 0.03, 0.1 * sin * randomMoveZPercentage)
                ServerScheduler.scheduleDelayAction { world.spawnEntity(this) }
            }
        }
        val dicePoints = dices.associateWith { DicePoint.random() }
        while (dices.any { it.rolling }) delayOnServer(50)
        dices.forEach { diceEntity -> diceEntity.point = dicePoints[diceEntity]!! }
        val totalPoints = dicePoints.values.sumOf { it.value }
        delayOnServer(500)
        val pointsSumText =
            Text.translatable("$MOD_ID.game.dice_points").formatted(Formatting.GOLD) + " §c$totalPoints"
        realPlayers.map { it.entity }.sendTitles(subtitle = pointsSumText)
        this@MahjongGame.dicePoints = totalPoints

        // Отложенное удаление кубиков через ServerScheduler
        ServerScheduler.scheduleDelayAction {
            dices.forEach { it.remove(Entity.RemovalReason.DISCARDED) }
        }

        return dices
    }

    //Начинает игру. Расставляет игроков по местам, раздает начальные очки, запускает первый раунд
    override fun start(sync: Boolean) {
        status = GameStatus.PLAYING
        seat = players.toMutableList().apply {
            shuffle()
            forEachIndexed { index, mjPlayer ->
                with(mjPlayer) {
                    val yaw = 90 - 90f * index
                    val stoolX = pos.x + if (index == 0) 2 else if (index == 2) -2 else 0
                    val stoolZ = pos.z + if (index == 1) -2 else if (index == 3) 2 else 0
                    val stoolBlockPos = BlockPos(stoolX, pos.y, stoolZ)
                    val blockState = world.getBlockState(stoolBlockPos)
                    val block = blockState.block
                    if (block is MahjongStool && SeatEntity.canSpawnAt(
                            world,
                            stoolBlockPos,
                            checkEntity = false
                        )
                    ) {
                        ServerScheduler.scheduleDelayAction {
                            val x = pos.x + 0.5 + if (index == 0) 3 else if (index == 2) -3 else 0
                            val y = pos.y.toDouble()
                            val z = pos.z + 0.5 + if (index == 1) -3 else if (index == 3) 3 else 0
                            this.teleport(world, x, y, z, yaw, 0f)
                            //посадка на стуле
                            val offsetY = if (this is MahjongPlayer) 0.4 else 0.1

                            SeatEntity.forceSpawnAt(
                                entity = this.entity,
                                world = world,
                                pos = stoolBlockPos,
                                sitOffsetY = offsetY
                            )
                            if (this is MahjongBot) this.entity.isInvisible = false
                            (mjPlayer.entity as? BotEntity)?.faceTable(tableCenterPos)
                        }
                    } else {
                        fun BlockPos.collisionExists() =
                            this.let { !world.getBlockState(it).getCollisionShape(world, it).isEmpty }

                        val blockBelowCollisionExists = stoolBlockPos.offset(Direction.DOWN).collisionExists()
                        val blockCollisionDoesNotExist = !stoolBlockPos.collisionExists()
                        val blockAboveCollisionDoesNotExist = !stoolBlockPos.offset(Direction.UP).collisionExists()
                        if (blockBelowCollisionExists && blockCollisionDoesNotExist && blockAboveCollisionDoesNotExist) {
                            this.teleport(world, stoolX + 0.5, pos.y.toDouble(), stoolZ + 0.5, yaw, 0f)
                        } else {
                            this.teleport(
                                world,
                                pos.x + 0.5,
                                pos.y + 1.2,
                                pos.z + 0.5,
                                yaw + 180,
                                0f
                            )
                        }
                        if (this is MahjongBot) this.entity.isInvisible = false
                        (mjPlayer.entity as? BotEntity)?.faceTable(tableCenterPos)
                    }
                }
            }
        }
        round = rule.length.getStartingRound()
        players.forEach {
            it.points = rule.startingPoints
            it.basicThinkingTime = rule.thinkingTime.base
            it.extraThinkingTime = rule.thinkingTime.extra
        }
        realPlayers.forEach {
            it.cancelWaitingBehavior = false
            sendPayloadToPlayer(
                player = it.entity,
                payload = MahjongGamePayload(behavior = MahjongGameBehavior.GAME_START)
            )
        }
        if (sync) syncMahjongTable()
        val handler = CoroutineExceptionHandler { _, _ -> }
        jobWaitForStart = CoroutineScope(Dispatchers.IO).launch(handler) {
            delayOnServer(500)
            startRound()
        }
    }

    override fun end(sync: Boolean) {
        status = GameStatus.WAITING
        jobWaitForStart?.cancel()
        jobRound?.cancel()
        seat.clear()
        clearStuffs()
        round = MahjongRound()
        realPlayers.forEach { it.gameOver() }
        botPlayers.forEach {
            it.entity.isInvisible = true
            it.entity.teleport(tableCenterPos.x, tableCenterPos.y, tableCenterPos.z, false)
        }
        if (sync) syncMahjongTable()
    }

    //Вызывается, когда разрушается блок стола для маджонга
    override fun onBreak() {
        if (isPlaying) {
            showGameResult()
            end(sync = false)
        }
        val message = PREFIX + Text.translatable("$MOD_ID.game.message.game_block_is_destroyed")
        realPlayers.forEach {
            it.sendMessage(message)
        }
        botPlayers.forEach {
            it.entity.remove(Entity.RemovalReason.DISCARDED)
        }
        players.clear()
        GameManager.games -= this
    }

    // Вызывается, когда игрок отключается от сервера
    override fun onPlayerDisconnect(player: ServerPlayerEntity) {
        if (isPlaying) {
            showGameResult()
            end(sync = false)
        }
        leave(player)
        val message = PREFIX + Text.translatable("$MOD_ID.game.message.player_left_game", player.displayName)
        realPlayers.forEach {
            it.sendMessage(message)
        }
        syncMahjongTable()
    }

    //Вызывается, когда игрок переходит в другой мир (измерение)
    override fun onPlayerChangedWorld(player: ServerPlayerEntity) {
        if (isPlaying) {
            showGameResult()
            end(sync = false)
        }
        leave(player)
        val message =
            PREFIX + Text.translatable("$MOD_ID.game.message.player_is_not_in_this_world", player.displayName)
        realPlayers.forEach {
            it.sendMessage(message)
        }
        syncMahjongTable()
    }
    // Вызывается при остановке сервера
    override fun onServerStopping(server: MinecraftServer) {
        if (isPlaying) end(sync = false)
        realPlayers.forEach { leave(it.entity) }
    }

    private fun syncMahjongTable(invokeOnNextTick: Boolean = true) {
        MahjongTablePayloadListener.syncBlockEntityWithGame(invokeOnNextTick = invokeOnNextTick, game = this)
    }

    override fun isHost(player: ServerPlayerEntity): Boolean =
        players.firstOrNull()?.let { it.entity == player } ?: false

    //Расчет очков
    private fun MahjongPlayerBase.getPersonalSituation(
        isTsumo: Boolean = false,
        isChankan: Boolean = false,
        isRinshanKaihoh: Boolean = false,
    ): PersonalSituation {
        val selfWindNumber = seatOrderFromDealer.indexOf(this)
        val jikaze = Wind.entries[selfWindNumber].tile
        val isIppatsu: Boolean = isIppatsu(players, board.discards)
        return PersonalSituation(
            isTsumo,
            isIppatsu,
            this.riichi,
            this.doubleRiichi,
            isChankan,
            isRinshanKaihoh,
            jikaze
        )
    }

    fun MahjongPlayerBase.getMachiAndHan(tile: MahjongTile): Map<MahjongTile, Int> {
        if (board.deadWall.isEmpty()) return emptyMap()
        val handsForCalculate = this.hands.toMahjongTileList().toMutableList().apply { remove(tile) }
        return this.calculateMachiAndHan(
            hands = handsForCalculate,
            rule = rule,
            generalSituation = board.generalSituation,
            personalSituation = this.getPersonalSituation(
                isTsumo = false,
                isChankan = false,
                isRinshanKaihoh = false
            )
        )
    }

    fun MahjongPlayerBase.isFuriten(tile: MahjongTile, machi: List<MahjongTile>): Boolean =
        this.isFuriten(tile.mahjong4jTile, board.discards.map { it.mahjong4jTile }, machi.map { it.mahjong4jTile })

    companion object {
        const val MIN_WAITING_TIME = 1200L
        const val STICKS_PER_STACK = 5

        val PREFIX get() = Text.translatable("$MOD_ID.game.riichi_mahjong.prefix")
    }
}
