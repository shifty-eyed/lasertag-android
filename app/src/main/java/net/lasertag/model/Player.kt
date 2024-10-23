package net.lasertag.model

data class Player(
    val id: Byte,
    var health: Byte,
    var score: Byte,
    val name: String
)