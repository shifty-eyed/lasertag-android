package net.lasertag.model;


import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class UdpMessages {

    public static final byte PING = 1;
    public static final byte GUN_SHOT = 2;
    public static final byte GUN_RELOAD = 3;
    public static final byte YOU_HIT_SOMEONE = 4;
    public static final byte GOT_HIT = 5;
    public static final byte RESPAWN = 6;
    public static final byte GAME_OVER = 7;
    public static final byte GAME_START = 8;
    public static final byte YOU_KILLED = 9;
    public static final byte YOU_SCORED = 10;
    public static final byte FULL_STATS = 11;
    public static final byte GUN_NO_BULLETS = 12;
    public static final byte DEVICE_PLAYER_STATE = 13;

    public static final byte GAME_TIMER = 101;
    public static final byte LOST_CONNECTION = 102;

    public static WirelessMessage fromBytes(byte[] bytes, int length) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, length);
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        byte type = buffer.get();
        if (type == PING) {
            return new PingMessage(PING);
        } else if (type == FULL_STATS) {
            return parseFullStatsMessage(buffer);
        } else if (type == GAME_TIMER) {
            return parseTimeMessage(type, buffer);
        } else {
            return parseEventFromServer(type, buffer);
        }
    }


    private static EventMessageIn parseEventFromServer(byte type, ByteBuffer buffer) {
        var otherPlayerId = buffer.get();
        return new EventMessageIn(type, otherPlayerId);
    }

    public static EventMessageIn parseMessageFromDevice(List<Byte> bytes) {
        return new EventMessageIn(bytes.get(0), bytes.get(1));
    }

    private static StatsMessageIn parseFullStatsMessage(ByteBuffer buffer) {
        var isGameRunning = buffer.get() != 0;
        var teamPlay = buffer.get() != 0;
        var playersCount = buffer.get();
        var players = new Player[playersCount];
        for (int i = 0; i < playersCount; i++) {
            var id = buffer.get();
            var health = buffer.get();
            var score = buffer.get();
            var teamId = buffer.get();
            var damage = buffer.get();
            var maxHealth = buffer.get();
            var maxBullets = buffer.get();
            var bulletsLeft = buffer.get();
            var nameLength = buffer.get();
            var nameBytes = new byte[nameLength];
            buffer.get(nameBytes, 0, nameLength);
            var name = new String(nameBytes);
            players[i] = new Player(id, health, score, teamId, damage, maxHealth, maxBullets, bulletsLeft, name);
        }
        Arrays.sort(players, (a, b) -> Integer.compare(b.getScore(), a.getScore()));
        return new StatsMessageIn(FULL_STATS, isGameRunning, teamPlay, playersCount, players);
    }

    private static TimeMessage parseTimeMessage(byte type, ByteBuffer buffer) {
        var minutes = buffer.get();
        var seconds = buffer.get();
        return new TimeMessage(type, minutes, seconds);
    }


}
