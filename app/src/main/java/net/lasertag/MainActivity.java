package net.lasertag;

import android.os.Bundle;
import android.util.Log;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import android.view.Window;
import android.view.WindowManager;

import net.lasertag.model.AckMessage;
import net.lasertag.model.EventMessage;
import net.lasertag.model.Player;
import net.lasertag.model.StatsMessage;
import net.lasertag.model.UdpMessage;
import net.lasertag.model.UdpMessages;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "LasertagMain";
    public static final String SERVER_IP = "192.168.4.95";
    public static final int SERVER_PORT = 9878;
    public static final int PLAYER_ID = 1;
    public static final long HEARTBEAT_INTERVAL = 2000;

    private DatagramSocket heartbeatSocket;
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(3);

    private TextView playerName;
    private TextView playerHealth;
    private TextView playerScore;
    private TextView bulletsLeft;
    private TableLayout playersTable;

    private SoundManager soundManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        playerName = findViewById(R.id.player_name);
        playerHealth = findViewById(R.id.player_health);
        playerScore = findViewById(R.id.player_score);
        bulletsLeft = findViewById(R.id.bullets);
        playersTable = findViewById(R.id.players_table);

        soundManager = new SoundManager(this);

        try {
            heartbeatSocket = new DatagramSocket();
            executorService.scheduleWithFixedDelay(this::heartbeat, 0, HEARTBEAT_INTERVAL, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        startUDPListener();
    }

    private void heartbeat() {
        try {

            byte[] message = new byte[] { UdpMessages.PING, (byte) PLAYER_ID, 0 };
            DatagramPacket packet = new DatagramPacket(message, 3, InetAddress.getByName(SERVER_IP), SERVER_PORT);
            heartbeatSocket.send(packet);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send heartbeat", e);
        }
    }

    private void startUDPListener() {
        executorService.execute(() -> {
            try (var socket = new DatagramSocket(1234)) {
                socket.setSoTimeout(1000);
                var buffer = new byte[512];
                Log.i(TAG, "Listening socket: "+socket.getLocalSocketAddress());
                while (true) {
                    Thread.yield();
                    var packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(packet);
                    } catch (SocketTimeoutException e) {
                        continue;
                    }

                    var message = UdpMessages.fromBytes(packet.getData(), packet.getLength());
                    if (message instanceof AckMessage) {
                        //TODO: update last ping time
                    } else {
                        var truncatedData = Arrays.copyOf(packet.getData(), packet.getLength());
                        Log.i(TAG, "Got packet: "+ Arrays.toString(truncatedData));
                        runOnUiThread(() -> handleIncomingMessage(message));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to receive UDP message", e);
            }
        });
    }

    private void handleIncomingMessage(UdpMessage message) {
        if (message.getType() == UdpMessages.FULL_STATS) {
            updatePlayersInfo((StatsMessage) message);
        } else {
            handleEvent((EventMessage) message);
        }
    }

    private void updatePlayersInfo(StatsMessage message) {
        playersTable.removeViews(1, Math.max(0, playersTable.getChildCount() - 1));
        for (Player player : message.getPlayers()) {
            if (player.getId() == PLAYER_ID) {
                playerName.setText(player.getName());
                playerHealth.setText(player.getHealth());
                playerScore.setText(player.getScore());
            }
            TableRow row = new TableRow(this);

            TextView nameText = new TextView(this);
            nameText.setText(player.getName());
            nameText.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1));

            TextView scoreText = new TextView(this);
            scoreText.setText(player.getScore());
            scoreText.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1));

            TextView healthText = new TextView(this);
            healthText.setText(player.getHealth());
            healthText.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1));

            row.addView(nameText);
            row.addView(scoreText);
            row.addView(healthText);

            playersTable.addView(row);
        }
    }

    private void handleEvent(EventMessage message) {
        playerHealth.setText(String.valueOf(message.getHealth()));
        playerScore.setText(String.valueOf(message.getScore()));
        bulletsLeft.setText(String.valueOf(message.getBulletsLeft()));
        switch (message.getType()) {
            case UdpMessages.GUN_SHOT -> soundManager.playGunShot();
            case UdpMessages.GOT_HIT -> soundManager.playGotHit();
            case UdpMessages.YOU_HIT_SOMEONE -> soundManager.playYouHitSomeone();
            case UdpMessages.GUN_NO_BULLETS -> soundManager.playNoBullets();
            case UdpMessages.GUN_RELOAD -> soundManager.playReload();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        soundManager.release();
    }


}