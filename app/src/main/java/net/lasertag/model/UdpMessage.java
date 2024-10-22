package net.lasertag.model;



public class UdpMessage {

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

    public static class Event {
        private byte type;
        private byte counterpartPlayerId;
        private byte health;
        private byte score;
        private byte bulletsLeft;
    }




}
