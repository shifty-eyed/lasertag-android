package net.lasertag;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.InetAddress;
import java.util.UUID;

public class Config {

    public static final int DEFAULT_PLAYER_ID = 1;

    public static final String TAG = "Lasertag";
    public static final String CHANNEL_ID = "LasertagForegroundServiceChannel";

    private static final String PREFS_NAME = "LaserTagPrefs";
    private static final String PLAYER_ID_KEY = "player_id";
    public static final String GUN_DEVICE_NAME = "LaserTagGun";
    public static final String VEST_DEVICE_NAME = "LaserTagVest";
    public static final UUID SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public static final String INTERCOM_GAME_MESSAGE = "UDP_MESSAGE_RECEIVED";
    public static final String INTERCOM_TIME_TICK = "TIME_TICK_RECEIVED";
    public static final String INTERCOM_GAME_STATE = "CURRENT_STATE";

    public static final int STATE_IDLE = 0;
    public static final int STATE_GAME = 1;
    public static final int STATE_DEAD = 2;
    public static final int STATE_OFFLINE = 3;

    public static final int SERVER_PORT = 9878;
    public static final int LISTENING_PORT = 1234;
    public static final long HEARTBEAT_INTERVAL = 2000;
    public static final long HEARTBEAT_TIMEOUT = 5000;

    public static final int MAX_HEALTH = 100;
    public static final int MAGAZINE_SIZE = 10;


    private final byte playerId;
    private InetAddress broadcastAddress;
    private InetAddress serverAddress = null;

    public Config(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        playerId = (byte)prefs.getInt(PLAYER_ID_KEY, DEFAULT_PLAYER_ID);
        try {
            broadcastAddress = InetAddress.getByName("255.255.255.255");
        } catch (Exception ignored) {}
    }

    public byte getPlayerId() {
        return playerId;
    }

    public InetAddress getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(InetAddress serverAddress) {
        this.serverAddress = serverAddress;
    }

    public InetAddress getBroadcastAddress() {
        return broadcastAddress;
    }

    public int getTeamColor(int teamId, boolean background) {
        return switch (teamId) {
            case 1 -> background ? R.color.tableRowBackgroundRed : R.color.nameTextRed;
            case 2 -> background ? R.color.tableRowBackgroundBlue : R.color.nameTextBlue;
            case 3 -> background ? R.color.tableRowBackgroundGreen : R.color.nameTextGreen;
            case 4 -> background ? R.color.tableRowBackgroundYellow : R.color.nameTextYellow;
            case 5 -> background ? R.color.tableRowBackgroundMagenta : R.color.nameTextMagenta;
            case 6 -> background ? R.color.tableRowBackgroundCyan : R.color.nameTextCyan;
            default -> R.color.tableRowBackground;
        };
    }
}
