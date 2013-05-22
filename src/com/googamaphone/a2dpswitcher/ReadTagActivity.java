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

package com.googamaphone.a2dpswitcher;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Pair;
import android.widget.TextView;

import com.googamaphone.a2dpswitcher.BluetoothSwitcherService.DeviceManagementBinder;
import com.googamaphone.compat.BluetoothA2dpCompat;
import com.googamaphone.compat.BluetoothDeviceCompatUtils;

import java.util.Set;

/**
 * Activity used to read NFC tags encoded by this app.
 * <p/>
 * After parsing the tag data, this activity performs several checks and
 * attempts to resolve any issues before connecting to a Bluetooth device.
 * <ol>
 * <li>Is Bluetooth enabled on this device? Turn on Bluetooth.
 * <li>Is the A2DP device management service running? Bind to it.
 * <li>Is this device bonded to the Bluetooth device? Bond to it.
 * <li>Is audio output connected to the Bluetooth device? Connect it.
 * </ol>
 */
public class ReadTagActivity extends Activity {
    /**
     * Delay in milliseconds before finishing after a successful read.
     */
    private static final long DELAY_SUCCESS = 1000;

    /**
     * Delay in milliseconds before finishing after a failed read.
     */
    private static final long DELAY_FAILURE = 2000;

    /**
     * Intent filter used to monitor changes in Bluetooth state.
     */
    private static final IntentFilter FILTER_STATE_CHANGED = new IntentFilter();

    static {
        FILTER_STATE_CHANGED.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        FILTER_STATE_CHANGED.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        FILTER_STATE_CHANGED.addAction(BluetoothA2dpCompat.ACTION_CONNECTION_STATE_CHANGED);
    }

    /**
     * The default Bluetooth adapter.
     */
    private BluetoothAdapter mBluetoothAdapter;

    /**
     * The audio proxy used to connect A2DP streaming.
     */
    private BluetoothA2dpCompat mAudioProxy;

    /**
     * Connection to the A2DP device management service.
     */
    // TODO(alanv): This seems like overkill since they're in the same package.
    private DeviceManagementBinder mDeviceManagementBinder;

    /**
     * The address of the target device, as read from the NFC tag.
     */
    private String mTargetAddress;

    /**
     * The name of the target device, as read from the NFC tag.
     */
    private String mTargetName;

    /**
     * Whether the service was bound successfully.
     */
    private boolean mServiceBound;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        showProgress(R.string.progress_reading_tag);

        registerReceiver(mBroadcastReceiver, FILTER_STATE_CHANGED);

        if (!handleIntent()) {
            showFailure(R.string.failure_read_tag);
            return;
        }

        resumeConnectingToDevice();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mBroadcastReceiver);

        if (mDeviceManagementBinder != null) {
            mDeviceManagementBinder.unregisterCallback(mDeviceDataCallback);
        }

        if (mServiceBound) {
            unbindService(mServiceConnection);
        }
    }

    /**
     * Parses the target device's address and name from the NFC tag.
     *
     * @return {@code true} on success.
     */
    private boolean handleIntent() {
        final Intent intent = getIntent();
        if (intent == null) {
            return false;
        }

        final String action = intent.getAction();
        if (!NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            return false;
        }

        final Uri uri = intent.getData();
        if (uri == null) {
            return false;
        }

        final Pair<String, String> data = A2dpSwitcherUtils.parseUri(uri);
        if (data == null) {
            return false;
        }

        mTargetAddress = data.first;
        mTargetName = data.second;

        return true;
    }

    /**
     * Called after connecting to the device management service. Registers a
     * callback to listen for changes in device data.
     *
     * @param binder The binder that was connected.
     */
    private void onDeviceManagerConnected(DeviceManagementBinder binder) {
        mDeviceManagementBinder = binder;
        mDeviceManagementBinder.registerCallback(mDeviceDataCallback);
    }

    /**
     * Attempts to connect to the device with the specified address, handling
     * failure cases where possible.
     * <p/>
     * If necessary, this method will attempt to:
     * <ul>
     * <li>Prompt the user to enable Bluetooth</li>
     * <li>Initiate bonding with the requested device</li>
     * </ul>
     */
    private void resumeConnectingToDevice() {
        // Is the Bluetooth adapter enabled?
        if (!mBluetoothAdapter.isEnabled()) {
            attemptEnableBluetooth();
            return;
        }

        // Are we bound to the device management service?
        if (mDeviceManagementBinder == null) {
            attemptBindService();
            return;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mTargetAddress);
        final Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
        if (bondedDevices == null) {
            showFailure(R.string.failure_connect_device);
            return;
        }

        // Are we bonded to the Bluetooth device?
        if (!bondedDevices.contains(device)) {
            attemptBondDevice(device);
            return;
        }

        attemptConnectDevice(device);
    }

    /**
     * Attempts to bind to the A2DP device management service.
     */
    private void attemptBindService() {
        final Intent serviceIntent = new Intent(this, BluetoothSwitcherService.class);
        mServiceBound = bindService(serviceIntent, mServiceConnection, 0);
    }

    /**
     * Attempts to enable Bluetooth on this device.
     */
    private void attemptEnableBluetooth() {
        if (!mBluetoothAdapter.enable()) {
            showFailure(R.string.failure_enable_bluetooth);
        } else {
            showProgress(R.string.progress_enable_bluetooth);
        }
    }

    /**
     * Attempts to bond to the specified Bluetooth device.
     *
     * @param device The device to bond.
     */
    private void attemptBondDevice(BluetoothDevice device) {
        if (!BluetoothDeviceCompatUtils.createBond(device)) {
            showFailure(R.string.failure_bond_device);
        } else {
            showProgress(R.string.progress_bond_device);
        }
    }

    /**
     * Attempts to connect audio output to the specified Bluetooth device.
     *
     * @param device The device to connect.
     */
    private void attemptConnectDevice(BluetoothDevice device) {
        // If the device has a name, let the management service know.
        // TODO: Maybe ask the user if they want to rename the device?
        if (mTargetName != null) {
            mDeviceManagementBinder.setNameForDevice(device, mTargetName);
        }

        final int state = mAudioProxy.getConnectionState(device);
        switch (state) {
            case BluetoothA2dpCompat.STATE_CONNECTED:
            case BluetoothA2dpCompat.STATE_PLAYING:
                // The device is already connected.
                showSuccess();
                return;
        }

        // Attempt to connect. If we fail immediately, let the user know.
        if (mAudioProxy.connect(device)) {
            showProgress(R.string.progress_connect_device);
        } else {
            showFailure(R.string.failure_connect_device);
        }
    }

    /**
     * Called after connecting to the audio proxy. Attempts to resume connecting
     * to the Bluetooth device.
     */
    private void onAudioProxyAvailable() {
        mAudioProxy = mDeviceManagementBinder.getAudioProxy();

        resumeConnectingToDevice();
    }

    /**
     * Sets the layout to progress with the specified message.
     *
     * @param resId The message to display.
     */
    private void showProgress(int resId) {
        setContentView(R.layout.dialog_waiting);

        final TextView message = (TextView) findViewById(R.id.message);
        message.setText(resId);
    }

    /**
     * Sets the layout to success, sets the activity result to okay, and
     * finishes after a delay.
     */
    private void showSuccess() {
        setContentView(R.layout.dialog_success);

        setResult(RESULT_OK);
        mHandler.postDelayed(mDelayedFinish, DELAY_SUCCESS);
    }

    /**
     * Sets the layout to failure with the specified message, sets the activity
     * result to cancelled, and finishes after a delay.
     *
     * @param resId The message to display.
     */
    private void showFailure(int resId) {
        setContentView(R.layout.dialog_failure);

        final TextView message = (TextView) findViewById(R.id.message);
        message.setText(resId);

        setResult(RESULT_CANCELED);
        mHandler.postDelayed(mDelayedFinish, DELAY_FAILURE);

        // TODO: Is this necessary? Probably not...
        getWindow().getDecorView().requestLayout();
    }

    /**
     * Handles changes in the Bluetooth device connection state.
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                onAdapterStateChanged(intent);
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                onDeviceBondStateChanged(intent);
            } else if (BluetoothA2dpCompat.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                onDeviceConnectionStateChanged(intent);
            }
        }

        private void onDeviceConnectionStateChanged(Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null) {
                showFailure(R.string.failure_connect_device);
                return;
            }

            final int state = intent.getIntExtra(BluetoothA2dpCompat.EXTRA_STATE,
                    BluetoothA2dpCompat.STATE_DISCONNECTED);
            final String address = device.getAddress();
            if ((address == null) || !address.equals(mTargetAddress)) {
                showFailure(R.string.failure_connect_device);
                return;
            }

            if ((state == BluetoothA2dpCompat.STATE_CONNECTED)
                    || (state == BluetoothA2dpCompat.STATE_PLAYING)) {
                showSuccess();
            } else {
                showFailure(R.string.failure_connect_device);
            }
        }

        private void onDeviceBondStateChanged(Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null) {
                showFailure(R.string.failure_bond_device);
                return;
            }

            final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.BOND_NONE);
            final String address = device.getAddress();
            if ((address == null) || !address.equals(mTargetAddress)) {
                showFailure(R.string.failure_bond_device);
                return;
            }

            if (state == BluetoothDevice.BOND_BONDED) {
                resumeConnectingToDevice();
            } else if (state == BluetoothDevice.BOND_NONE) {
                showFailure(R.string.failure_bond_device);
            }
        }

        private void onAdapterStateChanged(Intent intent) {
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR);

            if (state == BluetoothAdapter.STATE_ON) {
                resumeConnectingToDevice();
            } else {
                showFailure(R.string.failure_enable_bluetooth);
            }
        }
    };

    /**
     * Handles changes in the audio proxy connection state.
     */
    private final DeviceDataCallback mDeviceDataCallback = new DeviceDataCallback.Stub() {
        @Override
        public void onDeviceDataChanged() throws RemoteException {
            // Do nothing.
        }

        @Override
        public void onAudioProxyAvailable() throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    ReadTagActivity.this.onAudioProxyAvailable();
                }
            });
        }
    };

    /**
     * Handles changes in the A2DP device management service connection state.
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof DeviceManagementBinder) {
                onDeviceManagerConnected((DeviceManagementBinder) service);
            }
        }
    };

    private final Handler mHandler = new Handler();

    /**
     * Runnable used to finish the app after a delay.
     */
    private final Runnable mDelayedFinish = new Runnable() {
        @Override
        public void run() {
            finish();
        }
    };
}
