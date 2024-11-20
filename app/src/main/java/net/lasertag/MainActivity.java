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
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;
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
import net.lasertag.model.UdpMessages;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@SuppressLint({"SetTextI18n","InlinedApi","DefaultLocale"})
public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    /*

    - better sounds, game start and over
    - fix bug with delayed leader announcement causing delayed table update

    */

    private final BroadcastReceiver udpMessageReceiver = new BroadcastReceiver() {

        @Override
        public synchronized void onReceive(Context context, Intent intent) {
            switch (Objects.requireNonNull(intent.getAction())) {
                case "CURRENT_STATE" -> {
                    var state = intent.getIntExtra("state", -1);
                    teamPlay = intent.getBooleanExtra("teamPlay", false);
                    if (state != currentState) {
                        currentState = state;
                        if (!toasterOn) {
                            onRefreshUIGameSate();
                        }
                    }
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

    private TextToSpeech textToSpeech;

    private static final String[] uhVariants = new String[] {"uh!", "ouch!", "ah!", "oh!", "oi!"};
    private static final String[] teamNames = new String[] {"Red", "Blue", "Green", "Yellow", "Purple", "Cyan"};
    private int lastUhVariant = 0;
    private volatile int currentState = -1;
    private volatile boolean teamPlay = false;
    private volatile Player[] players = new Player[0];
    private volatile boolean toasterOn = false;
    private int lastLeader = -1;

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "onConfigurationChanged");
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        config = new Config(this);
        textToSpeech = new TextToSpeech(this, this);

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
        currentState = STATE_OFFLINE;
        onRefreshUIGameSate();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.US);
            textToSpeech.setSpeechRate(0.7f);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported");
            }
        } else {
            Log.e(TAG, "Initialization failed");
        }
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

    private void showToasterMessage(String message, long delayMillis) {
        toasterOn = true;
        showAnnouncementLayout(true);
        announcementText.setText(message);
        new Handler().postDelayed(
                () -> textToSpeech.speak(message, TextToSpeech.QUEUE_ADD, null, null), 1000);
        new Handler().postDelayed(this::onRefreshUIGameSate, delayMillis);
    }

    private void onRefreshUIGameSate() {
        toasterOn = false;
        switch (currentState) {
            case STATE_GAME -> showAnnouncementLayout(false);
            case STATE_IDLE -> {
                showAnnouncementLayout(true);
                announcementText.setText("Game is not started.");
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
    }

    private void handleEvent(EventMessage message) {
        playerHealth.setText(String.valueOf(message.getHealth()));
        playerScore.setText(String.valueOf(message.getScore()));
        refreshBulletsBar(message.getBulletsLeft());
        var otherPlayer = getPlayerById(message.getCounterpartPlayerId());
        var otherName = otherPlayer != null ? otherPlayer.getName() : "someone";
        switch (message.getType()) {
            case UdpMessages.GAME_START -> lastLeader = -1;
            case UdpMessages.GOT_HIT -> {
                var uhVariant = lastUhVariant;
                while (uhVariant == lastUhVariant) {
                    uhVariant = (int) (Math.random() * uhVariants.length);
                }
                textToSpeech.speak(uhVariants[uhVariant], TextToSpeech.QUEUE_ADD, null, null);
                lastUhVariant = uhVariant;
            }
            case UdpMessages.RESPAWN -> showToasterMessage("Play!", 2000);
            case UdpMessages.YOU_KILLED -> showToasterMessage(otherName + " killed you.", 3000);
            case UdpMessages.YOU_SCORED -> showToasterMessage("You killed " + otherName, 2000);
            case UdpMessages.GAME_OVER -> {
                if (message.getCounterpartPlayerId() == config.getPlayerId()) {
                    showToasterMessage("You win!", 4000);
                } else {
                    showToasterMessage("Game Over!\n" + (otherPlayer == null ? "No one" : otherPlayer.getName()) + " wins.", 4000);
                }
            }
        }
    }

    private void handleTime(TimeMessage message) {
        if (!toasterOn) {
            switch (currentState) {
                case STATE_IDLE -> announcementText.setText(String.format("Start in %d...", message.getSeconds()));
                case STATE_GAME -> gameTime.setText(String.format("%02d:%02d", message.getMinutes(), message.getSeconds()));
                case STATE_DEAD -> announcementText.setText(String.format("You are dead.\nRespawn in %d...", message.getSeconds()));
            }
            if ((currentState == STATE_IDLE || currentState == STATE_DEAD)
                    && message.getMinutes() == 0 && message.getSeconds() < 4 && message.getSeconds() > 0) {
                textToSpeech.speak(String.valueOf(message.getSeconds()), TextToSpeech.QUEUE_ADD, null, null);
            }
        }
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

    private void announceLeaderChange(Player[] newPlayers) {
        if (players.length == 0) {
            return;
        }
        if (teamPlay) {
            var teamScores = getTeamScores(players);
            var leaderTeam = Collections.max(teamScores.entrySet(), Map.Entry.comparingByValue());
            var countOfLeaders = (int) teamScores.values().stream().filter(s -> s.equals(leaderTeam.getValue())).count();
            var newLeaderId = countOfLeaders == 1 ? leaderTeam.getKey() : -1;
            if (lastLeader != newLeaderId) {
                var message = (newLeaderId == -1 ? "Teams are tie!" : teamNames[newLeaderId - 1] + " team leads!");
                new Handler().postDelayed(() -> textToSpeech.speak(message, TextToSpeech.QUEUE_ADD, null, null), toasterOn ? 2000 : 100);
            }
            lastLeader = newLeaderId;
        } else {
            var maxScore = newPlayers[0].getScore();
            var countOfLeaders = (int) Arrays.stream(newPlayers).filter(p -> p.getScore() == maxScore).count();
            var newLeaderId = countOfLeaders == 1 ? newPlayers[0].getId() : -1;
            if (lastLeader != newLeaderId) {
                var message = (newLeaderId == -1 ? "You are tie!" :
                        (newLeaderId == config.getPlayerId()
                                ? "You are"
                                : Objects.requireNonNull(getPlayerById(newLeaderId)).getName() + " is") + " the new leader!");
                new Handler().postDelayed(() -> textToSpeech.speak(message, TextToSpeech.QUEUE_ADD, null, null), toasterOn ? 2000 : 100);
            }
            lastLeader = newLeaderId;
        }
    }

    private Map<Byte, Integer> getTeamScores(Player[] players) {
        Map<Byte, Integer> teamScores = new HashMap<>();
        for (Player player : players) {
            teamScores.put(player.getTeamId(), teamScores.getOrDefault(player.getTeamId(), 0) + player.getScore());
        }
        return teamScores;
    }

    private void updatePlayersInfo(StatsMessage message) {
        playersTable.removeViews(1, Math.max(0, playersTable.getChildCount() - 1));
        teamScoresBar.removeAllViews();
        for (Player player : message.getPlayers()) {
            if (player.getId() == config.getPlayerId()) {
                playerName.setText(player.getName());
                playerName.setBackgroundColor(ResourcesCompat.getColor(getResources(), config.getTeamColor(player.getTeamId(), true), null));
                playerHealth.setText(String.valueOf(player.getHealth()));
                playerScore.setText(String.valueOf(player.getScore()));
            }
            TableRow row = new TableRow(this);
            row.setPadding(8, 8, 8, 8);
            row.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.table_row_border, null));
            row.setBackgroundColor(ResourcesCompat.getColor(getResources(), config.getTeamColor(player.getTeamId(), true), null));

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
                text.setPadding(8, 8, 8, 8);
                if (player.getHealth() <= 0) {
                    text.setPaintFlags(text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    text.setTextColor(ResourcesCompat.getColor(getResources(), R.color.black, null));
                } else {
                    text.setTextColor(ResourcesCompat.getColor(getResources(), R.color.white, null));
                }
                row.addView(text);
            }
            playersTable.addView(row);
        }
        if (teamPlay) {
            for (Map.Entry<Byte, Integer> e : getTeamScores(message.getPlayers()).entrySet()) {
                TextView teamScore = new TextView(this);
                teamScore.setTextColor(ResourcesCompat.getColor(getResources(), R.color.white, null));
                teamScore.setPadding(16, 8, 8, 8);
                teamScore.setTextSize(30);
                teamScore.setText(String.valueOf(e.getValue()));
                teamScore.setBackgroundColor(ResourcesCompat.getColor(getResources(), config.getTeamColor(e.getKey(), true), null));
                teamScore.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
                teamScoresBar.addView(teamScore);
            }
        }
        announceLeaderChange(message.getPlayers());
        players = message.getPlayers();
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

    private Player getPlayerById(byte id) {
        for (Player player : players) {
            if (player.getId() == id) {
                return player;
            }
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy called");
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
        unregisterReceiver(udpMessageReceiver);
    }

}