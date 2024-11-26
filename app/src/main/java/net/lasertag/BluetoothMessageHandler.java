package net.lasertag;

import net.lasertag.model.MessageFromDevice;

import java.io.IOException;

public interface BluetoothMessageHandler {

    void handleBluetoothMessage(MessageFromDevice message) throws IOException;
}
