package net.lasertag.model

abstract class UdpMessage(
    open val type: Byte
)

data class AckMessage (
    override val type: Byte
): UdpMessage(type)

data class EventMessage (
    override val type: Byte,
    val counterpartPlayerId: Byte,
    val health: Byte,
    val score: Byte,
    val bulletsLeft: Byte
): UdpMessage(type)

data class StatsMessage (
    override val type: Byte,
    val numPlayers: Byte,
    val players: Array<Player>
) : UdpMessage(type) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StatsMessage
        return type == other.type
    }

    override fun hashCode(): Int {
        return type.toInt()
    }
}