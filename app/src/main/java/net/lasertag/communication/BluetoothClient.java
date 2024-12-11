package net.lasertag.communication;

import static net.lasertag.Config.SERVICE_UUID;
import static net.lasertag.Config.TAG;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import net.lasertag.model.EventMessageIn;
import net.lasertag.model.SignalMessage;
import net.lasertag.model.WirelessMessage;
import net.lasertag.model.Messaging;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("MissingPermission")
public class BluetoothClient {

    public static final byte DEVICE_GUN = 1;
    public static final byte DEVICE_VEST = 2;

    private static final byte STOP_BYTE = 125;

    private final WirelessMessageHandler messageHandler;
    private final String deviceName;
    private final byte deviceType;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private volatile boolean running = false;

    private BluetoothDevice espDevice;

    public BluetoothClient(String deviceName, int deviceType, BluetoothAdapter bluetoothAdapter, WirelessMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
        this.deviceName = deviceName;
        this.bluetoothAdapter = bluetoothAdapter;
        this.deviceType = (byte)deviceType;
        running = true;
        executorService.execute(this::loop);
    }

    public void stop() {
        running = false;
        try {
            executorService.shutdown();
            bluetoothSocket.close();
            outputStream.close();
            inputStream.close();
            outputStream = null;
            inputStream = null;
        } catch (IOException ignored) {}
    }

    public void sendMessageToDevice(WirelessMessage message) {
        if (outputStream != null) {
            try {
                outputStream.write(message.getBytes());
                outputStream.write(STOP_BYTE);
                outputStream.flush();
                if (message.getType() != Messaging.PING) {
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
                messageHandler.handleWirelessEvent(new EventMessageIn(Messaging.DEVICE_CONNECTED, deviceType));
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
                    } else if (buffer.get(0) == Messaging.PING) {
                        sendMessageToDevice(new SignalMessage());
                    } else {
                        Log.i(TAG, "Handling message: " + buffer);
                        messageHandler.handleWirelessEvent(Messaging.parseMessageFromDevice(buffer));
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
