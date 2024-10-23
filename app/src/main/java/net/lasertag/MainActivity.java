package net.lasertag;

import android.os.Bundle;
import android.os.Handler;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.view.Window;
import android.view.WindowManager;

import net.lasertag.model.EventMessage;
import net.lasertag.model.Player;
import net.lasertag.model.StatsMessage;
import net.lasertag.model.UdpMessage;
import net.lasertag.model.UdpMessages;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    public static final String SERVER_IP = "192.168.4.95";
    public static final int SERVER_PORT = 9877;
    public static final int PLAYER_ID = 1;
    public static final long HEARTBEAT_INTERVAL = 1000;

    private final Handler heartbeatHandler = new Handler();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private TextView playerName;
    private TextView playerHealth;
    private TextView playerScore;
    private TextView bulletsLeft;
    private TableLayout playersTable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        playerName = findViewById(R.id.player_name);
        playerHealth = findViewById(R.id.player_health);
        playerScore = findViewById(R.id.player_score);
        bulletsLeft = findViewById(R.id.bullets);
        playersTable = findViewById(R.id.players_table);

        heartbeatHandler.postDelayed(this::heartbeat, HEARTBEAT_INTERVAL);
        startUDPListener();
    }

    private void heartbeat() {
        try (var socket = new DatagramSocket()){
            byte[] message = new byte[] { 0, (byte) PLAYER_ID, 0 };
            DatagramPacket packet = new DatagramPacket(message, message.length, InetAddress.getByName(SERVER_IP), SERVER_PORT);
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
        heartbeatHandler.postDelayed(this::heartbeat, HEARTBEAT_INTERVAL);
    }

    private void startUDPListener() {
        executorService.execute(() -> {
            try (var socket = new DatagramSocket(9876)) {
                var buffer = new byte[1024];
                while (true) {
                    var packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    var message = UdpMessages.fromBytes(packet.getData(), packet.getLength());
                    runOnUiThread(() -> handleIncomingMessage(message));
                }
            } catch (Exception e) {
                e.printStackTrace();
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
        playerHealth.setText(message.getHealth());
        playerScore.setText(message.getScore());
        bulletsLeft.setText(message.getBulletsLeft());
        switch (message.getType()) {
            case UdpMessages.GUN_SHOT -> {
                // play gunshot sound

            }
        }
    }


}