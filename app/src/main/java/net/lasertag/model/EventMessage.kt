package net.lasertag.model

data class Player(
    val id: Byte,
    val name: String,
    var health: Byte,
    var score: Byte
)

data class EventMessage (
    val type: Byte,
    val counterpartPlayerId: Byte,
    val health: Byte,
    val score: Byte,
    val bulletsLeft: Byte
)

data class StatsMessage (
    val type: Byte,
    val numPlayers: Byte,
    val players: Array<Player>
) {
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