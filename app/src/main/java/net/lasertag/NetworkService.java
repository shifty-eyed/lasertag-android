package net.lasertag;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import net.lasertag.model.AckMessage;
import net.lasertag.model.EventMessage;
import net.lasertag.model.UdpMessage;
import net.lasertag.model.UdpMessages;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class NetworkService extends Service {

    private static final String TAG = "Lasertag";
    private static final long HEARTBEAT_INTERVAL = 2000;
    private DatagramSocket heartbeatSocket;
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service Starting");
        try {
            heartbeatSocket = new DatagramSocket();
            executorService.scheduleWithFixedDelay(this::heartbeat, 0, HEARTBEAT_INTERVAL, java.util.concurrent.TimeUnit.MILLISECONDS);
            startUDPListener();
        } catch (Exception e) {
            Log.e(TAG, "Service failed to start", e);
            stopSelf();
        }
        return START_STICKY;
    }

    private void heartbeat() {
        Log.i(TAG, "HB");
        try {
            byte[] message = new byte[] { UdpMessages.PING, (byte) MainActivity.PLAYER_ID, 0 };
            DatagramPacket packet = new DatagramPacket(message, 3, InetAddress.getByName(MainActivity.SERVER_IP), MainActivity.SERVER_PORT);
            heartbeatSocket.send(packet);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send heartbeat", e);
        }
    }

    private void startUDPListener() {
        executorService.execute(() -> {
            try (var socket = new DatagramSocket(MainActivity.SERVER_PORT)) {
                socket.setSoTimeout(1000);
                var buffer = new byte[512];
                Log.i(TAG, "Listening on socket: " + socket.getLocalSocketAddress());
                while (true) {
                    var packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(packet);
                        var message = UdpMessages.fromBytes(packet.getData(), packet.getLength());
                        Intent broadcastIntent = new Intent("UDP_MESSAGE_RECEIVED");
                        broadcastIntent.putExtra("message", message);
                        sendBroadcast(broadcastIntent);
                    } catch (SocketTimeoutException ignored) {
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to receive UDP message", e);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow();
        if (heartbeatSocket != null && !heartbeatSocket.isClosed()) {
            heartbeatSocket.close();
        }
        Log.i(TAG, "Service destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // We don't need binding for this service
    }
}

