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
    val name: String
) : Serializable {
    constructor(id: Int) : this(id, 0, 0, 0, 0, 0, "NoName")

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

    fun copyFrom(player: Player) {
        health = player.health
        score = player.score
        teamId = player.teamId
        damage = player.damage
        bulletsLeft = player.bulletsLeft
    }
}