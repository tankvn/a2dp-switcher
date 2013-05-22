package com.googamaphone.utils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

public class BluetoothDeviceUtils {
    public static int getDeviceId(BluetoothDevice device) {
        return getDeviceId(device.getAddress());
    }

    public static int getDeviceId(String address) {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            return 0;
        }

        final String longAddress = address.replaceAll(":", "");
        return (int) (Long.parseLong(longAddress, 16));
    }
}
