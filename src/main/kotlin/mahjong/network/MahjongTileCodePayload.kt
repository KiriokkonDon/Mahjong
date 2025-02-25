package mahjong.network

import mahjong.id
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload

//Используется для передачи кодов тайлов

data class MahjongTileCodePayload(
    val id: Int,
    val code: Int = 0,
) : CustomPayload {
    constructor(byteBuf: PacketByteBuf) : this(
        id = byteBuf.readVarInt(),
        code = byteBuf.readVarInt()
    )

    fun writeByteBuf(byteBuf: PacketByteBuf) {
        with(byteBuf) {
            writeVarInt(id)
            writeVarInt(code)
        }
    }

    override fun getId(): CustomPayload.Id<MahjongTileCodePayload> = ID

    companion object {
        val ID = CustomPayload.Id<MahjongTileCodePayload>(id("mahjong_tile_code_payload"))
        val CODEC: PacketCodec<RegistryByteBuf, MahjongTileCodePayload> =
            PacketCodec.of(MahjongTileCodePayload::writeByteBuf, ::MahjongTileCodePayload)
    }
}