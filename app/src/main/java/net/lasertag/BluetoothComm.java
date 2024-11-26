package net.lasertag;

import static net.lasertag.Config.SERVICE_UUID;
import static net.lasertag.Config.TAG;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import net.lasertag.model.PingMessage;
import net.lasertag.model.UdpMessage;
import net.lasertag.model.UdpMessages;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("MissingPermission")
public class BluetoothComm {

    private static final byte STOP_BYTE = 125;

    private final BluetoothMessageHandler messageHandler;
    private final String deviceName;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private volatile boolean running = false;

    private BluetoothDevice espDevice;

    public BluetoothComm(String deviceName, BluetoothAdapter bluetoothAdapter, BluetoothMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
        this.deviceName = deviceName;
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public void start() {
        if (!running) {
            running = true;
            executorService.execute(this::loop);
        }
    }

    public void stop() {
        running = false;
        try {
            bluetoothSocket.close();
            outputStream.close();
            inputStream.close();
            outputStream = null;
            inputStream = null;
        } catch (IOException ignored) {}
    }

    public void sendMessageToDevice(UdpMessage message) {
        if (outputStream != null) {
            try {
                outputStream.write(message.getBytes());
                outputStream.write(STOP_BYTE);
                outputStream.flush();
                if (message.getType() != UdpMessages.PING) {
                    Log.i(TAG, "Sent to " + deviceName + ": " + message);
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to send message to " + deviceName);
            }
        }
    }

    private void loop() {
        while (running) {
            if (connectToBluetoothDevice()) {
                sendMessageToDevice(new PingMessage());
                listening();
            }
            try {
                Thread.yield();
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {}
        }
    }

    private boolean connectToBluetoothDevice() {
        Log.i(TAG, "Connecting to Bluetooth device " + deviceName);
        if (!bluetoothAdapter.isEnabled()) {
            Log.i(TAG, "Bluetooth is not enabled");
            return false;
        }
        if (espDevice == null) {
            for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                if (deviceName.equals(device.getName())) {
                    Log.i(TAG, "Found: " + device.getName());
                    espDevice = device;
                    break;
                }
            }
        }

        if (espDevice == null) {
            Log.i(TAG, "Device not found");
            return false;
        }

        try {
            bluetoothSocket = espDevice.createRfcommSocketToServiceRecord(SERVICE_UUID);
            bluetoothSocket.connect();
            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();
            Log.i(TAG, "Connected to device: " + deviceName);
        } catch (IOException e) {
            Log.w(TAG, "Failed to connect to ESP32 device: " + deviceName);
            return false;
        }
        return true;
    }

    private void listening() {
        Log.i(TAG, "Listening to Bluetooth");
        List<Byte> buffer = new java.util.ArrayList<>(16);
        while (running) {
            try {
                buffer.clear();
                while (true) {
                    byte data = (byte)inputStream.read();
                    if (data == STOP_BYTE) {
                        break;
                    }
                    buffer.add(data);
                }
                if (!buffer.isEmpty()) {
                    if (buffer.size() != 2) {
                        Log.i(TAG, "Got something wrong: " + buffer);
                    } else if (buffer.get(0) == UdpMessages.PING) {
                        sendMessageToDevice(new PingMessage());
                    } else {
                        Log.i(TAG, "Handling message: " + buffer);
                        messageHandler.handleBluetoothMessage(UdpMessages.parseMessageFromDevice(buffer));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Connection lost", e);
                try {
                    bluetoothSocket.close();
                    outputStream = null;
                    inputStream = null;
                } catch (IOException ignored) {}
                return;
            }
        }
    }
}
