package net.lasertag.model

import java.io.Serializable

data class Player(
    val id: Byte,
    var health: Byte,
    var score: Byte,
    var teamId: Byte,
    val name: String
) : Serializable