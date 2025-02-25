package mahjong.game.game_logic



enum class MahjongTableBehavior {

    JOIN,
    LEAVE,
    READY,
    NOT_READY,
    START,
    KICK,
    ADD_BOT,
    CHANGE_RULE,


    OPEN_TABLE_WAITING_GUI,
    OPEN_TABLE_PLAYING_GUI,


    OPEN_RULES_EDITOR_GUI,
}
