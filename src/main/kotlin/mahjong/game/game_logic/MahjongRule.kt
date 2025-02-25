package mahjong.game.game_logic

import mahjong.MOD_ID
import mahjong.util.TextFormatting
import mahjong.util.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting


@Serializable
data class MahjongRule(
    var length: GameLength = GameLength.TWO_WIND,
    var thinkingTime: ThinkingTime = ThinkingTime.NORMAL,
    var startingPoints: Int = 25000,
    var minPointsToWin: Int = 30000,
    var minimumHan: MinimumHan = MinimumHan.ONE,
    var spectate: Boolean = true,
    var redFive: RedFive = RedFive.NONE,
    var openTanyao: Boolean = true,
    var localYaku: Boolean = false
) {

    fun toJsonString(): String = Json.encodeToString(serializer(), this)

    fun toTexts(
        color1: Formatting = Formatting.RED,
        color2: Formatting = Formatting.YELLOW,
        color3: Formatting = Formatting.GREEN,
        color4: Formatting = Formatting.AQUA,
        color5: Formatting = Formatting.WHITE
    ): List<Text> {
        val colon = Text.literal(": ").formatted(color5)
        val rules =
            Text.translatable("$MOD_ID.game.rules").formatted(color1).formatted(Formatting.BOLD)
        val lengthText =
            (Text.translatable("$MOD_ID.game.length") + colon).formatted(color2)
        val thinkingTimeText =
            (Text.translatable("$MOD_ID.game.thinking_time") + colon).formatted(color2)
        val startingPointsText =
            (Text.translatable("$MOD_ID.game.starting_points") + colon).formatted(color2)
        val minPointsToWinText =
            (Text.translatable("$MOD_ID.game.min_points_to_win") + colon).formatted(color2)
        val minimumHanText =
            (Text.translatable("$MOD_ID.game.minimum_han") + colon).formatted(color2)
        val spectateText =
            (Text.translatable("$MOD_ID.game.spectate") + colon).formatted(color2)
        val redFiveText =
            (Text.translatable("$MOD_ID.game.red_five") + colon).formatted(color2)
        val openTanyaoText =
            (Text.translatable("$MOD_ID.game.open_tanyao") + colon).formatted(color2)
        val enable = Text.translatable("$MOD_ID.game.enabled").formatted(color3)
        val disable = Text.translatable("$MOD_ID.game.disabled").formatted(color3)
        val spectateStatus = if (spectate) enable else disable
        val openTanyaoStatus = if (openTanyao) enable else disable
        val second = Text.translatable("$MOD_ID.game.seconds").formatted(color3)
        return listOf(
            rules,
            Text.literal("§3 - ") + lengthText + (length.toText() as MutableText).formatted(color3),
            Text.literal("§3 - ")
                    + thinkingTimeText
                    + Text.literal("${thinkingTime.base}").formatted(color4)
                    + Text.literal(" + ").formatted(color1)
                    + Text.literal("${thinkingTime.extra}").formatted(color4)
                    + " " + second,
            Text.literal("§3 - ") + startingPointsText + Text.literal("$startingPoints").formatted(color3),
            Text.literal("§3 - ") + minPointsToWinText + Text.literal("$minPointsToWin").formatted(color3),
            Text.literal("§3 - ") + minimumHanText + Text.literal("${minimumHan.han}").formatted(color3),
            Text.literal("§3 - ") + spectateText + spectateStatus,
            Text.literal("§3 - ") + redFiveText + Text.literal("${redFive.quantity}").formatted(color3),
            Text.literal("§3 - ") + openTanyaoText + openTanyaoStatus
        )
    }

    companion object {

        fun fromJsonString(jsonString: String): MahjongRule = Json.decodeFromString(serializer(), jsonString)

        const val MAX_POINTS = 200000
        const val MIN_POINTS = 100
    }


    enum class GameLength(
        private val startingWind: Wind,
        val rounds: Int,
        val finalRound: Pair<Wind, Int>
    ) : TextFormatting {
        ONE_GAME(Wind.EAST, 1, Wind.EAST to 3),
        EAST(Wind.EAST, 4, Wind.SOUTH to 3),
        SOUTH(Wind.SOUTH, 4, Wind.WEST to 3),
        TWO_WIND(Wind.EAST, 8, Wind.WEST to 3);



        fun getStartingRound(): MahjongRound = MahjongRound(wind = startingWind)

        override fun toText() = Text.translatable("$MOD_ID.game.length.${name.lowercase()}")
    }


    enum class MinimumHan(val han: Int) : TextFormatting {
        ONE(1),
        TWO(2),
        FOUR(4),
        YAKUMAN(13);

        override fun toText(): Text = Text.of(han.toString())
    }

    enum class ThinkingTime(
        val base: Int,
        val extra: Int
    ) : TextFormatting {
        VERY_SHORT(3, 5),
        SHORT(5, 10),
        NORMAL(5, 20),
        LONG(60, 0),
        VERY_LONG(300, 0);

        override fun toText(): Text = Text.of("$base + $extra s")
    }


    enum class RedFive(
        val quantity: Int,
    ) : TextFormatting {
        NONE(0),
        THREE(3),
        FOUR(4);

        override fun toText(): Text = Text.of(quantity.toString())
    }
}