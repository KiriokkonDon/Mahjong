package mahjong.network

//типы каналов связи для сетевых пакетов
enum class ChannelType(val s2c: Boolean, val c2s: Boolean) {
    Both(true, true)
}