package mahjong.scheduler

interface ActionBase {

    var stop: Boolean
    var timeToAction: Long
    val action: () -> Unit

    fun tick(): Boolean
}