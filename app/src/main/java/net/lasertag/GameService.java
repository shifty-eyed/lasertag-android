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
import net.lasertag.model.GameStartMessageIn;
import net.lasertag.model.WirelessMessage;

import static net.lasertag.Config.*;

import net.lasertag.model.EventMessageToServer;
import net.lasertag.model.MessageToDevice;
import net.lasertag.model.Player;
import net.lasertag.model.StatsMessageIn;
import net.lasertag.model.TimeMessage;
import net.lasertag.model.Messaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressLint("MissingPermission")
public class GameService extends Service {

    private Config config;
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(3);
    private SoundManager soundManager;

    private volatile boolean isActive = true;
    private volatile boolean isGameRunning = false;
    private volatile boolean isGameStartPending = false;
    private volatile boolean teamPlay = false;
    private volatile int currentState = -1;
    private volatile int respawnTimeoutSeconds = 0;

    private final AtomicInteger[] timerCounters = new AtomicInteger[] { new AtomicInteger(0), new AtomicInteger(0) };
    private static final int TIMER_GAME = 0;
    private static final int TIMER_RESPAWN = 1;


    private Player thisPlayer;
    private final List<Player> allPlayersSnapshot = new ArrayList<>();

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
        thisPlayer = new Player(config.getPlayerId());
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

            gunComm = new BluetoothClient(Config.GUN_DEVICE_NAME, BluetoothClient.DEVICE_GUN, bluetoothAdapter, this::handleEventFromDevice);
            vestComm = new BluetoothClient(Config.VEST_DEVICE_NAME, BluetoothClient.DEVICE_VEST, bluetoothAdapter, this::handleEventFromDevice);
            udpClient = new UdpClient(config, this::handleEventFromServer);

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
        if (!isGameStartPending && !isGameRunning) {
            return;
        }
        for (AtomicInteger gameTimer : timerCounters) {
            if (gameTimer.decrementAndGet() < 0) {
                gameTimer.set(0);
            }
        }
        var timerId = currentState == STATE_GAME ? TIMER_GAME : TIMER_RESPAWN;
        var minutes = (byte) (timerCounters[timerId].get() / 60);
        var seconds = (byte) (timerCounters[timerId].get() % 60);
        sendMessageToActivity(new TimeMessage(Messaging.GAME_TIMER, minutes, seconds), INTERCOM_TIME_TICK);
}

    // return true if state changed
    private boolean evaluateCurrentState() {
        var newState = -1;
        if (!udpClient.isOnline()) {
            newState = STATE_OFFLINE;
        } else if (!isGameRunning || isGameStartPending) {
            newState = STATE_IDLE;
        } else {
            newState = !thisPlayer.isAlive() ? STATE_DEAD : STATE_GAME;
        }
        if (newState != currentState) {
            currentState = newState;
            sendCurrentStateToActivity();
            sendCurrentStateToDevice();
            return true;
        } else {
            return false;
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
                Messaging.DEVICE_PLAYER_STATE,
                config.getPlayerId(),
                (byte)thisPlayer.getTeamId(),
                (byte)currentState,
                (byte)thisPlayer.getBulletsLeft());
        gunComm.sendMessageToDevice(message);
        vestComm.sendMessageToDevice(message);
    }

    private void sendMessageToActivity(WirelessMessage message, String action) {
        if (message == null || message.getType() == Messaging.PING) {
            return;
        }

        if (isActive) {
            Intent broadcastIntent = new Intent(action);
            if (message instanceof StatsMessageIn) {
                var statsMessage = (StatsMessageIn) message;
                statsMessage.setPlayers(allPlayersSnapshot.toArray(new Player[0]));
                broadcastIntent.putExtra("message", statsMessage);
            } else {
                broadcastIntent.putExtra("message", message);
            }
            broadcastIntent.putExtra("player", thisPlayer);
            sendBroadcast(broadcastIntent);
        } else if (message instanceof StatsMessageIn) {
            lastStatsMessage = (StatsMessageIn) message;
        } else if (message instanceof EventMessageIn) {
            lastReceivedEvent = (EventMessageIn) message;
        }
    }

    private void respawn() {
        soundManager.playRespawn();
        isGameRunning = true;
        isGameStartPending = false;
        thisPlayer.respawn();
        evaluateCurrentState();
        udpClient.sendEventToServer(new EventMessageToServer(Messaging.RESPAWN, thisPlayer,  0));
        sendMessageToActivity(new EventMessageIn(Messaging.RESPAWN, (byte)0), INTERCOM_GAME_MESSAGE);
    }

    private void handleEventFromServer(WirelessMessage message) {
        switch (message.getType()) {
            case Messaging.YOU_HIT_SOMEONE -> soundManager.playYouHitSomeone();
            case Messaging.GAME_OVER -> {
                soundManager.playGameOver();
                isGameRunning = false;
            }
            case Messaging.GAME_START -> {
                isGameStartPending = true;
                soundManager.playGameStart();
                var gameStartMessage = (GameStartMessageIn) message;
                teamPlay = gameStartMessage.getTeamPlay();
                respawnTimeoutSeconds = gameStartMessage.getRespawnTime();
                timerCounters[TIMER_GAME].set(gameStartMessage.getGameTimeMinutes() * 60 + gameStartMessage.getStartDelaySeconds());
                timerCounters[TIMER_RESPAWN].set(gameStartMessage.getStartDelaySeconds());
                executorService.schedule(this::respawn, gameStartMessage.getStartDelaySeconds(), java.util.concurrent.TimeUnit.SECONDS);
            }
            case Messaging.YOU_SCORED -> {
                soundManager.playYouScored();
                thisPlayer.setScore(thisPlayer.getScore() + 1);
            }
            case Messaging.PLAYER_VALUES_SNAPSHOT -> {
                var statsMessage = (StatsMessageIn) message;
                isGameRunning = statsMessage.isGameRunning();
                teamPlay = statsMessage.isTeamPlay();
                timerCounters[TIMER_GAME].set(statsMessage.getGameTimerSeconds());
                //update all players
                for (var playerUpdates : statsMessage.getPlayers()) {
                    if (!allPlayersSnapshot.contains(playerUpdates)) {
                        allPlayersSnapshot.add(playerUpdates);
                    } else {
                        for (var existingPlayer : allPlayersSnapshot) {
                            if (existingPlayer.getId() == playerUpdates.getId()) {
                                existingPlayer.copyPlayerValuesFrom(playerUpdates);
                            }
                        }
                    }
                }
                Collections.sort(allPlayersSnapshot);
                var myData = getPlayerById(config.getPlayerId());
                if (myData != null) {
                    thisPlayer.copyPlayerValuesFrom(myData);
                }
            }
        }
        sendMessageToActivity(message, INTERCOM_GAME_MESSAGE);
        if (!evaluateCurrentState()) {//send state anyway
            sendCurrentStateToDevice();
        }
    }

    public void handleEventFromDevice(WirelessMessage message) {
        var type = message.getType();
        var extraValue = ((EventMessageIn)message).getPayload();
        switch (message.getType()) {
            case Messaging.DEVICE_CONNECTED -> sendCurrentStateToDevice();
            // Messaging.DEVICE_DISCONNECTED has no action, just propagate to activity
            case Messaging.GUN_SHOT -> {
                if (thisPlayer.getBulletsLeft() > 0) {
                    soundManager.playGunShot();
                    thisPlayer.decreaseBullets();
                } else {
                    soundManager.playNoBullets();
                    type = Messaging.GUN_NO_BULLETS;
                }
            }
            case Messaging.GUN_RELOAD -> {
                soundManager.playReload();
                thisPlayer.reload();
            }
            case Messaging.GOT_HIT -> {
                //Assumed that other player has bullets > 0, not dead, game started
                var otherPlayer = getPlayerById(extraValue);
                if (otherPlayer.getTeamId() == thisPlayer.getTeamId()) {
                    //maybe play Friendly fire
                    return;
                }
                thisPlayer.decreaseHealth(otherPlayer.getDamage());
                if (thisPlayer.isAlive()) {
                    soundManager.playGotHit();
                } else {
                    soundManager.playYouKilled();
                    evaluateCurrentState();
                    type = Messaging.YOU_KILLED;
                    timerCounters[TIMER_RESPAWN].set(respawnTimeoutSeconds);
                    executorService.schedule(this::respawn, respawnTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
                }
            }
        }
        if (type != Messaging.GUN_NO_BULLETS) {
            udpClient.sendEventToServer(new EventMessageToServer(type, thisPlayer, extraValue));
        }
        sendMessageToActivity(new EventMessageIn(type, extraValue), INTERCOM_GAME_MESSAGE);
    }

    private Player getPlayerById(int id) {
        return allPlayersSnapshot.stream()
                .filter(p -> p.getId() == id)
                .findFirst()
                .orElse(null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // We don't need binding for this service
    }
}
