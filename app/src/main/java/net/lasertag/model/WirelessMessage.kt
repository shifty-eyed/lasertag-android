package net.lasertag.model

import java.io.Serializable

abstract class WirelessMessage(
    open val type: Byte
): Serializable {
    open fun getBytes(): ByteArray {
        return byteArrayOf(type)
    }
}

data class PingMessage (
    override val type: Byte = UdpMessages.PING
): WirelessMessage(type)

data class TimeMessage (
    override val type: Byte,
    val minutes: Byte,
    val seconds: Byte
): WirelessMessage(type)

data class EventMessageIn (
    override val type: Byte,
    val payload: Byte
): WirelessMessage(type)

data class EventMessageToServer (
    override val type: Byte,
    val playerId: Byte,
    val otherPlayerId: Byte,
    val health: Byte,
    val score: Byte,
    val bulletsLeft: Byte
): WirelessMessage(type) {
    override fun getBytes(): ByteArray {
        return byteArrayOf(type, playerId, otherPlayerId, health, score, bulletsLeft)
    }
}

data class MessageToDevice (
    override val type: Byte,
    val playerId: Byte,
    val playerTeam: Byte,
    val playerState: Byte,
    val bulletsLeft: Byte
): WirelessMessage(type) {
    override fun getBytes(): ByteArray {
        return byteArrayOf(type, playerId, playerTeam, playerState, bulletsLeft)
    }
}

data class StatsMessageIn (
    override val type: Byte,
    val isGameRunning: Boolean,
    val isTeamPlay: Boolean,
    val numPlayers: Byte,
    val players: Array<Player>
) : WirelessMessage(type) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StatsMessageIn
        return type == other.type
    }

    override fun hashCode(): Int {
        return type.toInt()
    }
}