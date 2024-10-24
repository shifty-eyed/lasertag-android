package net.lasertag.model;


import java.nio.ByteBuffer;
import java.util.Arrays;

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

    public static UdpMessage fromBytes(byte[] bytes, int length) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, length);
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        byte type = buffer.get();
        if (type == PING) {
            return new AckMessage(PING);
        } else if (type == FULL_STATS) {
            return parseFullStatsMessage(buffer);
        } else {
            return parseEventMessage(type, buffer);
        }
    }

    private static StatsMessage parseFullStatsMessage(ByteBuffer buffer) {
        var playersCount = buffer.get();
        var players = new Player[playersCount];
        for (int i = 0; i < playersCount; i++) {
            var id = buffer.get();
            var health = buffer.get();
            var score = buffer.get();
            var nameLength = buffer.get();
            var nameBytes = new byte[nameLength];
            buffer.get(nameBytes, 0, nameLength);
            var name = new String(nameBytes);
            players[i] = new Player(id, health, score, name);
        }
        Arrays.sort(players, (a, b) -> Integer.compare(b.getScore(), a.getScore()));
        return new StatsMessage(FULL_STATS, playersCount, players);
    }

    private static EventMessage parseEventMessage(byte type, ByteBuffer buffer) {
        var counterpartPlayerId = buffer.get();
        var health = buffer.get();
        var score = buffer.get();
        var bulletsLeft = buffer.get();
        return new EventMessage(type, counterpartPlayerId, health, score, bulletsLeft);
    }


}
