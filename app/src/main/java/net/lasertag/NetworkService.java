package net.lasertag;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

import net.lasertag.model.EventMessage;
import net.lasertag.model.Player;
import net.lasertag.model.StatsMessage;
import net.lasertag.model.UdpMessage;
import net.lasertag.model.UdpMessages;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class NetworkService extends Service {

    private static final String TAG = "Lasertag";
    public static final int STATE_IDLE = 0;
    public static final int STATE_GAME = 1;
    public static final int STATE_DEAD = 2;
    public static final int STATE_OFFLINE = 3;

    public static final String SERVER_IP = "192.168.4.95";
    public static final int SERVER_PORT = 9878;
    private static final long HEARTBEAT_INTERVAL = 2000;
    private static final long HEARTBEAT_TIMEOUT = 5000;

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(3);
    private DatagramSocket heartbeatSocket;
    private SoundManager soundManager;

    private volatile boolean isActive = true;
    private volatile boolean isGameRunning = false;
    private volatile boolean isPlayerDead = false;
    private volatile boolean isOnline = false;

    private volatile Long lastPingTime = 0L;
    private volatile int currentState = -1;

    private StatsMessage lastStatsMessage;
    private EventMessage lastEventMessage;

    private final BroadcastReceiver activityResumedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case "ACTIVITY_PAUSED" -> isActive = false;
                case "ACTIVITY_RESUMED" -> {
                    isActive = true;
                    sendCurrentStateToActivity();
                    sendUdpMessageToActivity(lastStatsMessage);
                    sendUdpMessageToActivity(lastEventMessage);
                    lastStatsMessage = null;
                    lastEventMessage = null;
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        soundManager = new SoundManager(this);
        registerReceiver(activityResumedReceiver, new IntentFilter("ACTIVITY_RESUMED"));
        registerReceiver(activityResumedReceiver, new IntentFilter("ACTIVITY_PAUSED"));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(activityResumedReceiver);
        executorService.shutdownNow();
        soundManager.release();
        if (heartbeatSocket != null && !heartbeatSocket.isClosed()) {
            heartbeatSocket.close();
        }
        Log.i(TAG, "Service destroyed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service Starting");
        try {
            heartbeatSocket = new DatagramSocket();
            executorService.scheduleWithFixedDelay(this::heartbeat, 0, HEARTBEAT_INTERVAL, java.util.concurrent.TimeUnit.MILLISECONDS);
            startUDPListener();
            evaluateCurrentState();
        } catch (Exception e) {
            Log.e(TAG, "Service failed to start", e);
            stopSelf();
        }
        return START_STICKY;
    }

    private void heartbeat() {
        if (System.currentTimeMillis() - lastPingTime > HEARTBEAT_TIMEOUT) {
            Log.i(TAG, "LostConnection");
            isOnline = false;
            evaluateCurrentState();
        }
        try {
            byte[] message = new byte[] { UdpMessages.PING, (byte) MainActivity.PLAYER_ID, 0 };
            DatagramPacket packet = new DatagramPacket(message, 3, InetAddress.getByName(SERVER_IP), SERVER_PORT);
            heartbeatSocket.send(packet);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send heartbeat", e);
        }
    }

    private void startUDPListener() {
        executorService.execute(() -> {
            try (var socket = new DatagramSocket(SERVER_PORT)) {
                var buffer = new byte[512];
                Log.i(TAG, "Listening on socket: " + socket.getLocalSocketAddress());
                while (true) {
                    var packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    var message = UdpMessages.fromBytes(packet.getData(), packet.getLength());
                    handleEvent(message.getType(), message);
                    sendUdpMessageToActivity(message);
                    lastPingTime = System.currentTimeMillis();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to receive UDP message", e);
            }
        });
    }

    private void evaluateCurrentState() {
        var newState = currentState;
        if (!isOnline) {
            newState = STATE_OFFLINE;
        } else if (!isGameRunning) {
            newState = STATE_IDLE;
        } else {
            newState = isPlayerDead ? STATE_DEAD : STATE_GAME;
        }
        if (newState != currentState) {
            currentState = newState;
            sendCurrentStateToActivity();
        }
    }

    private void sendCurrentStateToActivity() {
        Intent broadcastIntent = new Intent("CURRENT_STATE");
        broadcastIntent.putExtra("state", currentState);
        sendBroadcast(broadcastIntent);
    }

    private void sendUdpMessageToActivity(UdpMessage message) {
        if (message == null) {
            return;
        }
        if (isActive) {
            Intent broadcastIntent = new Intent("UDP_MESSAGE_RECEIVED");
            broadcastIntent.putExtra("message", message);
            sendBroadcast(broadcastIntent);
        } else if (message instanceof StatsMessage) {
            lastStatsMessage = (StatsMessage) message;
        } else if (message instanceof EventMessage) {
            lastEventMessage = (EventMessage) message;
        }
    }

    private void handleEvent(byte eventType, UdpMessage message) {
        isOnline = true;
        switch (eventType) {
            case UdpMessages.GUN_SHOT -> soundManager.playGunShot();
            case UdpMessages.GUN_RELOAD -> soundManager.playReload();
            case UdpMessages.YOU_HIT_SOMEONE -> soundManager.playYouHitSomeone();
            case UdpMessages.GOT_HIT -> soundManager.playGotHit();
            case UdpMessages.RESPAWN -> {
                soundManager.playRespawn();
                isGameRunning = true;
                isPlayerDead = false;
            }
            case UdpMessages.GAME_OVER -> {
                soundManager.playGameOver();
                isGameRunning = false;
            }
            case UdpMessages.GAME_START -> soundManager.playGameStart();
            case UdpMessages.YOU_KILLED -> {
                soundManager.playYouKilled();
                isPlayerDead = true;
            }
            case UdpMessages.YOU_SCORED -> soundManager.playYouScored();
            case UdpMessages.GUN_NO_BULLETS -> soundManager.playNoBullets();
            case UdpMessages.FULL_STATS -> {
                var statsMessage = (StatsMessage) message;
                isGameRunning = statsMessage.isGameRunning();
                Arrays.stream(statsMessage.getPlayers())
                        .filter(p -> p.getId() == MainActivity.PLAYER_ID)
                        .findFirst()
                        .ifPresent(p -> isPlayerDead = p.getHealth() <= 0);
            }
            case UdpMessages.GAME_TIMER -> {}
        }
        evaluateCurrentState();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // We don't need binding for this service
    }
}

