package mahjong.network

import mahjong.game.game_logic.ClaimTarget
import mahjong.game.game_logic.MahjongGameBehavior
import mahjong.game.game_logic.MahjongTile
import mahjong.id
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload

//информации о игровых действиях, состоянии игры и запросах/ответах между клиентом и сервером
data class MahjongGamePayload(
    val behavior: MahjongGameBehavior,
    val hands: List<MahjongTile> = listOf(),
    val target: ClaimTarget = ClaimTarget.SELF,
    val extraData: String = "",
) : CustomPayload {
    constructor(byteBuf: RegistryByteBuf) : this(
        behavior = byteBuf.readEnumConstant(MahjongGameBehavior::class.java),
        hands = Json.decodeFromString<MutableList<MahjongTile>>(byteBuf.readString(Short.MAX_VALUE.toInt())),
        target = byteBuf.readEnumConstant(ClaimTarget::class.java),
        extraData = byteBuf.readString(Short.MAX_VALUE.toInt())
    )

    fun writeByteBuf(byteBuf: RegistryByteBuf) {
        with(byteBuf) {
            writeEnumConstant(behavior)
            writeString(Json.encodeToString(hands), Short.MAX_VALUE.toInt())
            writeEnumConstant(target)
            writeString(extraData, Short.MAX_VALUE.toInt())
        }
    }

    override fun getId(): CustomPayload.Id<MahjongGamePayload> = ID

    companion object {
        val ID = CustomPayload.Id<MahjongGamePayload>(id("mahjong_game_payload"))
        val CODEC: PacketCodec<RegistryByteBuf, MahjongGamePayload> =
            PacketCodec.of(MahjongGamePayload::writeByteBuf, ::MahjongGamePayload)
    }
}