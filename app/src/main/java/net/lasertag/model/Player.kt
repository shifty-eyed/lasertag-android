package net.lasertag.model

import net.lasertag.Config
import java.io.Serializable

data class Player(
    val id: Int,
    var health: Int,
    var score: Int,
    var teamId: Int,
    var damage: Int,
    var bulletsLeft: Int,
    var name: String
) : Serializable, Comparable<Player> {
    constructor(id: Int) : this(id, 100, 0, 0, 0, 0, "NoName")

    fun isAlive(): Boolean {
        return health > 0
    }

    fun decreaseHealth(damage: Int) {
        health -= damage
        if (health < 0) {
            health = 0
        }
    }

    fun decreaseBullets() {
        bulletsLeft--
        if (bulletsLeft < 0) {
            bulletsLeft = 0
        }
    }

    fun respawn() {
        health = Config.MAX_HEALTH
        bulletsLeft = Config.MAGAZINE_SIZE
    }

    fun reload() {
        bulletsLeft = Config.MAGAZINE_SIZE
    }

    fun copyPlayerValuesFrom(player: Player) {
        health = player.health
        score = player.score
        teamId = player.teamId
        damage = player.damage
        if (player.name.isNotEmpty()) {
            name = player.name
        }
    }

    override fun compareTo(other: Player): Int {
        return other.score - score
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Player
        return id == other.id
    }

    override fun hashCode(): Int {
        return id
    }
}