package net.lasertag;

import static net.lasertag.NetworkService.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.view.Window;
import android.view.WindowManager;

import net.lasertag.model.EventMessage;
import net.lasertag.model.Player;
import net.lasertag.model.StatsMessage;
import net.lasertag.model.TimeMessage;
import net.lasertag.model.UdpMessage;
import net.lasertag.model.UdpMessages;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Lasertag";
    public static final int PLAYER_ID = 1;

    private final BroadcastReceiver udpMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case "CURRENT_STATE" -> {
                    int state = intent.getIntExtra("state", -1);
                    handleStateChange(state);
                }
                case "UDP_MESSAGE_RECEIVED" -> {
                    UdpMessage message = (UdpMessage) intent.getSerializableExtra("message");
                    handleIncomingMessage(message);
                }
            }
        }
    };

    private PowerManager.WakeLock wakeLock;
    private TextView playerName;
    private TextView playerHealth;
    private TextView playerScore;
    private TextView bulletsLeft;
    private TableLayout playersTable;
    private ConstraintLayout playerInfoLayout;
    private ConstraintLayout announcementLayout;
    private TextView announcementText;
    private TextView gameTime;

    private volatile int currentState = -1;

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "onConfigurationChanged");
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        EdgeToEdge.enable(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        playerName = findViewById(R.id.player_name);
        playerHealth = findViewById(R.id.player_health);
        playerScore = findViewById(R.id.player_score);
        bulletsLeft = findViewById(R.id.bullets);
        playersTable = findViewById(R.id.players_table);
        playerInfoLayout = findViewById(R.id.player_info_layout);
        announcementLayout = findViewById(R.id.announcement_layout);
        announcementText = findViewById(R.id.announcement_text);
        gameTime = findViewById(R.id.game_timer);

        startService(new Intent(this, NetworkService.class));
        registerReceiver(udpMessageReceiver, new IntentFilter("UDP_MESSAGE_RECEIVED"));
        registerReceiver(udpMessageReceiver, new IntentFilter("CURRENT_STATE"));
        handleStateChange(STATE_OFFLINE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        sendBroadcast(new Intent("ACTIVITY_RESUMED"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        sendBroadcast(new Intent("ACTIVITY_PAUSED"));
    }

    private void showAnnouncementLayout(boolean show) {
        announcementLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        playerInfoLayout.setVisibility(!show ? View.VISIBLE : View.GONE);
    }

    private void handleStateChange(int newState) {
        if (newState == currentState) {
            return;
        }
        switch (newState) {
            case STATE_IDLE -> {
                showAnnouncementLayout(true);
                announcementText.setText("Game is not started.");
            }
            case STATE_GAME -> {
                showAnnouncementLayout(false);
            }
            case STATE_DEAD -> {
                showAnnouncementLayout(true);
                announcementText.setText("You are dead.");
            }
            case STATE_OFFLINE -> {
                showAnnouncementLayout(true);
                announcementText.setText("Offline");
            }
        }
        currentState = newState;
    }

    private void handleIncomingMessage(UdpMessage message) {
        if (message instanceof StatsMessage) {
            runOnUiThread(() -> updatePlayersInfo((StatsMessage) message));
        } else if (message instanceof EventMessage) {
            handleEvent((EventMessage) message);
        } else if (message instanceof TimeMessage) {
            handleTime((TimeMessage) message);
        }//AckMessage ignored
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
    }

    private void handleTime(TimeMessage message) {
        switch (currentState) {
            case STATE_IDLE -> announcementText.setText(String.format("Start in %d...", message.getSeconds()));
            case STATE_GAME -> gameTime.setText(String.format("%02d:%02d", message.getMinutes(), message.getSeconds()));
            case STATE_DEAD -> announcementText.setText(String.format("You are dead.\nRespawn in %d...", message.getSeconds()));
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy called");
        super.onDestroy();
        unregisterReceiver(udpMessageReceiver);
    }

}