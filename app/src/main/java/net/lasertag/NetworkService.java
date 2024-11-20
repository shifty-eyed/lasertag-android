package net.lasertag;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import net.lasertag.model.EventMessage;
import static net.lasertag.Config.*;

import androidx.annotation.RequiresApi;

import net.lasertag.model.StatsMessage;
import net.lasertag.model.UdpMessage;
import net.lasertag.model.UdpMessages;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class NetworkService extends Service {

    private Config config;
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private DatagramSocket heartbeatSocket;
    private SoundManager soundManager;

    private volatile boolean isActive = true;
    private volatile boolean isGameRunning = false;
    private volatile boolean isPlayerDead = false;
    private volatile boolean isOnline = false;
    private volatile boolean teamPlay = false;

    private volatile Long lastPingTime = 0L;
    private volatile int currentState = -1;
    private boolean firstEverMessage = true;

    private StatsMessage lastStatsMessage;
    private EventMessage lastEventMessage;


    private final BroadcastReceiver activityResumedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (Objects.requireNonNull(intent.getAction())) {
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
        config = new Config(this);
        soundManager = new SoundManager(this);
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Your Service is Running")
                .setContentText("This service will keep running.")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .build();

        startForeground(1, notification);
        registerReceiver(activityResumedReceiver, new IntentFilter("ACTIVITY_RESUMED"), Context.RECEIVER_EXPORTED);
        registerReceiver(activityResumedReceiver, new IntentFilter("ACTIVITY_PAUSED"), Context.RECEIVER_EXPORTED);
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_ID, "Foreground Service Channel", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
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
            byte[] message = new byte[] { UdpMessages.PING, config.getPlayerId(), firstEverMessage ? (byte) 1 : (byte) 0 };
            var ip = config.getServerAddress() == null ? config.getBroadcastAddress() : config.getServerAddress();
            DatagramPacket packet = new DatagramPacket(message, 3, ip, SERVER_PORT);
            heartbeatSocket.send(packet);
            firstEverMessage = false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to send heartbeat", e);
        }
    }

    private void startUDPListener() {
        executorService.execute(() -> {
            try (var socket = new DatagramSocket(LISTENING_PORT)) {
                var buffer = new byte[512];
                Log.i(TAG, "Listening on socket: " + socket.getLocalSocketAddress());
                while (true) {
                    var packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    var message = UdpMessages.fromBytes(packet.getData(), packet.getLength());
                    if (config.getServerAddress() == null) {
                        Log.i(TAG, "Server IP discovered: " + packet.getAddress());
                        config.setServerAddress(packet.getAddress());
                    }
                    mainHandler.post(() -> sendUdpMessageToActivity(message));
                    mainHandler.post(() -> handleEvent(message.getType(), message));
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
        broadcastIntent.putExtra("teamPlay", teamPlay);
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
            case UdpMessages.GAME_START -> {
                soundManager.playGameStart();
                teamPlay = ((EventMessage) message).getCounterpartPlayerId() != 0;
            }
            case UdpMessages.YOU_KILLED -> {
                soundManager.playYouKilled();
                isPlayerDead = true;
            }
            case UdpMessages.YOU_SCORED -> soundManager.playYouScored();
            case UdpMessages.GUN_NO_BULLETS -> soundManager.playNoBullets();
            case UdpMessages.FULL_STATS -> {
                var statsMessage = (StatsMessage) message;
                isGameRunning = statsMessage.isGameRunning();
                teamPlay = statsMessage.isTeamPlay();
                Arrays.stream(statsMessage.getPlayers())
                        .filter(p -> p.getId() == config.getPlayerId())
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

