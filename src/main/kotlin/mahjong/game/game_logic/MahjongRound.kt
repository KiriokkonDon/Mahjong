package mahjong.game.game_logic

import kotlinx.serialization.Serializable


@Serializable
data class MahjongRound(
    var wind: Wind = Wind.EAST,
    var round: Int = 0,
    var honba: Int = 0
) {
    private var spentRounds = 0

    fun nextRound() {
        val nextRound = (this.round + 1) % 4
        honba = 0
        if (nextRound == 0) {
            val nextWindNum = (this.wind.ordinal + 1) % 4
            wind = Wind.values()[nextWindNum]
        }
        round = nextRound
        spentRounds++
    }


    fun isAllLast(rule: MahjongRule): Boolean = (spentRounds + 1) >= rule.length.rounds
}