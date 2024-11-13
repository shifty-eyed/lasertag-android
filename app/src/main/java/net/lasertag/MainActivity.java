package net.lasertag;

import static net.lasertag.Config.*;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;

import android.view.Window;
import android.view.WindowManager;

import net.lasertag.model.EventMessage;
import net.lasertag.model.Player;
import net.lasertag.model.StatsMessage;
import net.lasertag.model.TimeMessage;
import net.lasertag.model.UdpMessage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuppressLint({"SetTextI18n","InlinedApi","DefaultLocale"})
public class MainActivity extends AppCompatActivity {

    private final BroadcastReceiver udpMessageReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (Objects.requireNonNull(intent.getAction())) {
                case "CURRENT_STATE" -> {
                    int state = intent.getIntExtra("state", -1);
                    teamPlay = intent.getBooleanExtra("teamPlay", false);
                    handleStateChange(state);
                }
                case "UDP_MESSAGE_RECEIVED" -> {
                    UdpMessage message = (UdpMessage) intent.getSerializableExtra("message");
                    handleIncomingMessage(message);
                }
            }
        }
    };

    private Config config;
    private TextView playerName;
    private TextView playerHealth;
    private TextView playerScore;
    private TableLayout playersTable;
    private ConstraintLayout playerInfoLayout;
    private ConstraintLayout announcementLayout;
    private TextView announcementText;
    private TextView gameTime;
    private LinearLayout bulletsBar;
    private LinearLayout teamScoresBar;

    private volatile int currentState = -1;
    private volatile boolean teamPlay = false;

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "onConfigurationChanged");
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        config = new Config(this);
        Log.i(TAG, "onCreate");

        enableFullScreenMode();
        setContentView(R.layout.activity_main);

        playerName = findViewById(R.id.player_name);
        playerHealth = findViewById(R.id.player_health);
        playerScore = findViewById(R.id.player_score);
        playersTable = findViewById(R.id.players_table);
        playerInfoLayout = findViewById(R.id.player_info_layout);
        announcementLayout = findViewById(R.id.announcement_layout);
        announcementText = findViewById(R.id.announcement_text);
        gameTime = findViewById(R.id.game_timer);
        bulletsBar = findViewById(R.id.bullets_bar);
        teamScoresBar = findViewById(R.id.team_scores);

        startService(new Intent(this, NetworkService.class));
        registerReceiver(udpMessageReceiver, new IntentFilter("UDP_MESSAGE_RECEIVED"), Context.RECEIVER_EXPORTED);
        registerReceiver(udpMessageReceiver, new IntentFilter("CURRENT_STATE"), Context.RECEIVER_EXPORTED);
        handleStateChange(STATE_OFFLINE);
    }

    private void enableFullScreenMode() {
        EdgeToEdge.enable(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
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
        teamScoresBar.removeAllViews();
        Map<Byte, Integer> teamScores = new HashMap<>();

        for (Player player : message.getPlayers()) {
            teamScores.put(player.getTeamId(), teamScores.getOrDefault(player.getTeamId(), 0) + player.getScore());
            if (player.getId() == config.getPlayerId()) {
                playerName.setText(player.getName());
                playerName.setBackgroundColor(ResourcesCompat.getColor(getResources(), config.getTeamColor(player.getTeamId()), null));
                playerHealth.setText(String.valueOf(player.getHealth()));
                playerScore.setText(String.valueOf(player.getScore()));
            }
            TableRow row = new TableRow(this);
            row.setPadding(8, 8, 8, 8);
            row.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.table_row_border, null));
            row.setBackgroundColor(ResourcesCompat.getColor(getResources(), config.getTeamColor(player.getTeamId()), null));

            TextView nameText = new TextView(this);
            nameText.setText(player.getName());
            nameText.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 2));

            TextView scoreText = new TextView(this);
            scoreText.setText(String.valueOf(player.getScore()));
            scoreText.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1));

            TextView healthText = new TextView(this);
            healthText.setText(String.valueOf(player.getHealth()));
            healthText.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1));

            var textFields = Arrays.asList(nameText, scoreText, healthText);

            for (TextView text : textFields) {
                if (player.getHealth() <= 0) {
                    text.setPaintFlags(text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                }
                text.setPadding(8, 8, 8, 8);
                text.setTextColor(ResourcesCompat.getColor(getResources(), R.color.white, null));
                row.addView(text);
            }
            playersTable.addView(row);
        }

        if (teamPlay) {
            for (Map.Entry<Byte, Integer> e : teamScores.entrySet()) {
                TextView teamScore = new TextView(this);
                teamScore.setTextColor(ResourcesCompat.getColor(getResources(), R.color.white, null));
                teamScore.setPadding(16, 8, 8, 8);
                teamScore.setTextSize(30);
                teamScore.setText(String.valueOf(e.getValue()));
                teamScore.setBackgroundColor(ResourcesCompat.getColor(getResources(), config.getTeamColor(e.getKey()), null));
                teamScore.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
                teamScoresBar.addView(teamScore);
            }
        }
    }

    private void handleEvent(EventMessage message) {
        playerHealth.setText(String.valueOf(message.getHealth()));
        playerScore.setText(String.valueOf(message.getScore()));
        refreshBulletsBar(message.getBulletsLeft());
    }

    private void refreshBulletsBar(int bulletsLeft) {
        bulletsBar.removeAllViews();
        for (int i = 0; i < bulletsLeft; i++) {
            ImageView bullet = new ImageView(this);
            bullet.setImageResource(R.drawable.bullet);
            bullet.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            bulletsBar.addView(bullet);
        }
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