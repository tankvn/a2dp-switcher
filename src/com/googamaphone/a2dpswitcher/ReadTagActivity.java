
package com.googamaphone.a2dpswitcher;

import java.util.Set;

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
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.widget.TextView;

import com.googamaphone.a2dpswitcher.BluetoothSwitcherService.DeviceManagementBinder;
import com.googamaphone.compat.BluetoothA2dpCompat;
import com.googamaphone.compat.BluetoothDeviceCompatUtils;
import com.googamaphone.utils.WeakReferenceHandler;

public class ReadTagActivity extends Activity {
    private static final long DELAY_SUCCESS = 1000;
    private static final long DELAY_FAILURE = 2000;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothA2dpCompat mAudioProxy;
    private DeviceManagementBinder mDeviceManagementBinder;

    private String mTargetAddress;
    private String mTargetName;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        showProgress(R.string.progress_reading_tag);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothA2dpCompat.ACTION_CONNECTION_STATE_CHANGED);

        registerReceiver(mBroadcastReceiver, filter);

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
    }

    private boolean handleIntent() {
        final Intent intent = getIntent();
        if (intent == null) {
            return false;
        }

        if (!NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            return false;
        }

        final Uri uri = intent.getData();
        if ((uri == null) || !parseUri(uri)) {
            return false;
        }

        return true;
    }

    private void onDeviceManagerConnected(DeviceManagementBinder binder) {
        mDeviceManagementBinder = binder;
        mDeviceManagementBinder.registerCallback(mDeviceDataCallback);
    }

    /**
     * Attempts to connect to the device with the specified address, handling
     * failure cases where possible.
     * <p>
     * If necessary, this method will attempt to:
     * <ul>
     * <li>Prompt the user to enable Bluetooth</li>
     * <li>Initiate bonding with the requested device</li>
     * </ul>
     */
    private void resumeConnectingToDevice() {
        if (!mBluetoothAdapter.isEnabled()) {
            attemptEnableBluetooth();
            return;
        }

        if (mDeviceManagementBinder == null) {
            attemptBindService();
            return;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mTargetAddress);
        final Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();

        if (!bondedDevices.contains(device)) {
            attemptBondDevice(device);
            return;
        }

        if (mTargetName != null) {
            mDeviceManagementBinder.setNameForDevice(device, mTargetName);
        }

        attemptConnectDevice(device);
    }

    private void attemptBindService() {
        final Intent serviceIntent = new Intent(this, BluetoothSwitcherService.class);
        bindService(serviceIntent, mServiceConnection, 0);
    }

    private boolean parseUri(Uri uri) {
        final String version = uri.getQueryParameter(MainActivity.QUERY_VERSION);
        if (version == null) {
            return getAddressVersion0(uri);
        }

        try {
            final int versionCode = Integer.parseInt(version);
            if (versionCode == 1) {
                return getAddressVersion1(uri);
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        // Invalid version.
        return false;
    }

    private boolean getAddressVersion0(Uri uri) {
        final String path = uri.getPath();
        if ((path == null) || (path.length() <= 1)) {
            // Missing address.
            return true;
        }

        final String address = path.substring(1);
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            // Invalid address.
            return true;
        }

        mTargetAddress = address;
        mTargetName = null;

        return true;
    }

    private boolean getAddressVersion1(Uri uri) {
        final String address = uri.getQueryParameter(MainActivity.QUERY_ADDRESS);
        if ((address == null) || !BluetoothAdapter.checkBluetoothAddress(address)) {
            // Invalid or missing address.
            return true;
        }

        if (mBluetoothAdapter == null) {
            // This device does not support Bluetooth.
            return true;
        }

        mTargetAddress = address;
        mTargetName = uri.getQueryParameter(MainActivity.QUERY_NAME);

        return true;
    }

    private void showProgress(int resId) {
        setContentView(R.layout.dialog_waiting);

        final TextView message = (TextView) findViewById(R.id.message);
        message.setText(resId);
    }

    private void showSuccess() {
        setContentView(R.layout.dialog_success);

        mHandler.delayFinish(DELAY_SUCCESS);
    }

    private void showFailure(int resId) {
        setContentView(R.layout.dialog_failure);

        final TextView message = (TextView) findViewById(R.id.message);
        message.setText(resId);

        mHandler.delayFinish(DELAY_FAILURE);

        getWindow().getDecorView().requestLayout();
    }

    private void attemptEnableBluetooth() {
        if (!mBluetoothAdapter.enable()) {
            showFailure(R.string.failure_enable_bluetooth);
        } else {
            showProgress(R.string.progress_enable_bluetooth);
        }
    }

    private void attemptBondDevice(BluetoothDevice device) {
        if (!BluetoothDeviceCompatUtils.createBond(device)) {
            showFailure(R.string.failure_bond_device);
        } else {
            showProgress(R.string.progress_bond_device);
        }
    }

    private void attemptConnectDevice(BluetoothDevice device) {
        final int state = mAudioProxy.getConnectionState(device);

        switch (state) {
            case BluetoothA2dpCompat.STATE_CONNECTED:
            case BluetoothA2dpCompat.STATE_PLAYING:
                // The device is already connected.
                showSuccess();
                return;
        }

        if (!mAudioProxy.connect(device)) {
            showFailure(R.string.failure_connect_device);
        } else {
            showProgress(R.string.progress_connect_device);
        }
    }

    private void onAudioProxyAvailable() {
        mAudioProxy = mDeviceManagementBinder.getAudioProxy();

        resumeConnectingToDevice();
    }

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
            final int state = intent.getIntExtra(BluetoothA2dpCompat.EXTRA_STATE,
                    BluetoothA2dpCompat.STATE_DISCONNECTED);

            if (device.getAddress().equals(mTargetAddress)) {
                if ((state == BluetoothA2dpCompat.STATE_CONNECTED)
                        || (state == BluetoothA2dpCompat.STATE_PLAYING)) {
                    showSuccess();
                } else if (state == BluetoothA2dpCompat.STATE_DISCONNECTED) {
                    showFailure(R.string.failure_connect_device);
                }
            }
        }

        private void onDeviceBondStateChanged(Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.BOND_NONE);

            if (device.getAddress().equals(mTargetAddress)) {
                if (state == BluetoothDevice.BOND_BONDED) {
                    resumeConnectingToDevice();
                } else if (state == BluetoothDevice.BOND_NONE) {
                    showFailure(R.string.failure_bond_device);
                }
            }
        }

        private void onAdapterStateChanged(Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR);

            if (device.getAddress().equals(mTargetAddress)) {
                if (state == BluetoothAdapter.STATE_ON) {
                    resumeConnectingToDevice();
                } else {
                    showFailure(R.string.failure_enable_bluetooth);
                }
            }
        }
    };

    private final DeviceDataCallback mDeviceDataCallback = new DeviceDataCallback.Stub() {
        @Override
        public void onDeviceDataChanged() throws RemoteException {
            // Do nothing.
        }

        @Override
        public void onAudioProxyAvailable() throws RemoteException {
            mHandler.onAudioProxyAvailable();
        }
    };

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

    private final ReadTagHandler mHandler = new ReadTagHandler(this);

    private static class ReadTagHandler extends WeakReferenceHandler<ReadTagActivity> {
        private static final int DELAYED_FINISH = 1;
        private static final int PROXY_AVAILABLE = 2;

        public ReadTagHandler(ReadTagActivity parent) {
            super(parent);
        }

        @Override
        protected void handleMessage(Message msg, ReadTagActivity parent) {
            switch (msg.what) {
                case DELAYED_FINISH:
                    parent.finish();
                    break;
                case PROXY_AVAILABLE:
                    parent.onAudioProxyAvailable();
                    break;
            }
        }

        public void delayFinish(long delayMillis) {
            sendEmptyMessageDelayed(DELAYED_FINISH, delayMillis);
        }

        public void onAudioProxyAvailable() {
            sendEmptyMessage(PROXY_AVAILABLE);
        }
    }
}
