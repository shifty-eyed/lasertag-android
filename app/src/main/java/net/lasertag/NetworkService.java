package net.lasertag;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

import net.lasertag.communication.BluetoothClient;
import net.lasertag.communication.UdpClient;
import net.lasertag.model.EventMessageIn;
import net.lasertag.model.WirelessMessage;

import static net.lasertag.Config.*;

import net.lasertag.model.EventMessageToServer;
import net.lasertag.model.MessageToDevice;
import net.lasertag.model.Player;
import net.lasertag.model.StatsMessageIn;
import net.lasertag.model.TimeMessage;
import net.lasertag.model.UdpMessages;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressLint("MissingPermission")
public class NetworkService extends Service {

    private Config config;
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(3);
    private SoundManager soundManager;

    private volatile boolean isActive = true;
    private volatile boolean isGameRunning = false;
    private volatile boolean teamPlay = false;

    private final AtomicInteger gameTimerSeconds = new AtomicInteger(0);
    private volatile int currentState = -1;
    private final Player thisPlayer = new Player(config.getPlayerId());
    private Player[] allPlayersSnapshot = new Player[0];

    private StatsMessageIn lastStatsMessage;
    private EventMessageIn lastReceivedEvent;

    private BluetoothClient gunComm;
    private BluetoothClient vestComm;
    private UdpClient udpClient;

    private final BroadcastReceiver activityResumedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (Objects.requireNonNull(intent.getAction())) {
                case "ACTIVITY_PAUSED" -> isActive = false;
                case "ACTIVITY_RESUMED" -> {
                    isActive = true;
                    sendCurrentStateToActivity();
                    sendMessageToActivity(lastStatsMessage, INTERCOM_GAME_MESSAGE);
                    sendMessageToActivity(lastReceivedEvent, INTERCOM_GAME_MESSAGE);
                    lastStatsMessage = null;
                    lastReceivedEvent = null;
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

        try {
            var bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            gunComm = new BluetoothClient(Config.GUN_DEVICE_NAME, bluetoothAdapter, this::handleEventFromDevice);
            gunComm.start();

            vestComm = new BluetoothClient(Config.VEST_DEVICE_NAME, bluetoothAdapter, this::handleEventFromDevice);
            vestComm.start();

            udpClient = new UdpClient(config, this::handleEventFromServer);
            udpClient.start();

            executorService.scheduleWithFixedDelay(this::timerTick, 0, 1, java.util.concurrent.TimeUnit.SECONDS);
            evaluateCurrentState();
        } catch (Exception e) {
            Log.e(TAG, "Service failed to start", e);
            stopSelf();
        }
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
        if (gunComm != null && vestComm != null) {
            gunComm.stop();
            vestComm.stop();
        }
        if (udpClient != null) {
            udpClient.stop();
        }
        Log.i(TAG, "Service destroyed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void timerTick() {
        if (gameTimerSeconds.decrementAndGet() < 0) {
            gameTimerSeconds.set(0);
        }
        var minutes = (byte) (gameTimerSeconds.get() / 60);
        var seconds = (byte) (gameTimerSeconds.get() % 60);
        sendMessageToActivity(new TimeMessage(UdpMessages.GAME_TIMER, minutes, seconds), INTERCOM_TIME_TICK);
    }

    private void evaluateCurrentState() {
        var newState = currentState;
        if (!udpClient.isOnline()) {
            newState = STATE_OFFLINE;
        } else if (!isGameRunning) {
            newState = STATE_IDLE;
        } else {
            newState = !thisPlayer.isAlive() ? STATE_DEAD : STATE_GAME;
        }
        if (newState != currentState) {
            currentState = newState;
            sendCurrentStateToActivity();
            sendCurrentStateToDevice();
        }
    }

    private void sendCurrentStateToActivity() {
        Intent broadcastIntent = new Intent("CURRENT_STATE");
        broadcastIntent.putExtra("state", currentState);
        broadcastIntent.putExtra("teamPlay", teamPlay);
        sendBroadcast(broadcastIntent);
    }

    private void sendCurrentStateToDevice() {
        var message = new MessageToDevice(
                UdpMessages.DEVICE_PLAYER_STATE,
                config.getPlayerId(),
                (byte)thisPlayer.getTeamId(),
                (byte)currentState,
                (byte)thisPlayer.getBulletsLeft());
        gunComm.sendMessageToDevice(message);
        vestComm.sendMessageToDevice(message);
    }

    private void sendMessageToActivity(WirelessMessage message, String action) {
        if (message == null || message.getType() == UdpMessages.PING) {
            return;
        }
        if (isActive) {
            Intent broadcastIntent = new Intent(action);
            broadcastIntent.putExtra("message", message);
            broadcastIntent.putExtra("player", thisPlayer);
            sendBroadcast(broadcastIntent);
        } else if (message instanceof StatsMessageIn) {
            lastStatsMessage = (StatsMessageIn) message;
        } else if (message instanceof EventMessageIn) {
            lastReceivedEvent = (EventMessageIn) message;
        }
    }

    private void handleEventFromServer(WirelessMessage message) {
        switch (message.getType()) {
            case UdpMessages.YOU_HIT_SOMEONE -> soundManager.playYouHitSomeone();
            case UdpMessages.RESPAWN -> {
                soundManager.playRespawn();
                isGameRunning = true;
                thisPlayer.respawn();
            }
            case UdpMessages.GAME_OVER -> {
                soundManager.playGameOver();
                isGameRunning = false;
            }
            case UdpMessages.GAME_START -> soundManager.playGameStart();
            case UdpMessages.YOU_SCORED -> {
                soundManager.playYouScored();
                thisPlayer.setScore(thisPlayer.getScore() + 1);
            }
            case UdpMessages.FULL_STATS -> {
                var statsMessage = (StatsMessageIn) message;
                isGameRunning = statsMessage.isGameRunning();
                teamPlay = statsMessage.isTeamPlay();
                allPlayersSnapshot = statsMessage.getPlayers();
                Arrays.stream(allPlayersSnapshot)
                        .filter(p -> p.getId() == config.getPlayerId())
                        .findFirst()
                        .ifPresent(thisPlayer::copyFrom);
                sendCurrentStateToDevice();
            }
            case UdpMessages.GAME_TIMER -> {
                var timeMessage = (TimeMessage) message;
                gameTimerSeconds.set(timeMessage.getMinutes() * 60 + timeMessage.getSeconds());
                sendMessageToActivity(message, INTERCOM_TIME_TICK);
            }
        }
        sendMessageToActivity(message, INTERCOM_GAME_MESSAGE);
        if (message.getType() != UdpMessages.GAME_TIMER) {
            evaluateCurrentState();
        }
    }

    public void handleEventFromDevice(WirelessMessage message) {
        var type = message.getType();
        var otherPlayerId = ((EventMessageIn)message).getPayload();
        switch (message.getType()) {
            case UdpMessages.GUN_SHOT -> {
                soundManager.playGunShot();
                thisPlayer.decreaseBullets();
            }
            case UdpMessages.GUN_RELOAD -> {
                soundManager.playReload();
                thisPlayer.setBulletsLeft(thisPlayer.getMaxBullets());
            }
            case UdpMessages.GOT_HIT -> {
                var otherPlayer = getPlayerById(otherPlayerId);
                thisPlayer.decreaseHealth(otherPlayer.getDamage());
                if (thisPlayer.isAlive()) {
                    soundManager.playGotHit();
                } else {
                    soundManager.playYouKilled();
                    type = UdpMessages.YOU_KILLED;
                }
            }
            case UdpMessages.GUN_NO_BULLETS -> soundManager.playNoBullets();
        }
        udpClient.sendEventToServer(new EventMessageToServer(type,
                config.getPlayerId(),
                otherPlayerId,
                (byte) thisPlayer.getHealth(),
                (byte) thisPlayer.getScore(),
                (byte) thisPlayer.getTeamId()));
        sendMessageToActivity(new EventMessageIn(type, otherPlayerId), INTERCOM_GAME_MESSAGE);
    }

    private Player getPlayerById(int id) {
        return Arrays.stream(allPlayersSnapshot)
                .filter(p -> p.getId() == id)
                .findFirst()
                .orElse(null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // We don't need binding for this service
    }
}

