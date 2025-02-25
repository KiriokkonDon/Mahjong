package mahjong.game.player

import mahjong.entity.toMahjongTileList
import mahjong.game.game_logic.ClaimTarget
import mahjong.game.game_logic.MahjongGameBehavior
import mahjong.game.game_logic.MahjongRule
import mahjong.game.game_logic.MahjongTile
import mahjong.network.MahjongGamePayload
import mahjong.network.sendPayloadToPlayer
import mahjong.scheduler.server.ServerScheduler
import mahjong.util.delayOnServer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text


class MahjongPlayer(
    override val entity: ServerPlayerEntity,
) : MahjongPlayerBase() {

    init {

        sendPayloadToPlayer(
            player = entity,
            payload = MahjongGamePayload(behavior = MahjongGameBehavior.AUTO_ARRANGE)
        )
    }

    fun sendMessage(text: Text, overlay: Boolean = true) {
        entity.sendMessage(text, overlay)
    }


    val waitingBehavior = mutableListOf<MahjongGameBehavior>()


    var cancelWaitingBehavior = false


    var behaviorResult: Pair<MahjongGameBehavior, String>? = null


    var cannotDiscardTiles = listOf<MahjongTile>()
        private set

    override fun teleport(targetWorld: ServerWorld, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        this.entity.teleport(targetWorld, x, y, z, yaw, pitch)
    }


    override suspend fun askToDiscardTile(
        timeoutTile: MahjongTile,
        cannotDiscardTiles: List<MahjongTile>,
        skippable: Boolean,
    ): MahjongTile {
        this.cannotDiscardTiles = cannotDiscardTiles
        return waitForBehaviorResult(
            behavior = MahjongGameBehavior.DISCARD,
            extraData = skippable.toString(),
            target = ClaimTarget.SELF,
            skippable = skippable
        ) { behavior, data ->
            this.cannotDiscardTiles = listOf()
            val tileCode = data.toIntOrNull() ?: return@waitForBehaviorResult timeoutTile
            if (behavior == MahjongGameBehavior.DISCARD && tileCode in MahjongTile.entries.toTypedArray().indices) {
                MahjongTile.entries[tileCode]
            } else timeoutTile
        }
    }


    override suspend fun askToChii(
        tile: MahjongTile,
        tilePairs: List<Pair<MahjongTile, MahjongTile>>,
        target: ClaimTarget,
    ): Pair<MahjongTile, MahjongTile>? = waitForBehaviorResult(
        behavior = MahjongGameBehavior.CHII,
        extraData = Json.encodeToString(
            listOf(
                Json.encodeToString(tile),
                Json.encodeToString(tilePairs)
            )
        ),
        target = target
    ) { behavior, data ->
        val result = runCatching {
            Json.decodeFromString<Pair<MahjongTile, MahjongTile>>(data)
        }.getOrNull() ?: return@waitForBehaviorResult null
        if (behavior == MahjongGameBehavior.CHII && result in tilePairs) result
        else null
    }


    override suspend fun askToPonOrChii(
        tile: MahjongTile,
        tilePairsForChii: List<Pair<MahjongTile, MahjongTile>>,
        tilePairForPon: Pair<MahjongTile, MahjongTile>,
        target: ClaimTarget,
    ): Pair<MahjongTile, MahjongTile>? = waitForBehaviorResult(
        behavior = MahjongGameBehavior.PON_OR_CHII,
        waitingBehavior = listOf(
            MahjongGameBehavior.CHII,
            MahjongGameBehavior.PON,
            MahjongGameBehavior.SKIP
        ),
        extraData = Json.encodeToString(
            listOf(
                Json.encodeToString(tile),
                Json.encodeToString(tilePairsForChii),
                Json.encodeToString(tilePairForPon)
            )
        ),
        target = target
    ) { behavior, data ->
        when (behavior) {
            MahjongGameBehavior.CHII -> {
                val result = runCatching {
                    Json.decodeFromString<Pair<MahjongTile, MahjongTile>>(data)
                }.getOrNull() ?: return@waitForBehaviorResult null
                if (result in tilePairsForChii) result else null
            }
            MahjongGameBehavior.PON -> tile to tile
            else -> null
        }
    }



    override suspend fun askToPon(
        tile: MahjongTile,
        tilePairForPon: Pair<MahjongTile, MahjongTile>,
        target: ClaimTarget,
    ): Boolean = waitForBehaviorResult(
        behavior = MahjongGameBehavior.PON,
        extraData = Json.encodeToString(
            listOf(
                Json.encodeToString(tile),
                Json.encodeToString(tilePairForPon)
            )
        ),
        target = target
    ) { behavior, _ ->
        behavior == MahjongGameBehavior.PON
    }


    override suspend fun askToAnkanOrKakan(
        canAnkanTiles: Set<MahjongTile>,
        canKakanTiles: Set<Pair<MahjongTile, ClaimTarget>>,
        rule: MahjongRule,
    ): MahjongTile? = waitForBehaviorResult(
        behavior = MahjongGameBehavior.ANKAN_OR_KAKAN,
        extraData = Json.encodeToString(
            listOf(
                Json.encodeToString(canAnkanTiles),
                Json.encodeToString(canKakanTiles),
                rule.toJsonString()
            )
        ),
        target = ClaimTarget.SELF
    ) { behavior, data ->
        val result = runCatching {
            Json.decodeFromString<MahjongTile>(data)
        }.getOrNull() ?: return@waitForBehaviorResult null
        if (behavior == MahjongGameBehavior.ANKAN_OR_KAKAN) {
            if (result in canAnkanTiles || result in canKakanTiles.unzip().first) result else null
        } else null
    }


    override suspend fun askToMinkanOrPon(
        tile: MahjongTile,
        target: ClaimTarget,
        rule: MahjongRule,
    ): MahjongGameBehavior = waitForBehaviorResult(
        behavior = MahjongGameBehavior.MINKAN,
        waitingBehavior = listOf(MahjongGameBehavior.PON, MahjongGameBehavior.MINKAN),
        extraData = Json.encodeToString(
            listOf(
                Json.encodeToString(tile),
                rule.toJsonString()
            )
        ),
        target = target
    ) { behavior, _ ->
        when (behavior) {
            MahjongGameBehavior.PON -> behavior
            MahjongGameBehavior.MINKAN -> behavior
            else -> MahjongGameBehavior.SKIP
        }
    }


    override suspend fun askToRiichi(tilePairsForRiichi: List<Pair<MahjongTile, List<MahjongTile>>>): MahjongTile? =
        waitForBehaviorResult(
            behavior = MahjongGameBehavior.RIICHI,
            extraData = Json.encodeToString(tilePairsForRiichi),
            target = ClaimTarget.SELF
        ) { behavior, data ->
            val result = runCatching {
                Json.decodeFromString<MahjongTile>(data)
            }.getOrNull() ?: return@waitForBehaviorResult null
            if (behavior == MahjongGameBehavior.RIICHI && result in tilePairsForRiichi.unzip().first) result
            else null
        }


    override suspend fun askToTsumo(): Boolean = waitForBehaviorResult(
        behavior = MahjongGameBehavior.TSUMO,
        extraData = "",
        target = ClaimTarget.SELF
    ) { behavior, _ ->
        behavior == MahjongGameBehavior.TSUMO
    }


    override suspend fun askToRon(tile: MahjongTile, target: ClaimTarget): Boolean = waitForBehaviorResult(
        behavior = MahjongGameBehavior.RON,
        extraData = Json.encodeToString(tile),
        target = target
    ) { behavior, _ ->
        behavior == MahjongGameBehavior.RON
    }

    override suspend fun askToKyuushuKyuuhai(): Boolean = waitForBehaviorResult(
        behavior = MahjongGameBehavior.KYUUSHU_KYUUHAI,
        extraData = "",
        target = ClaimTarget.SELF
    ) { behavior, _ ->
        behavior == MahjongGameBehavior.KYUUSHU_KYUUHAI
    }


    private suspend fun <T> waitForBehaviorResult(
        behavior: MahjongGameBehavior,
        waitingBehavior: List<MahjongGameBehavior> = listOf(behavior),
        extraData: String,
        hands: List<MahjongTile> = this.hands.toMahjongTileList(),
        target: ClaimTarget,
        skippable: Boolean = true,
        onResult: (MahjongGameBehavior, String) -> T,
    ): T {
        this.waitingBehavior += waitingBehavior
        if (skippable) this.waitingBehavior += MahjongGameBehavior.SKIP


        sendPayloadToPlayer(
            player = entity,
            payload = MahjongGamePayload(
                behavior = behavior,
                hands = hands,
                target = target,
                extraData = extraData
            )
        )

        var tBase = basicThinkingTime
        var tExtra = extraThinkingTime
        var count = 0
        var completed = false
        val action = ServerScheduler.scheduleRepeatAction(
            times = (tBase + tExtra) * 20 + 1,
            interval = 0,
        ) {
            count++
            if (cancelWaitingBehavior || behaviorResult != null || ((tBase + tExtra) <= 0 && count % 20 == 1)) {
                completed = true
            } else if ((tBase + tExtra) > 0 && count % 20 == 1) {
                sendPayloadToPlayer(
                    player = entity,
                    payload = MahjongGamePayload(
                        behavior = MahjongGameBehavior.COUNTDOWN_TIME,
                        extraData = Json.encodeToString<Pair<Int?, Int?>>(tBase to tExtra)
                    )
                )

                if (tBase > 0) {
                    tBase--
                } else if (tExtra > 0) {
                    tExtra--
                }
            }
        }
        while (!completed) delayOnServer(50)
        ServerScheduler.removeQueuedAction(action)


        sendPayloadToPlayer(
            player = entity,
            payload = MahjongGamePayload(
                behavior = MahjongGameBehavior.COUNTDOWN_TIME,
                extraData = Json.encodeToString<Pair<Int?, Int?>>(null to null)
            )
        )

        val usedExtraTime = extraThinkingTime - tExtra
        extraThinkingTime -= usedExtraTime
        val result = behaviorResult ?: (MahjongGameBehavior.SKIP to "")
        behaviorResult = null
        this.waitingBehavior.clear()
        return onResult.invoke(result.first, result.second)
    }
}