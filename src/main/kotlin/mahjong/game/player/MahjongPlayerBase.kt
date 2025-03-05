package mahjong.game.player

import mahjong.entity.*
import mahjong.game.GamePlayer
import mahjong.game.game_logic.*
import mahjong.logger
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import org.mahjong4j.*
import org.mahjong4j.hands.*
import org.mahjong4j.tile.Tile
import org.mahjong4j.tile.TileType
import org.mahjong4j.yaku.normals.NormalYaku
import org.mahjong4j.yaku.yakuman.Yakuman
import kotlin.math.absoluteValue

abstract class MahjongPlayerBase : GamePlayer {

    val hands: MutableList<MahjongTileEntity> = mutableListOf()


    var autoArrangeHands: Boolean = true


    val fuuroList: MutableList<Fuuro> = mutableListOf()


    var riichiSengenTile: MahjongTileEntity? = null


    val discardedTiles: MutableList<MahjongTileEntity> = mutableListOf()


    val discardedTilesForDisplay: MutableList<MahjongTileEntity> = mutableListOf()

    open var ready: Boolean = false


    var riichi: Boolean = false

    var doubleRiichi: Boolean = false


    val riichiStickAmount: Int
        get() = sticks.count { it.scoringStick == ScoringStick.P1000 }


    val sticks: MutableList<ScoringStickEntity> = mutableListOf()

    var points: Int = 0


    val isMenzenchin: Boolean
        get() = fuuroList.isEmpty() ||
                fuuroList.all { it.mentsu is Kantsu && !it.mentsu.isOpen }


    val isRiichiable: Boolean
        get() = isMenzenchin && !(riichi || doubleRiichi) && tilePairsForRiichi.isNotEmpty() && points >= 1000

    var basicThinkingTime = 0

    var extraThinkingTime = 0


    val numbersOfYaochuuhaiTypes: Int
        get() = hands.map { it.mahjong4jTile }.distinct().count { it.isYaochu }

    abstract fun teleport(
        targetWorld: ServerWorld,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        pitch: Float,
    )

    fun chii(
        mjTileEntity: MahjongTileEntity,
        tilePair: Pair<MahjongTile, MahjongTile>,
        target: MahjongPlayerBase,
        onChii: (MahjongPlayerBase) -> Unit = {},
    ) {
        onChii.invoke(this)
        val tileMj4jCodePair: Pair<Tile, Tile> = tilePair.first.mahjong4jTile to tilePair.second.mahjong4jTile
        val claimTarget = ClaimTarget.LEFT
        val tileShuntsu = mutableListOf(
            mjTileEntity,
            hands.find { it.mahjong4jTile == tileMj4jCodePair.first }!!,
            hands.find { it.mahjong4jTile == tileMj4jCodePair.second }!!
        ).also {
            it.sortBy { tile -> tile.mahjong4jTile.code }
            it.forEach { tile -> tile.inGameTilePosition = TilePosition.OTHER }
        }
        val middleTile = tileShuntsu[1].mahjong4jTile
        val shuntsu = Shuntsu(true, middleTile)
        val fuuro =
            Fuuro(mentsu = shuntsu, tileMjEntities = tileShuntsu, claimTarget = claimTarget, claimTile = mjTileEntity)
        hands -= tileShuntsu.toMutableList().also { it -= mjTileEntity }.toSet()
        target.discardedTilesForDisplay -= mjTileEntity
        fuuroList += fuuro
    }


    fun pon(
        mjTileEntity: MahjongTileEntity,
        claimTarget: ClaimTarget,
        target: MahjongPlayerBase,
        onPon: (MahjongPlayerBase) -> Unit = {},
    ) {
        onPon.invoke(this)
        val kotsu = Kotsu(true, mjTileEntity.mahjong4jTile)
        val tilesForPon = tilesForPon(mjTileEntity).onEach { it.inGameTilePosition = TilePosition.OTHER }
        val fuuro =
            Fuuro(mentsu = kotsu, tileMjEntities = tilesForPon, claimTarget = claimTarget, claimTile = mjTileEntity)
        hands -= tilesForPon.toSet()
        target.discardedTilesForDisplay -= mjTileEntity
        fuuroList += fuuro
    }


    fun minkan(
        mjTileEntity: MahjongTileEntity,
        claimTarget: ClaimTarget,
        target: MahjongPlayerBase,
        onMinkan: (MahjongPlayerBase) -> Unit = {},
    ) {
        onMinkan.invoke(this)
        val kantsu = Kantsu(true, mjTileEntity.mahjong4jTile)
        val tilesForMinkan = tilesForMinkan(mjTileEntity).onEach { it.inGameTilePosition = TilePosition.OTHER }
        val fuuro =
            Fuuro(mentsu = kantsu, tileMjEntities = tilesForMinkan, claimTarget = claimTarget, claimTile = mjTileEntity)
        hands -= tilesForMinkan.toSet()
        target.discardedTilesForDisplay -= mjTileEntity
        fuuroList += fuuro
    }


    fun ankan(mjTileEntity: MahjongTileEntity, onAnkan: (MahjongPlayerBase) -> Unit = {}) {
        val tilesForAnkan = tilesForAnkan(mjTileEntity)
        if (tilesForAnkan.size != 4) {
            logger.warn("Not enough tiles for Ankan: ${tilesForAnkan.size}, expected 4, tile: ${mjTileEntity.mahjongTile}")
            return
        }
        onAnkan.invoke(this)
        val kantsu = Kantsu(false, mjTileEntity.mahjong4jTile)
        tilesForAnkan.forEach { it.inGameTilePosition = TilePosition.OTHER }
        val fuuro = Fuuro(
            mentsu = kantsu,
            tileMjEntities = tilesForAnkan,
            claimTarget = ClaimTarget.SELF,
            claimTile = mjTileEntity
        )
        hands.removeAll(tilesForAnkan) // Удаляем явно все 4 тайла
        fuuroList += fuuro
    }

    fun kakan(mjTileEntity: MahjongTileEntity, onKakan: (MahjongPlayerBase) -> Unit = {}) {
        val minKotsu = fuuroList.find { it.mentsu is Kotsu && mjTileEntity.mahjongTile in it.tileMjEntities.toMahjongTileList() }
        if (minKotsu == null) {
            logger.warn("No Pon found for Kakan with tile: ${mjTileEntity.mahjongTile}")
            return
        }
        onKakan.invoke(this)
        fuuroList -= minKotsu
        val kakantsu = Kakantsu(mjTileEntity.mahjong4jTile)
        val tiles = minKotsu.tileMjEntities.toMutableList().also { it += mjTileEntity }
        tiles.forEach { it.inGameTilePosition = TilePosition.OTHER }
        val fuuro = Fuuro(
            mentsu = kakantsu,
            tileMjEntities = tiles,
            claimTarget = minKotsu.claimTarget,
            claimTile = minKotsu.claimTile
        )
        hands.remove(mjTileEntity) // Удаляем только один тайл
        fuuroList += fuuro
    }


    fun canPon(mjTileEntity: MahjongTileEntity): Boolean =
        !(riichi || doubleRiichi) && sameTilesInHands(mjTileEntity).size >= 2


    fun canMinkan(mjTileEntity: MahjongTileEntity): Boolean =
        !(riichi || doubleRiichi) && sameTilesInHands(mjTileEntity).size == 3

    val canKakan: Boolean
        get() = tilesCanKakan.size > 0


    val canAnkan: Boolean
        get() = tilesCanAnkan.isNotEmpty()

    fun canChii(mjTileEntity: MahjongTileEntity): Boolean =
        !(riichi || doubleRiichi) && tilePairsForChii(mjTileEntity).isNotEmpty()

    private fun tilesForPon(mjTileEntity: MahjongTileEntity): List<MahjongTileEntity> =
        sameTilesInHands(mjTileEntity).apply {
            if (size > 2) {
                this -= first { !it.mahjongTile.isRed }
                sortBy { it.mahjongTile.isRed }
            }
            this += mjTileEntity
        }


    private fun tilesForMinkan(mjTileEntity: MahjongTileEntity): List<MahjongTileEntity> =
        sameTilesInHands(mjTileEntity).also { it += mjTileEntity }


    private fun tilesForAnkan(mjTileEntity: MahjongTileEntity): List<MahjongTileEntity> =
        sameTilesInHands(mjTileEntity)

    val tilesCanAnkan: Set<MahjongTileEntity>
        get() = buildSet {
            hands.distinct().forEach {
                val count = hands.count { tile -> tile.mahjong4jTile.code == it.mahjong4jTile.code }
                if (count == 4) this += it
            }
            if (!riichi && !doubleRiichi) return@buildSet

            forEach {
                val handsCopy = hands.toMutableList()
                val anKanTilesInHands = hands.filter { tile -> tile.mahjong4jTile == it.mahjong4jTile }.toMutableList()
                handsCopy -= anKanTilesInHands.toSet()
                val fuuroListCopy = fuuroList.toMutableList().apply {
                    this += Fuuro(
                        mentsu = Kantsu(false, it.mahjong4jTile),
                        tileMjEntities = anKanTilesInHands,
                        claimTarget = ClaimTarget.SELF,
                        it
                    )
                }
                val mentsuList = fuuroListCopy.map { fuuro -> fuuro.mentsu }
                val calculatedMachi = buildList {
                    MahjongTile.entries.filter { mjTile ->
                        mjTile.mahjong4jTile != it.mahjong4jTile
                    }.forEach { mjTile ->
                        val mj4jTile = mjTile.mahjong4jTile
                        val nowHands =
                            handsCopy.toIntArray().apply { this[mj4jTile.code]++ }
                        val tilesWinnable = tilesWinnable(
                            hands = nowHands,
                            mentsuList = mentsuList,
                            lastTile = mj4jTile
                        )
                        if (tilesWinnable) this += mjTile
                    }
                }
                if (calculatedMachi != machi) {
                    this -= it
                } else {

                    val otherTiles = hands.toIntArray()
                    val mentsuList1 = fuuroList.map { fuuro -> fuuro.mentsu }
                    calculatedMachi.forEach { machiTile ->
                        val tile = machiTile.mahjong4jTile
                        val mj4jHands = Hands(otherTiles, tile, mentsuList1)
                        val mentsuCompSet = mj4jHands.mentsuCompSet
                        val shuntsuList =
                            mentsuCompSet.flatMap { mentsuComp -> mentsuComp.shuntsuList }
                        shuntsuList.forEach { shuntsu ->
                            val middleTile = shuntsu.tile
                            val previousTile = MahjongTile.entries[middleTile.code].previousTile.mahjong4jTile
                            val nextTile = MahjongTile.entries[middleTile.code].nextTile.mahjong4jTile
                            val shuntsuTiles = listOf(previousTile, middleTile, nextTile)
                            if (it.mahjong4jTile in shuntsuTiles) this -= it
                        }
                    }
                }
            }
        }


    private val tilesCanKakan: MutableSet<Pair<MahjongTileEntity, ClaimTarget>>
        get() = mutableSetOf<Pair<MahjongTileEntity, ClaimTarget>>().apply {
            fuuroList.filter { it.mentsu is Kotsu }.forEach { fuuro ->
                val tile =
                    hands.find { it.mahjong4jTile.code == fuuro.claimTile.mahjong4jTile.code }
                if (tile != null) this += tile to fuuro.claimTarget
            }
        }


    private fun tilePairsForChii(mjTileEntity: MahjongTileEntity): List<Pair<MahjongTile, MahjongTile>> {
        val mj4jTile = mjTileEntity.mahjong4jTile
        if (mj4jTile.number == 0) return emptyList()
        val mjTile = mjTileEntity.mahjongTile
        val next = hands.find { it.mahjongTile == mjTile.nextTile }?.mahjongTile
        val nextNext = hands.find { it.mahjongTile == mjTile.nextTile.nextTile }?.mahjongTile
        val previous = hands.find { it.mahjongTile == mjTile.previousTile }?.mahjongTile
        val previousPrevious = hands.find { it.mahjongTile == mjTile.previousTile.previousTile }?.mahjongTile
        val pairs = mutableListOf<Pair<MahjongTile, MahjongTile>>()

        if (mj4jTile.number < 8 && next != null && nextNext != null) pairs += next to nextNext

        if (mj4jTile.number in 2..8 && previous != null && next != null) pairs += previous to next

        if (mj4jTile.number > 2 && previous != null && previousPrevious != null) pairs += previous to previousPrevious

        val sameTypeRedFiveTile =
            hands.filter { it.mahjongTile.isRed && it.mahjong4jTile.type == mj4jTile.type }.getOrNull(0)
        val canChiiWithRedFive =
            (mj4jTile.number in 3..4 || mj4jTile.number in 6..7) && sameTypeRedFiveTile != null
        if (canChiiWithRedFive) {
            val redFiveTile = sameTypeRedFiveTile!!
            val redFiveTileCode = redFiveTile.mahjong4jTile.code
            val targetCode = mj4jTile.code

            val gap = redFiveTileCode - targetCode
            if (gap.absoluteValue == 1) {
                val firstTile = MahjongTile.entries[minOf(redFiveTileCode, targetCode)].previousTile
                val lastTile = MahjongTile.entries[maxOf(redFiveTileCode, targetCode)].nextTile
                val allTileInHands =
                    hands.any { it.mahjongTile == firstTile } && hands.any { it.mahjongTile == lastTile }
                if (allTileInHands) {
                    pairs += firstTile to lastTile
                }
            } else {
                val midTileCode = (redFiveTileCode + targetCode) / 2
                val midTile = MahjongTile.entries[midTileCode]
                val midTileInHands = hands.any { it.mahjongTile == midTile }
                if (midTileInHands) {
                    pairs += if (gap > 0) {
                        redFiveTile.mahjongTile to midTile
                    } else {
                        midTile to redFiveTile.mahjongTile
                    }
                }
            }
        }
        return pairs
    }


    private fun tilePairForPon(mjTileEntity: MahjongTileEntity): Pair<MahjongTile, MahjongTile> {
        val tiles = tilesForPon(mjTileEntity)
        return tiles[0].mahjongTile to tiles[1].mahjongTile
    }


    private val tilePairsForRiichi
        get() = buildList {
            if (hands.size != 14) return@buildList
            val listToAdd = buildList {
                hands.forEach { entity ->
                    val nowHands = hands.toMutableList().also { it -= entity }.toMahjongTileList()
                    val nowMachi = calculateMachi(hands = nowHands)
                    if (nowMachi.isNotEmpty()) {
                        this += entity.mahjongTile to nowMachi
                    }
                }
            }
            addAll(listToAdd.distinct())
        }


    private fun sameTilesInHands(mjTileEntity: MahjongTileEntity): MutableList<MahjongTileEntity> =
        hands.filter { it.mahjong4jTile == mjTileEntity.mahjong4jTile }.toMutableList()

    val isTenpai: Boolean
        get() = machi.isNotEmpty()

    private val machi: List<MahjongTile>
        get() = calculateMachi()

    private fun calculateMachi(
        hands: List<MahjongTile> = this.hands.toMahjongTileList(),
        fuuroList: List<Fuuro> = this.fuuroList,
    ): List<MahjongTile> = MahjongTile.entries.filter {
        val tileInHandsCount = hands.count { tile -> tile.mahjong4jTile == it.mahjong4jTile }
        val tileInFuuroCount =
            fuuroList.sumOf { fuuro -> fuuro.tileMjEntities.count { entity -> entity.mahjong4jTile == it.mahjong4jTile } }
        val allTileHere = (tileInHandsCount + tileInFuuroCount) == 4
        if (allTileHere) return@filter false
        val nowHands = hands.toIntArray().apply { this[it.mahjong4jTile.code]++ }
        val mentsuList = fuuroList.map { fuuro -> fuuro.mentsu }
        if (nowHands.sum() > 14) {
            logger.error("Рука имеет больше 14 карт")
            logger.error(nowHands.joinToString(prefix = "Hands: ") { code -> "$code" })
        }
        tilesWinnable(
            hands = nowHands,
            mentsuList = mentsuList,
            lastTile = it.mahjong4jTile,
        )
    }


    fun calculateMachiAndHan(
        hands: List<MahjongTile> = this.hands.toMahjongTileList(),
        fuuroList: List<Fuuro> = this.fuuroList,
        rule: MahjongRule,
        generalSituation: GeneralSituation,
        personalSituation: PersonalSituation,
    ): Map<MahjongTile, Int> {
        val allMachi = calculateMachi(hands, fuuroList)
        return allMachi.associateWith { machiTile ->
            val yakuSettlement = calculateYakuSettlement(
                winningTile = machiTile,
                isWinningTileInHands = false,
                hands = hands,
                fuuroList = fuuroList,
                rule = rule,
                generalSituation = generalSituation,
                personalSituation = personalSituation,
                doraIndicators = emptyList(),
                uraDoraIndicators = emptyList()
            )
            yakuSettlement.let { if (it.yakuList.isNotEmpty() || it.yakumanList.isNotEmpty()) -1 else it.han }
        }
    }


    fun isFuriten(tile: MahjongTileEntity, discards: List<MahjongTileEntity>): Boolean =
        isFuriten(tile.mahjong4jTile, discards.map { it.mahjong4jTile })


    fun isFuriten(
        tile: Tile, discards: List<Tile>,
        machi: List<Tile> = this.machi.map { it.mahjong4jTile },
    ): Boolean {
        val discardedTiles = discardedTiles.map { it.mahjong4jTile }
        if (tile in discardedTiles) return true

        val lastDiscard = discardedTiles.last()
        val sameTurnStartIndex = discards.indexOf(lastDiscard)
        for (index in sameTurnStartIndex until discards.lastIndex) {

            if (discards[index] in machi) return true
        }

        val riichiSengenTile = riichiSengenTile?.mahjong4jTile ?: return false
        if (riichi || doubleRiichi) {
            val riichiStartIndex = discards.indexOf(riichiSengenTile)
            for (index in riichiStartIndex until discards.lastIndex) {
                if (discards[index] in machi) return true
            }
        }
        return false
    }


    fun isIppatsu(players: List<MahjongPlayerBase>, discards: List<MahjongTileEntity>): Boolean {
        if (riichi) {
            val riichiSengenIndex = discards.indexOf(riichiSengenTile!!)
            if (discards.lastIndex - riichiSengenIndex > 4) return false

            val someoneCalls = discards.slice(riichiSengenIndex..discards.lastIndex).any { tile ->
                players.any {
                    it.fuuroList.any { fuuro ->
                        tile in fuuro.tileMjEntities
                    }
                }
            }
            return !someoneCalls
        }
        return false
    }


    fun isKokushimuso(tile: Tile): Boolean {
        val otherTiles = hands.toIntArray()
        val mentusList = fuuroList.toMentsuList()
        val mj4jHands = Hands(otherTiles, tile, mentusList)
        return mj4jHands.isKokushimuso
    }


    @JvmName("toIntArrayMahjongTileEntity")
    private fun List<MahjongTileEntity>.toIntArray() =
        IntArray(Tile.entries.size) { code -> this.count { it.mahjong4jTile.code == code } }

    private fun List<MahjongTile>.toIntArray() =
        IntArray(Tile.entries.size) { code -> this.count { it.mahjong4jTile.code == code } }

    private fun List<Fuuro>.toMentsuList() = this.map { it.mentsu }


    fun canWin(
        winningTile: MahjongTile,
        isWinningTileInHands: Boolean,
        hands: List<MahjongTile> = this.hands.toMahjongTileList(),
        fuuroList: List<Fuuro> = this.fuuroList,
        rule: MahjongRule,
        generalSituation: GeneralSituation,
        personalSituation: PersonalSituation,
    ): Boolean {
        val yakuSettlement = calculateYakuSettlement(
            winningTile = winningTile,
            isWinningTileInHands = isWinningTileInHands,
            hands = hands,
            fuuroList = fuuroList,
            rule = rule,
            generalSituation = generalSituation,
            personalSituation = personalSituation
        )
        return with(yakuSettlement) { yakumanList.isNotEmpty() || doubleYakumanList.isNotEmpty() || han >= rule.minimumHan.han }
    }


    private fun tilesWinnable(
        hands: IntArray,
        mentsuList: List<Mentsu>,
        lastTile: Tile,
    ): Boolean = Hands(hands, lastTile, mentsuList).canWin


    private fun calculateYakuSettlement(
        winningTile: MahjongTile,
        isWinningTileInHands: Boolean,
        hands: List<MahjongTile>,
        fuuroList: List<Fuuro>,
        rule: MahjongRule,
        generalSituation: GeneralSituation,
        personalSituation: PersonalSituation,
        doraIndicators: List<MahjongTile> = listOf(),
        uraDoraIndicators: List<MahjongTile> = listOf(),
    ): YakuSettlement {
        val handsIntArray = hands.toIntArray().also { if (!isWinningTileInHands) it[winningTile.mahjong4jTile.code]++ }
        val mentsuList = fuuroList.toMentsuList()
        val mj4jHands = Hands(handsIntArray, winningTile.mahjong4jTile, mentsuList)
        if (!mj4jHands.canWin) return YakuSettlement.NO_YAKU
        val mj4jPlayer = Player(mj4jHands, generalSituation, personalSituation).apply { calculate() }
        var finalHan = 0
        var finalFu = 0
        var finalRedFiveCount = 0
        var finalNormalYakuList = mutableListOf<NormalYaku>()
        val finalYakumanList = mj4jPlayer.yakumanList.toMutableList()
        val finalDoubleYakumanList = mutableListOf<DoubleYakuman>()

        if (finalYakumanList.isNotEmpty()) {
            if (!rule.localYaku) {
                if (Yakuman.RENHO in finalYakumanList) finalYakumanList -= Yakuman.RENHO
            }

            val handsWithoutWinningTile =
                hands.toMutableList().also { if (isWinningTileInHands) it -= winningTile }
            val machiBeforeWin = calculateMachi(handsWithoutWinningTile, fuuroList)
            when {
                Yakuman.DAISUSHI in finalYakumanList -> {
                    finalYakumanList -= Yakuman.DAISUSHI
                    finalDoubleYakumanList += DoubleYakuman.DAISUSHI
                }
                Yakuman.KOKUSHIMUSO in finalYakumanList && machiBeforeWin.size == 13 -> {
                    finalYakumanList -= Yakuman.KOKUSHIMUSO
                    finalDoubleYakumanList += DoubleYakuman.KOKUSHIMUSO_JUSANMENMACHI
                }
                Yakuman.CHURENPOHTO in finalYakumanList && machiBeforeWin.size == 9 -> {
                    finalYakumanList -= Yakuman.CHURENPOHTO
                    finalDoubleYakumanList += DoubleYakuman.JUNSEI_CHURENPOHTO
                }
                Yakuman.SUANKO in finalYakumanList && machiBeforeWin.size == 1 -> {
                    finalYakumanList -= Yakuman.SUANKO
                    finalDoubleYakumanList += DoubleYakuman.SUANKO_TANKI
                }
            }
        } else {
            var finalComp: MentsuComp =
                mj4jHands.mentsuCompSet.firstOrNull() ?: throw IllegalStateException("Введенная рука не является выигрышной рукой.")
            mj4jHands.mentsuCompSet.forEach { comp ->
                val yakuStock = mutableListOf<NormalYaku>()
                val resolverSet =
                    Mahjong4jYakuConfig.getNormalYakuResolverSet(comp, generalSituation, personalSituation)
                resolverSet.filter { it.isMatch }.forEach { yakuStock += it.normalYaku }


                if (!rule.openTanyao && mj4jHands.isOpen && NormalYaku.TANYAO in yakuStock) yakuStock -= NormalYaku.TANYAO

                val hanSum = if (mj4jHands.isOpen) yakuStock.sumOf { it.kuisagari } else yakuStock.sumOf { it.han }
                if (hanSum > finalHan) {
                    finalHan = hanSum
                    finalNormalYakuList = yakuStock
                    finalComp = comp
                }
            }

            if (finalHan >= rule.minimumHan.han) {
                val handsComp = mj4jHands.handsComp
                val isRiichi = NormalYaku.REACH in finalNormalYakuList

                val doraAmount = generalSituation.dora.sumOf { handsComp[it.code] }
                repeat(doraAmount) {
                    NormalYaku.DORA.apply { finalNormalYakuList += this; finalHan += this.han }
                }

                if (isRiichi) {
                    val uraDoraAmount = generalSituation.uradora.sumOf { handsComp[it.code] }
                    repeat(uraDoraAmount) {
                        NormalYaku.URADORA.apply { finalNormalYakuList += this; finalHan += this.han }
                    }
                }

                if (rule.redFive != MahjongRule.RedFive.NONE) {
                    val handsRedFiveCount = this@MahjongPlayerBase.hands.count { it.mahjongTile.isRed }
                    val fuuroListRedFiveCount =
                        fuuroList.sumOf { it.tileMjEntities.count { tile -> tile.mahjongTile.isRed } }
                    finalRedFiveCount = handsRedFiveCount + fuuroListRedFiveCount
                    finalHan += finalRedFiveCount
                }
            }

            finalFu = when {
                finalNormalYakuList.size == 0 -> 0
                NormalYaku.PINFU in finalNormalYakuList && NormalYaku.TSUMO in finalNormalYakuList -> 20
                NormalYaku.CHITOITSU in finalNormalYakuList -> 25
                else -> {
                    var tmpFu = 20
                    tmpFu += when {
                        personalSituation.isTsumo -> 2
                        !mj4jHands.isOpen -> 10
                        else -> 0
                    }
                    tmpFu += finalComp.allMentsu.sumOf { it.fu }
                    tmpFu +=
                        if (finalComp.isKanchan(mj4jHands.last) ||
                            finalComp.isPenchan(mj4jHands.last) ||
                            finalComp.isTanki(mj4jHands.last)
                        ) 2 else 0
                    val jantoTile = finalComp.janto.tile
                    if (jantoTile == generalSituation.bakaze) tmpFu += 2
                    if (jantoTile == personalSituation.jikaze) tmpFu += 2
                    if (jantoTile.type == TileType.SANGEN) tmpFu += 2
                    tmpFu
                }
            }
        }
        val fuuroListForSettlement = fuuroList.map { fuuro ->
            val isAnkan = fuuro.mentsu is Kantsu && !fuuro.mentsu.isOpen
            isAnkan to fuuro.tileMjEntities.toMahjongTileList()
        }
        val score =
            if (finalYakumanList.isNotEmpty() || finalDoubleYakumanList.isNotEmpty()) {
                val yakumanScore = finalYakumanList.size * 32000
                val doubleYakumanScore = finalDoubleYakumanList.size * 64000
                val scoreSum = yakumanScore + doubleYakumanScore
                if (personalSituation.isParent) (scoreSum * 1.5).toInt() else scoreSum
            } else {
                Score.calculateScore(personalSituation.isParent, finalHan, finalFu).ron
            }
        return YakuSettlement(
            mahjongPlayer = this@MahjongPlayerBase,
            yakuList = finalNormalYakuList,
            yakumanList = finalYakumanList,
            doubleYakumanList = finalDoubleYakumanList,
            redFiveCount = finalRedFiveCount,
            winningTile = winningTile,
            fuuroList = fuuroListForSettlement,
            doraIndicators = doraIndicators,
            uraDoraIndicators = uraDoraIndicators,
            fu = finalFu,
            han = finalHan,
            score = score
        )
    }


    fun calcYakuSettlementForWin(
        winningTile: MahjongTile,
        isWinningTileInHands: Boolean,
        rule: MahjongRule,
        generalSituation: GeneralSituation,
        personalSituation: PersonalSituation,
        doraIndicators: List<MahjongTile>,
        uraDoraIndicators: List<MahjongTile>,
    ): YakuSettlement = calculateYakuSettlement(
        winningTile = winningTile,
        isWinningTileInHands = isWinningTileInHands,
        hands = this.hands.toMahjongTileList(),
        fuuroList = this.fuuroList,
        rule = rule,
        generalSituation = generalSituation,
        personalSituation = personalSituation,
        doraIndicators = doraIndicators,
        uraDoraIndicators = uraDoraIndicators
    )


    open suspend fun askToDiscardTile(
        timeoutTile: MahjongTile,
        cannotDiscardTiles: List<MahjongTile>,
        skippable: Boolean,
    ): MahjongTile = hands.findLast { it.mahjongTile !in cannotDiscardTiles }?.mahjongTile ?: timeoutTile


    open suspend fun askToChii(
        tile: MahjongTile,
        tilePairs: List<Pair<MahjongTile, MahjongTile>>,
        target: ClaimTarget,
    ): Pair<MahjongTile, MahjongTile>? = null

    suspend fun askToChii(entity: MahjongTileEntity, target: ClaimTarget): Pair<MahjongTile, MahjongTile>? =
        askToChii(tile = entity.mahjongTile, tilePairs = tilePairsForChii(entity), target = target)


    open suspend fun askToPonOrChii(
        tile: MahjongTile,
        tilePairsForChii: List<Pair<MahjongTile, MahjongTile>>,
        tilePairForPon: Pair<MahjongTile, MahjongTile>,
        target: ClaimTarget,
    ): Pair<MahjongTile, MahjongTile>? = null

    suspend fun askToPonOrChii(entity: MahjongTileEntity, target: ClaimTarget): Pair<MahjongTile, MahjongTile>? =
        askToPonOrChii(
            tile = entity.mahjongTile,
            tilePairsForChii = tilePairsForChii(entity),
            tilePairForPon = tilePairForPon(entity),
            target = target
        )


    open suspend fun askToPon(
        tile: MahjongTile,
        tilePairForPon: Pair<MahjongTile, MahjongTile>,
        target: ClaimTarget,
    ): Boolean = true

    suspend fun askToPon(entity: MahjongTileEntity, target: ClaimTarget): Boolean =
        askToPon(
            tile = entity.mahjongTile,
            tilePairForPon = tilePairForPon(entity),
            target = target
        )


    open suspend fun askToAnkanOrKakan(
        canAnkanTiles: Set<MahjongTile>,
        canKakanTiles: Set<Pair<MahjongTile, ClaimTarget>>,
        rule: MahjongRule,
    ): MahjongTile? = null

    suspend fun askToAnkanOrKakan(rule: MahjongRule): MahjongTile? =
        askToAnkanOrKakan(
            canAnkanTiles = tilesCanAnkan.toList().toMahjongTileList().toSet(),
            canKakanTiles = tilesCanKakan.map { it.first.mahjongTile to it.second }.toSet(),
            rule = rule
        )



    open suspend fun askToMinkanOrPon(
        tile: MahjongTile,
        target: ClaimTarget,
        rule: MahjongRule,
    ): MahjongGameBehavior =
        MahjongGameBehavior.MINKAN

    suspend fun askToMinkanOrPon(
        mjTileEntity: MahjongTileEntity,
        target: ClaimTarget,
        rule: MahjongRule,
    ): MahjongGameBehavior =
        askToMinkanOrPon(tile = mjTileEntity.mahjongTile, target = target, rule = rule)

    open suspend fun askToRiichi(
        tilePairsForRiichi: List<Pair<MahjongTile, List<MahjongTile>>> = this.tilePairsForRiichi,
    ): MahjongTile? = null

    open suspend fun askToTsumo(): Boolean = true

    open suspend fun askToRon(tile: MahjongTile, target: ClaimTarget): Boolean = true

    suspend fun askToRon(mjTileEntity: MahjongTileEntity, target: ClaimTarget): Boolean =
        askToRon(tile = mjTileEntity.mahjongTile, target = target)


    open suspend fun askToKyuushuKyuuhai(): Boolean = true


    fun drawTile(tile: MahjongTileEntity) {
        hands += tile
        tile.ownerUUID = uuid
        tile.facing = TileFacing.HORIZONTAL
        tile.inGameTilePosition = TilePosition.HAND

    }

    fun riichi(riichiSengenTile: MahjongTileEntity, isFirstRound: Boolean) {
        this.riichiSengenTile = riichiSengenTile
        if (isFirstRound) doubleRiichi = true else riichi = true
    }


    fun discardTile(tile: MahjongTile): MahjongTileEntity? =
        hands.findLast { it.mahjongTile == tile }?.also {
            hands -= it
            it.facing = TileFacing.UP
            it.inGameTilePosition = TilePosition.OTHER
            discardedTiles += it
            discardedTilesForDisplay += it
            playSoundAtHandsMiddle(soundEvent = SoundEvents.BLOCK_BAMBOO_PLACE)
        }


    fun openHands() {
        hands.forEach {
            it.facing = TileFacing.UP
            it.inGameTilePosition = TilePosition.OTHER
        }
        playSoundAtHandsMiddle(soundEvent = SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value())
    }

    fun closeHands() {
        hands.forEach {
            it.facing = TileFacing.DOWN
            it.inGameTilePosition = TilePosition.OTHER
        }
        playSoundAtHandsMiddle(soundEvent = SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value())
    }


    fun playSoundAtHandsMiddle(
        soundEvent: SoundEvent,
        category: SoundCategory = SoundCategory.VOICE,
        volume: Float = 1f,
        pitch: Float = 1f
    ) {
        when (this) {
            is MahjongPlayer -> {
                // Для реального игрока: проигрываем звук в центре руки
                if (hands.isNotEmpty()) {
                    val handsMiddleIndex = hands.size / 2
                    val middleTile = hands[handsMiddleIndex]
                    middleTile.world.playSound(
                        null, // Отсутствие конкретного игрока-слушателя
                        middleTile.x,
                        middleTile.y,
                        middleTile.z,
                        soundEvent,
                        category,
                        volume,
                        pitch
                    )
                } else {
                    // Если рук нет, используем позицию игрока
                    entity.world.playSound(
                        null,
                        entity.blockPos,
                        soundEvent,
                        category,
                        volume,
                        pitch
                    )
                }
            }
            is MahjongBot -> {
                // Для бота: проигрываем звук в его позиции
                entity.world.playSound(
                    null,
                    entity.blockPos,
                    soundEvent,
                    category, // Используем переданную категорию
                    volume,
                    pitch
                )
            }
        }
    }

}