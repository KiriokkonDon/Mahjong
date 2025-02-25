package mahjong.network

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity

//Утилиты для сети
@Environment(EnvType.CLIENT)
fun CustomPayload.sendToServer() {
    ClientPlayNetworking.send(this)
}

@Environment(EnvType.CLIENT)
fun sendPayloadToServer(payload: CustomPayload) = payload.sendToServer()

fun CustomPayload.sendToPlayer(player: ServerPlayerEntity) {
    ServerPlayNetworking.send(player, this)
}

fun sendPayloadToPlayer(player: ServerPlayerEntity, payload: CustomPayload) = payload.sendToPlayer(player)
