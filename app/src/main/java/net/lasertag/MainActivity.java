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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MainActivity extends AppCompatActivity {

    public static final String SERVER_IP = "192.168.4.95";
    public static final int SERVER_PORT = 9877;
    public static final int PLAYER_ID = 1;
    public static final long HEARTBEAT_INTERVAL = 1000;

    private Handler heartbeatHandler = new Handler();

    private TextView playerName;
    private TextView playerHealth;
    private TextView playerScore;
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
        playersTable = findViewById(R.id.players_table);

        heartbeatHandler.postDelayed(this::heartbeat, HEARTBEAT_INTERVAL);
        startUDPListener();
    }

    private void heartbeat() {
        try {
            DatagramSocket socket = new DatagramSocket();
            byte[] message = new byte[] { 0, (byte) PLAYER_ID, 0 };
            DatagramPacket packet = new DatagramPacket(message, message.length, InetAddress.getByName(SERVER_IP), SERVER_PORT);
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
        heartbeatHandler.postDelayed(this::heartbeat, HEARTBEAT_INTERVAL);
    }

    private void startUDPListener() {
        new Thread(() -> {
            try (var socket = new DatagramSocket(9876)) {
                var buffer = new byte[1024];
                while (true) {
                    var packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    var message = new String(packet.getData(), 0, packet.getLength());
                    runOnUiThread(() -> handleIncomingMessage(message));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleIncomingMessage(String message) {
        // Assume message format is JSON or custom delimited string
        // Example: "PLAYER|John|100|1500;OTHERS|Jane,1400,90;Bob,1300,85;Alice,1250,95"
        String[] sections = message.split(";");
        for (String section : sections) {
            if (section.startsWith("PLAYER|")) {
                updatePlayerInfo(section);
            } else if (section.startsWith("OTHERS|")) {
                updateOtherPlayers(section);
            }
        }
    }

    private void updatePlayerInfo(String data) {
        // Format: "PLAYER|Name|Health|Score"
        String[] parts = data.split("\\|");
        if (parts.length == 4) {
            playerName.setText("Name: " + parts[1]);
            playerHealth.setText("Health: " + parts[2]);
            playerScore.setText("Score: " + parts[3]);
        }
    }

    private void updateOtherPlayers(String data) {
        // Clear existing rows except the header
        playersTable.removeViews(1, Math.max(0, playersTable.getChildCount() - 1));

        // Format: "OTHERS|Name,Score,Health;Name,Score,Health;..."
        String[] playersData = data.substring("OTHERS|".length()).split(";");
        for (String playerInfo : playersData) {
            String[] playerParts = playerInfo.split(",");
            if (playerParts.length == 3) {
                TableRow row = new TableRow(this);

                TextView nameText = new TextView(this);
                nameText.setText(playerParts[0]);
                nameText.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1));

                TextView scoreText = new TextView(this);
                scoreText.setText(playerParts[1]);
                scoreText.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1));

                TextView healthText = new TextView(this);
                healthText.setText(playerParts[2]);
                healthText.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1));

                row.addView(nameText);
                row.addView(scoreText);
                row.addView(healthText);

                playersTable.addView(row);
            }
        }
    }

}