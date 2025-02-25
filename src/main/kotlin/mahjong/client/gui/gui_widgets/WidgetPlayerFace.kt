package mahjong.client.gui.gui_widgets

import com.mojang.authlib.GameProfile
import mahjong.util.RenderHelper
import io.github.cottonmc.cotton.gui.widget.WWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.block.entity.SkullBlockEntity
import net.minecraft.client.gui.DrawContext
import java.util.*
import kotlin.jvm.optionals.getOrNull
import kotlin.math.min

class WidgetPlayerFace(
    uuid: UUID,
    name: String,
) : WWidget() {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private var gameProfile = GameProfile(uuid, name)

    var uuid: UUID = uuid
        private set
    var name: String = name
        private set

    init {
        loadGameProfileProperties()
    }

    fun setUuidAndName(uuid: UUID, name: String) {
        this.uuid = uuid
        this.name = name
        gameProfile = GameProfile(uuid, name)
        loadGameProfileProperties()
    }

    override fun canResize(): Boolean = true

    private fun loadGameProfileProperties() {
        coroutineScope.launch(Dispatchers.IO) {
            val profile = SkullBlockEntity.fetchProfileByUuid(uuid).get().getOrNull()
                ?: SkullBlockEntity.fetchProfileByName(name).get().getOrNull()
            if (profile != null) gameProfile = profile
        }
    }

    @Environment(EnvType.CLIENT)
    override fun paint(context: DrawContext, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        RenderHelper.renderPlayerFace(
            context = context,
            gameProfile = gameProfile,
            x = x,
            y = y,
            size = min(width, height)
        )
    }
}