package net.lasertag;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.InetAddress;

public class Config {

    public static final String TAG = "Lasertag";
    public static final String CHANNEL_ID = "LasertagForegroundServiceChannel";

    private static final String PREFS_NAME = "LaserTagPrefs";
    private static final String PLAYER_ID_KEY = "player_id";
    public static final int DEFAULT_PLAYER_ID = 1;
    private static final String SERVER_IP = "server_ip";
    private static final String DEFAULT_SERVER_IP = "192.168.4.95";

    public static final int STATE_IDLE = 0;
    public static final int STATE_GAME = 1;
    public static final int STATE_DEAD = 2;
    public static final int STATE_OFFLINE = 3;

    public static final int SERVER_PORT = 9878;
    public static final long HEARTBEAT_INTERVAL = 2000;
    public static final long HEARTBEAT_TIMEOUT = 5000;


    private final byte playerId;
    private final InetAddress serverAddress;

    public Config(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        playerId = (byte)prefs.getInt(PLAYER_ID_KEY, DEFAULT_PLAYER_ID);
        try {
            serverAddress = InetAddress.getByName(prefs.getString(SERVER_IP, DEFAULT_SERVER_IP));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public byte getPlayerId() {
        return playerId;
    }

    public InetAddress getServerAddress() {
        return serverAddress;
    }
}
