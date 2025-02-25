package mahjong.game.game_logic


enum class ScoringStick(
    val point: Int
) {
    P100(100),
    P1000(1000),
    P5000(5000),
    P10000(10000)
    ;

    val code = this.ordinal
}