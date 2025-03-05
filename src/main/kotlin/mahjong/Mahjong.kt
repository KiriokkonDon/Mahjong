package mahjong

import mahjong.entity.BotEntity
import mahjong.network.event.onPlayerChangedWorld
import mahjong.network.event.onPlayerDisconnect
import mahjong.network.MahjongGamePayloadListener
import mahjong.network.MahjongTablePayloadListener
import mahjong.network.MahjongTileCodePayloadListener
import mahjong.registry.*
import mahjong.scheduler.server.ServerScheduler
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.util.Identifier
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

const val MOD_ID = "mahjong"
val logger: Logger = LogManager.getLogger()

fun id(path: String): Identifier = Identifier.of(MOD_ID, path)

object Mahjong : ModInitializer {

    override fun onInitialize() {
        // Registry
        ItemRegistry.register()
        EntityTypeRegistry.register()
        BlockRegistry.register()
        BlockEntityTypeRegistry.register()
        SoundRegistry.register()


        // Register entity attributes
        FabricDefaultAttributeRegistry.register(
            EntityTypeRegistry.mahjongBot,
            BotEntity.createLivingAttributes() // Directly pass the DefaultAttributeContainer.Builder
        )


        // Event
        ServerTickEvents.END_SERVER_TICK.register(ServerScheduler::tick)
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerScheduler::onStopping)
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(::onPlayerChangedWorld)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> onPlayerDisconnect(handler.player) }

        // Packet
        MahjongTablePayloadListener.registerCommon()
        MahjongGamePayloadListener.registerCommon()
        MahjongTileCodePayloadListener.registerCommon()

        MahjongTablePayloadListener.registerServer()
        MahjongGamePayloadListener.registerServer()
        MahjongTileCodePayloadListener.registerServer()


    }
}