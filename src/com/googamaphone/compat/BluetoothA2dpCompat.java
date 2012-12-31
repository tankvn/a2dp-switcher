/*
 * Copyright (C) 2013 Alan Viverette
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googamaphone.compat;

import java.util.List;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Build;

public class BluetoothA2dpCompat {
    static interface BluetoothA2dpVersionImpl {
        public boolean obtain(Context context, BluetoothA2dpStubCallback callback);
        public boolean connect(Object receiver, BluetoothDevice device);
        public boolean disconnect(Object receiver, BluetoothDevice device);
        public List<BluetoothDevice> getDevicesMatchingConnectionStates(Object receiver, int[] states);
        public int getConnectionState(Object receiver, BluetoothDevice device);
        public void shutdown(Object receiver);
        public String getStateChangedAction();
        public String getExtraState();
        public String getExtraPreviousState();
    }

    static class BluetoothA2dpStubImpl implements BluetoothA2dpVersionImpl {
        @Override
        public boolean obtain(Context context, BluetoothA2dpStubCallback callback) {
            return false;
        }

        @Override
        public boolean connect(Object receiver, BluetoothDevice device) {
            return false;
        }

        @Override
        public boolean disconnect(Object receiver, BluetoothDevice device) {
            return false;
        }

        @Override
        public List<BluetoothDevice> getDevicesMatchingConnectionStates(Object receiver, int[] states) {
            return null;
        }

        @Override
        public int getConnectionState(Object receiver, BluetoothDevice device) {
            return -1;
        }

        @Override
        public void shutdown(Object receiver) { }

        @Override
        public String getStateChangedAction() {
            return null;
        }

        @Override
        public String getExtraState() {
            return null;
        }

        @Override
        public String getExtraPreviousState() {
            return null;
        }
    }

    private static final BluetoothA2dpVersionImpl IMPL;

    public static final String ACTION_CONNECTION_STATE_CHANGED;
    public static final String EXTRA_STATE;
    public static final String EXTRA_PREVIOUS_STATE;

    static {
        if (Build.VERSION.SDK_INT >= 11) {
            IMPL = new BluetoothA2dpHoneycombImpl();
        } else if (Build.VERSION.SDK_INT >= 9) {
            IMPL = new BluetoothA2dpGingerbreadImpl();
        } else {
            IMPL = new BluetoothA2dpStubImpl();
        }

        ACTION_CONNECTION_STATE_CHANGED = IMPL.getStateChangedAction();
        EXTRA_STATE = IMPL.getExtraState();
        EXTRA_PREVIOUS_STATE = IMPL.getExtraPreviousState();
    }

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_DISCONNECTING = 3;
    public static final int STATE_PLAYING = 10;
    public static final int STATE_NOT_PLAYING = 11;

    public static boolean obtain(Context context, final BluetoothA2dpCompatCallback callback) {
        if ((context == null) || (callback == null)) {
            throw new IllegalArgumentException();
        }

        final BluetoothA2dpStubCallback stubCallback = new BluetoothA2dpStubCallback() {
            @Override
            public void onProxyDisconnected() {
                callback.onProxyDisconnected();
            }

            @Override
            public void onProxyConnected(Object receiver) {
                callback.onProxyConnected(new BluetoothA2dpCompat(receiver));
            }
        };

        return IMPL.obtain(context, stubCallback);
    }

    private final Object mReceiver;

    private BluetoothA2dpCompat(Object receiver) {
        mReceiver = receiver;
    }

    /**
     * Initiate connection to a profile of the remote bluetooth device.
     * <p>
     * Currently, the system supports only 1 connection to the A2DP profile. The
     * API will automatically disconnect connected devices before connecting.
     * <p>
     * This API returns false in scenarios like the profile on the device is
     * already connected or Bluetooth is not turned on. When this API returns
     * true, it is guaranteed that connection state intent for the profile will
     * be broadcasted with the state. Users can get the connection state of the
     * profile from this intent.
     * <p>
     * Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN} permission.
     *
     * @param device Remote Bluetooth Device
     * @return false on immediate error, true otherwise
     */
    public boolean connect(BluetoothDevice device) {
        return IMPL.connect(mReceiver, device);
    }

    /**
     * Initiate disconnection from a profile
     * <p>
     * This API will return false in scenarios like the profile on the Bluetooth
     * device is not in connected state etc. When this API returns, true, it is
     * guaranteed that the connection state change intent will be broadcasted
     * with the state. Users can get the disconnection state of the profile from
     * this intent.
     * <p>
     * If the disconnection is initiated by a remote device, the state will
     * transition from {@link #STATE_CONNECTED} to {@link #STATE_DISCONNECTED}.
     * If the disconnect is initiated by the host (local) device the state will
     * transition from {@link #STATE_CONNECTED} to state
     * {@link #STATE_DISCONNECTING} to state {@link #STATE_DISCONNECTED}. The
     * transition to {@link #STATE_DISCONNECTING} can be used to distinguish
     * between the two scenarios.
     * <p>
     * Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN} permission.
     *
     * @param device Remote Bluetooth Device
     * @return false on immediate error, true otherwise
     */
    public boolean disconnect(BluetoothDevice device) {
        return IMPL.disconnect(mReceiver, device);
    }

    /**
     * Get a list of devices that match any of the given connection
     * states.
     *
     * <p> If none of the devices match any of the given states,
     * an empty list will be returned.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param states Array of states. States can be one of
     *              {@link #STATE_CONNECTED}, {@link #STATE_CONNECTING},
     *              {@link #STATE_DISCONNECTED}, {@link #STATE_DISCONNECTING},
     * @return List of devices. The list will be empty on error.
     */
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        return IMPL.getDevicesMatchingConnectionStates(mReceiver, states);
    }

    /**
     * Get the current connection state of the profile
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param device Remote bluetooth device.
     * @return State of the profile connection. One of
     *               {@link #STATE_CONNECTED}, {@link #STATE_CONNECTING},
     *               {@link #STATE_DISCONNECTED}, {@link #STATE_DISCONNECTING}
     */
    public int getConnectionState(BluetoothDevice device) {
        return IMPL.getConnectionState(mReceiver, device);
    }

    public void shutdown() {
        IMPL.shutdown(mReceiver);
    }

    interface BluetoothA2dpStubCallback {
        public void onProxyConnected(Object receiver);
        public void onProxyDisconnected();
    }

    public interface BluetoothA2dpCompatCallback {
        public void onProxyConnected(BluetoothA2dpCompat proxy);
        public void onProxyDisconnected();
    }
}
