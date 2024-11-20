package net.lasertag.model

import java.io.Serializable

abstract class UdpMessage(
    open val type: Byte
): Serializable

data class AckMessage (
    override val type: Byte
): UdpMessage(type)

data class TimeMessage (
    override val type: Byte,
    val minutes: Byte,
    val seconds: Byte
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
    val isGameRunning: Boolean,
    val isTeamPlay: Boolean,
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