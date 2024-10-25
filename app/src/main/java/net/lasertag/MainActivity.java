package net.lasertag;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
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

    private static final String TAG = "Lasertag";
    public static final String SERVER_IP = "192.168.4.95";
    public static final int SERVER_PORT = 9878;
    public static final int PLAYER_ID = 1;

    private final BroadcastReceiver udpMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            UdpMessage message = (UdpMessage) intent.getSerializableExtra("message");
            handleIncomingMessage(message);
        }
    };

    private TextView playerName;
    private TextView playerHealth;
    private TextView playerScore;
    private TextView bulletsLeft;
    private TableLayout playersTable;

    private SoundManager soundManager;

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "onConfigurationChanged");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
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

        Intent serviceIntent = new Intent(this, NetworkService.class);
        startService(serviceIntent);
        registerReceiver(udpMessageReceiver, new IntentFilter("UDP_MESSAGE_RECEIVED"));
    }

    private void handleIncomingMessage(UdpMessage message) {
        if (message instanceof StatsMessage) {
            runOnUiThread(() -> updatePlayersInfo((StatsMessage) message));
        } else if (message instanceof EventMessage) {
            handleEvent((EventMessage) message);
        } //AckMessage ignored
    }

    private void updatePlayersInfo(StatsMessage message) {
        playersTable.removeViews(1, Math.max(0, playersTable.getChildCount() - 1));
        for (Player player : message.getPlayers()) {
            if (player.getId() == PLAYER_ID) {
                playerName.setText(player.getName());
                playerHealth.setText(String.valueOf(player.getHealth()));
                playerScore.setText(String.valueOf(player.getScore()));
            }
            TableRow row = new TableRow(this);

            TextView nameText = new TextView(this);
            nameText.setText(player.getName());
            nameText.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1));

            TextView scoreText = new TextView(this);
            scoreText.setText(String.valueOf(player.getScore()));
            scoreText.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1));

            TextView healthText = new TextView(this);
            healthText.setText(String.valueOf(player.getHealth()));
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
            case UdpMessages.GUN_RELOAD -> soundManager.playReload();
            case UdpMessages.YOU_HIT_SOMEONE -> soundManager.playYouHitSomeone();
            case UdpMessages.GOT_HIT -> soundManager.playGotHit();
            case UdpMessages.RESPAWN -> soundManager.playRespawn();
            case UdpMessages.GAME_OVER -> soundManager.playGameOver();
            case UdpMessages.GAME_START -> soundManager.playGameStart();
            case UdpMessages.YOU_KILLED -> soundManager.playYouKilled();
            case UdpMessages.YOU_SCORED -> soundManager.playYouScored();
            case UdpMessages.GUN_NO_BULLETS -> soundManager.playNoBullets();

        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy called");
        super.onDestroy();
        unregisterReceiver(udpMessageReceiver);
        soundManager.release();
    }

}