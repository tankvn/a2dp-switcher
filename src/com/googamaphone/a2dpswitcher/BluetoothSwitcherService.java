
package com.googamaphone.a2dpswitcher;

import com.googamaphone.compat.BluetoothA2dpCompat;
import com.googamaphone.compat.BluetoothA2dpCompat.BluetoothA2dpCompatCallback;
import com.googamaphone.utils.BluetoothDeviceUtils;
import com.googamaphone.utils.PreferencesUtils;

import android.app.PendingIntent;
import android.app.Service;
import android.app.backup.BackupManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.SparseArray;

import java.util.List;
import java.util.TreeSet;

public class BluetoothSwitcherService extends Service {
    public static final String PREF_HIDDEN = "hidden";
    public static final String PREF_CUSTOM_NAMES = "customNames";
    public static final String PREF_NOTIFY = "show_notification";

    public static final boolean PREF_NOTIFY_DEFAULT = true;

    private static final int[] STATES_CONNECTED = new int[] {
            BluetoothA2dpCompat.STATE_CONNECTING,
            BluetoothA2dpCompat.STATE_CONNECTED,
            BluetoothA2dpCompat.STATE_DISCONNECTING,
            BluetoothA2dpCompat.STATE_PLAYING
    };

    private final DeviceManagementBinder mBinder = new DeviceManagementBinder(this);
    private final SparseArray<String> mCustomDeviceNames = new SparseArray<String>();
    private final TreeSet<Integer> mHiddenDevices = new TreeSet<Integer>();

    private NotificationCompat.Builder mNotificationBuilder;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothA2dpCompat mAudioProxy;

    private boolean mShowNotification;
    private boolean mIsConnectingToProxy;

    @Override
    public void onCreate() {
        loadPreferences();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        createNotification();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothA2dpCompat.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);

        connectAudioProxy();
    }

    private void connectAudioProxy() {
        if ((mAudioProxy != null) || mIsConnectingToProxy) {
            // Audio proxy is already connected or is connecting.
            return;
        }

        if ((mBluetoothAdapter == null) || !mBluetoothAdapter.isEnabled()) {
            // Bluetooth is not supported or is disabled.
            return;
        }

        mIsConnectingToProxy = true;
        BluetoothA2dpCompat.obtain(this, mAudioProxyCallback);
    }

    @Override
    public void onDestroy() {
        savePreferences();

        unregisterReceiver(mReceiver);

        if (mAudioProxy != null) {
            mAudioProxy.shutdown();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void createNotification() {
        final Intent intent = new Intent(this, MainActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        mNotificationBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_stat_switcher)
                .setTicker(getString(R.string.notification_ticker))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setWhen(0);
    }

    private void updateNotification() {
        if (!mShowNotification) {
            stopForeground(true);
            return;
        }

        if (mBluetoothAdapter == null) {
            // This device does not support Bluetooth.
            mNotificationBuilder.setContentTitle(getString(R.string.notify_missing_bluetooth));
            mNotificationBuilder.setContentText(null);
        } else if (!mBluetoothAdapter.isEnabled()) {
            // Bluetooth is currently disabled.
            mNotificationBuilder.setContentTitle(getString(R.string.notify_bluetooth_disabled));
            mNotificationBuilder.setContentText(null);
        } else if (mAudioProxy == null) {
            // Failed to connect to the audio service.
            mNotificationBuilder.setContentTitle(getString(R.string.notify_missing_audio_service));
            mNotificationBuilder.setContentText(null);
        } else {
            mNotificationBuilder.setContentTitle(getConnectedDeviceName());
            mNotificationBuilder.setContentText(getString(R.string.touch_to_change));
        }

        startForeground(R.id.notify_switcher, mNotificationBuilder.getNotification());
    }

    private String getConnectedDeviceName() {
        final List<BluetoothDevice> devices = mAudioProxy
                .getDevicesMatchingConnectionStates(STATES_CONNECTED);

        if ((devices == null) || devices.isEmpty()) {
            // No audio devices are connected.
            return getString(R.string.no_device);
        }

        final BluetoothDevice connectedDevice = devices.get(0);
        return mBinder.getDeviceName(connectedDevice);
    }

    private void loadPreferences() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        PreferencesUtils.getSparseArray(prefs, PREF_CUSTOM_NAMES, mCustomDeviceNames);
        PreferencesUtils.getCollection(prefs, PREF_HIDDEN, mHiddenDevices);

        mShowNotification = prefs.getBoolean(PREF_NOTIFY, PREF_NOTIFY_DEFAULT);
    }

    private void savePreferences() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final SharedPreferences.Editor editor = prefs.edit();

        PreferencesUtils.putSparseArray(editor, PREF_CUSTOM_NAMES, mCustomDeviceNames);
        PreferencesUtils.putCollection(editor, PREF_HIDDEN, mHiddenDevices);

        editor.putBoolean(PREF_NOTIFY, mShowNotification);
        editor.commit();

        // Ensure data is backed up.
        BackupManager.dataChanged(getPackageName());
    }

    private void setNameForDeviceInternal(int deviceId, String name) {
        mCustomDeviceNames.put(deviceId, name);

        savePreferences();
    }

    private String getDeviceNameInternal(BluetoothDevice device) {
        final int deviceId = BluetoothDeviceUtils.getDeviceId(device);
        return mCustomDeviceNames.get(deviceId, device.getName());
    }

    private void setDeviceVisibilityInternal(int deviceId, boolean isVisible) {
        if (isVisible) {
            mHiddenDevices.remove(deviceId);
        } else {
            mHiddenDevices.add(deviceId);
        }

        savePreferences();
    }

    private boolean isDeviceVisibleInternal(BluetoothDevice device) {
        final int deviceId = BluetoothDeviceUtils.getDeviceId(device);
        return !mHiddenDevices.contains(deviceId);
    }

    private void setShowNotificationInternal(boolean showNotification) {
        mShowNotification = showNotification;

        savePreferences();
        updateNotification();
    }

    private boolean getShowNotificationInternal() {
        return mShowNotification;
    }

    private BluetoothA2dpCompat getAudioProxyInternal() {
        return mAudioProxy;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                connectAudioProxy();
            }

            updateNotification();
        }
    };

    private final BluetoothA2dpCompatCallback mAudioProxyCallback = new BluetoothA2dpCompatCallback() {
        @Override
        public void onProxyDisconnected() {
            // Do nothing.
        }

        @Override
        public void onProxyConnected(BluetoothA2dpCompat proxy) {
            mAudioProxy = proxy;
            mIsConnectingToProxy = false;

            // We may now obtain streaming information.
            updateNotification();

            mBinder.fireAudioProxyAvailable();
        }
    };

    public static class DeviceManagementBinder extends Binder {
        private final RemoteCallbackList<DeviceDataCallback> mListeners =
                new RemoteCallbackList<DeviceDataCallback>();

        private final BluetoothSwitcherService mService;

        public DeviceManagementBinder(BluetoothSwitcherService service) {
            mService = service;
        }

        public String getDeviceName(BluetoothDevice device) {
            return mService.getDeviceNameInternal(device);
        }

        public void setNameForDevice(BluetoothDevice device, String name) {
            setNameForDevice(BluetoothDeviceUtils.getDeviceId(device), name);
        }

        public void setNameForDevice(int deviceId, String name) {
            mService.setNameForDeviceInternal(deviceId, name);
            fireStateChange();
        }

        public boolean isDeviceVisible(BluetoothDevice device) {
            return mService.isDeviceVisibleInternal(device);
        }

        public void setDeviceVisibility(int deviceId, boolean isVisible) {
            mService.setDeviceVisibilityInternal(deviceId, isVisible);
        }

        public boolean getShowNotification() {
            return mService.getShowNotificationInternal();
        }

        public void setShowNotification(boolean showNotification) {
            mService.setShowNotificationInternal(showNotification);
        }

        private void fireAudioProxyAvailable() {
            final int count = mListeners.beginBroadcast();

            for (int i = 0; i < count; i++) {
                try {
                    mListeners.getBroadcastItem(i).onAudioProxyAvailable();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            mListeners.finishBroadcast();
        }

        private void fireStateChange() {
            final int count = mListeners.beginBroadcast();

            for (int i = 0; i < count; i++) {
                try {
                    mListeners.getBroadcastItem(i).onDeviceDataChanged();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            mListeners.finishBroadcast();
        }

        public void registerCallback(DeviceDataCallback callback) {
            if (callback == null) {
                return;
            }

            // Audio proxy callback is "sticky".
            if (mService.getAudioProxyInternal() != null) {
                try {
                    callback.onAudioProxyAvailable();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            mListeners.register(callback);
        }

        public void unregisterCallback(DeviceDataCallback callback) {
            if (callback != null) {
                mListeners.unregister(callback);
            }
        }

        public BluetoothA2dpCompat getAudioProxy() {
            return mService.getAudioProxyInternal();
        }
    }
}
