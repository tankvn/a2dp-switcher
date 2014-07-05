
package com.googamaphone.a2dpswitcher;

import com.googamaphone.a2dpswitcher.BluetoothSwitcherService.DeviceManagementBinder;
import com.googamaphone.compat.BluetoothA2dpCompat;
import com.googamaphone.utils.BluetoothDeviceUtils;
import com.googamaphone.utils.NfcUtils;
import com.googamaphone.utils.WeakReferenceHandler;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.FragmentActivity;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends FragmentActivity {
    public static final String QUERY_NAME = "name";
    public static final String QUERY_ADDRESS = "address";
    public static final String QUERY_VERSION = "version";
    public static final String ACTION_SWITCH_DEVICE = "com.googamphone.a2dpswitcher.SWITCH_DEVICE";
    public static final String EXTRA_DEVICE_ID = "device_id";
    public static final String URI_AUTHORITY = "connect";
    public static final String URI_SCHEME = "a2dp";

    private static final String PREF_SHOW_ALL_DEVICES = "show_all_devices";

    /** The version of encoding used for NFC tags. */
    private static final int TAG_VERSION = 1;

    private static final int[] STATES_CONNECTED = new int[] {
            BluetoothA2dpCompat.STATE_CONNECTED,
            BluetoothA2dpCompat.STATE_CONNECTING
    };

    private static final String DIALOG_HIDE = "dialog_remove";
    private static final String DIALOG_RENAME = "dialog_rename";

    private final Object mDeviceLock = new Object();

    private SharedPreferences mPrefs;
    private BluetoothAdapter mBluetoothAdapter;
    private ManagedBluetoothListAdapter mDeviceAdapter;
    private BluetoothA2dpCompat mAudioProxy;
    private DeviceManagementBinder mDeviceManagementBinder;

    private boolean mHasRegisteredObserver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.app_name);
        setContentView(R.layout.activity_main);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        final boolean showAllDevices = mPrefs.getBoolean(PREF_SHOW_ALL_DEVICES, false);

        mDeviceAdapter = new ManagedBluetoothListAdapter(this);
        mDeviceAdapter.setOnSettingsClickListener(mOnSettingsClickListener);
        mDeviceAdapter.setShowAllDevices(showAllDevices);

        final View menuButton = findViewById(R.id.menu);

        final PopupMenu optionsMenu = new PopupMenu(this, menuButton) {
            @Override
            public void show() {
                // Prepare the options menu.
                final Menu menu = getMenu();
                menu.findItem(R.id.disconnect_all).setEnabled(mAudioProxy != null);
                menu.findItem(R.id.show_hidden).setChecked(mDeviceAdapter.isShowingAllDevices());

                super.show();
            }
        };

        getMenuInflater().inflate(R.menu.main_menu, optionsMenu.getMenu());

        optionsMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                return onContextItemSelected(item);
            }
        });

        menuButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                optionsMenu.show();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            attachDragToOpenListener(optionsMenu, menuButton);
        }

        final CheckBox checkBox = (CheckBox) findViewById(R.id.show_notification);
        checkBox.setOnCheckedChangeListener(mOnCheckedListener);

        final ListView listView = (ListView) findViewById(R.id.list_view);
        listView.setAdapter(mDeviceAdapter);
        listView.setOnItemClickListener(mOnDeviceClickListener);
        listView.setItemsCanFocus(true);
        registerForContextMenu(listView);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBroadcastReceiver, filter);

        final Intent serviceIntent = new Intent(this, BluetoothSwitcherService.class);
        startService(serviceIntent);
        bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE);

        updateApplicationState();
    }

    private static class ManagedBluetoothListAdapter extends BluetoothListAdapter {
        private DeviceManagementBinder mDeviceManagementBinder;

        public ManagedBluetoothListAdapter(Context context) {
            super(context, android.R.layout.simple_list_item_2, android.R.id.text1,
                    android.R.id.text2);
        }

        public void setDeviceManagementBinder(DeviceManagementBinder deviceManagementBinder) {
            mDeviceManagementBinder = deviceManagementBinder;
        }

        @Override
        public boolean isDeviceVisible(BluetoothDevice device) {
            if (mDeviceManagementBinder != null) {
                return mDeviceManagementBinder.isDeviceVisible(device);
            }

            return super.isDeviceVisible(device);
        }

        @Override
        public String getDeviceName(BluetoothDevice device) {
            if (mDeviceManagementBinder != null) {
                return mDeviceManagementBinder.getDeviceName(device);
            }

            return super.getDeviceName(device);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mDeviceManagementBinder != null) {
            mDeviceManagementBinder.unregisterCallback(mDeviceDataCallback);

            unbindService(mServiceConnection);
        }

        unregisterReceiver(mBroadcastReceiver);
    }

    private void onDeviceManagerConnected(DeviceManagementBinder binder) {
        mDeviceManagementBinder = binder;
        mDeviceManagementBinder.registerCallback(mDeviceDataCallback);

        mDeviceAdapter.setDeviceManagementBinder(mDeviceManagementBinder);

        mHandler.onDeviceDataChanged();
    }

    private void updateApplicationState() {
        if (mBluetoothAdapter == null) {
            showFailure(R.string.failure_bluetooth);
            return;
        }

        final boolean enabled = mBluetoothAdapter.isEnabled();

        updateDataSetObserver(enabled);

        if (enabled) {
            showDeviceList();
        } else {
            showBluetoothDisabled();
        }
    }

    private void updateDataSetObserver(boolean enabled) {
        synchronized (mDeviceLock) {
            if (!enabled && mHasRegisteredObserver) {
                mHasRegisteredObserver = false;
                mDeviceAdapter.unregisterDataSetObserver(mDataSetObserver);
            } else if (enabled && !mHasRegisteredObserver) {
                mHasRegisteredObserver = true;
                mDeviceAdapter.registerDataSetObserver(mDataSetObserver);
            }
        }
    }

    private void showBluetoothDisabled() {
        findViewById(R.id.list_view).setVisibility(View.GONE);
        findViewById(R.id.empty_list).setVisibility(View.GONE);

        final View bluetoothDisabled = findViewById(R.id.bluetooth_disabled);
        bluetoothDisabled.setVisibility(View.VISIBLE);

        final View enable = findViewById(R.id.enable);
        enable.setOnClickListener(mOnClickListener);
    }

    private void showFailure(int resId) {
        findViewById(R.id.list_view).setVisibility(View.GONE);
        findViewById(R.id.bluetooth_disabled).setVisibility(View.GONE);

        final TextView emptyList = (TextView) findViewById(R.id.empty_list);
        emptyList.setText(resId);
        emptyList.setVisibility(View.VISIBLE);
    }

    private void showDeviceList() {
        findViewById(R.id.bluetooth_disabled).setVisibility(View.GONE);
        findViewById(R.id.empty_list).setVisibility(View.GONE);
        findViewById(R.id.list_view).setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mDeviceAdapter.reloadDevices();
        mDeviceAdapter.register();
        mDeviceAdapter.setDiscoveryEnabled(true);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mDeviceAdapter.setDiscoveryEnabled(false);
        mDeviceAdapter.unregister();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                event.startTracking();
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                if (event.isTracking()) {
                    findViewById(R.id.menu).performClick();
                    return true;
                }
                break;
        }

        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        onCreateOptionsMenu(menu, v, menuInfo);
    }

    private void onCreateOptionsMenu(Menu menu, View v, ContextMenuInfo menuInfo) {
        if (menuInfo instanceof AdapterContextMenuInfo) {
            onCreateAdapterContextMenu(menu, menuInfo);
            return;
        }
    }

    private void onCreateAdapterContextMenu(Menu menu, ContextMenuInfo menuInfo) {
        final AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
        final int position = adapterMenuInfo.position;
        final BluetoothDevice device = mDeviceAdapter.getItem(position);

        onCreateOptionsMenuForDevice(menu, device);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final ContextMenuInfo menuInfo = item.getMenuInfo();

        if (menuInfo instanceof AdapterContextMenuInfo) {
            final AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
            final BluetoothDevice device = mDeviceAdapter.getItem(adapterMenuInfo.position);

            return onContextItemSelectedForDevice(item, device);
        }

        switch (item.getItemId()) {
            case R.id.disconnect_all:
                return disconnectAllDevices();
            case R.id.bluetooth_settings:
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                return true;
            case R.id.show_hidden:
                final boolean checked = !item.isChecked();
                item.setChecked(checked);
                mDeviceAdapter.setShowAllDevices(checked);
                final Editor editor = mPrefs.edit();
                editor.putBoolean(PREF_SHOW_ALL_DEVICES, checked);
                editor.apply();
                return true;
            case R.id.settings:
                // TODO: Implement preferences.
                return true;
            case R.id.contact_developer:
                contactDeveloper();
                return true;
        }

        return super.onContextItemSelected(item);
    }

    private boolean disconnectAllDevices() {
        final BluetoothA2dpCompat audioProxy = mAudioProxy;
        if (audioProxy == null) {
            return false;
        }

        final List<BluetoothDevice> connectedDevices = audioProxy
                .getDevicesMatchingConnectionStates(STATES_CONNECTED);
        for (BluetoothDevice connectedDevice : connectedDevices) {
            audioProxy.disconnect(connectedDevice);
        }

        return true;
    }

    private void onCreateOptionsMenuForDevice(Menu menu, BluetoothDevice device) {
        getMenuInflater().inflate(R.menu.device_menu, menu);

        // Remove unsupported device menu items.
        if (Build.VERSION.SDK_INT >= NfcUtils.MIN_SDK) {
            if (NfcUtils.hasDefaultAdapter(this)) {
                menu.findItem(R.id.write_tag).setVisible(true);
            }
        }

        final boolean isDeviceVisible = mDeviceAdapter.isDeviceVisible(device);

        menu.findItem(R.id.hide).setVisible(isDeviceVisible);
        menu.findItem(R.id.show).setVisible(!isDeviceVisible);
    }

    private boolean onContextItemSelectedForDevice(MenuItem item, BluetoothDevice device) {
        final String deviceName = mDeviceManagementBinder.getDeviceName(device);
        final int deviceId = BluetoothDeviceUtils.getDeviceId(device);

        switch (item.getItemId()) {
            case R.id.hide:
                RemoveDialogFragment.newInstance(deviceId, deviceName).show(
                        getSupportFragmentManager(), DIALOG_HIDE);
                return true;
            case R.id.show:
                setDeviceVisibility(deviceId, true);
                return true;
            case R.id.rename:
                RenameDialogFragment.newInstance(deviceId, deviceName).show(
                        getSupportFragmentManager(), DIALOG_RENAME);
                return true;
            case R.id.write_tag:
                showWriteTagActivity(device, deviceName);
                return true;
        }

        return false;
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
    private void showWriteTagActivity(BluetoothDevice device, String deviceName) {
        final Uri.Builder builder = new Uri.Builder().scheme(URI_SCHEME)
                .authority(URI_AUTHORITY)
                .appendQueryParameter(QUERY_VERSION, Integer.toString(TAG_VERSION))
                .appendQueryParameter(QUERY_ADDRESS, device.getAddress());

        final String realDeviceName = device.getName();
        if ((realDeviceName == null) || !realDeviceName.equals(deviceName)) {
            builder.appendQueryParameter(QUERY_NAME, deviceName);
        }

        final Intent intent = new Intent(this, WriteTagActivity.class);
        intent.putExtra(WriteTagActivity.EXTRA_URI, builder.build());
        intent.putExtra(WriteTagActivity.EXTRA_PACKAGE, getPackageName());

        startActivity(intent);
    }

    public void setDeviceName(int deviceId, String name) {
        mDeviceManagementBinder.setNameForDevice(deviceId, name);
    }

    public void setDeviceVisibility(int deviceId, boolean visible) {
        mDeviceManagementBinder.setDeviceVisibility(deviceId, visible);
        mDeviceAdapter.reloadDevices();
    }

    private void onAudioProxyAvailable() {
        mAudioProxy = mDeviceManagementBinder.getAudioProxy();
        mDeviceAdapter.setAudioProxy(mAudioProxy);

        // TODO: Manage a "loading" spinner.
    }

    private void attemptEnableBluetooth() {
        if (!mBluetoothAdapter.enable()) {
            Toast.makeText(this, R.string.failure_enable_bluetooth, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleDeviceState(View v, BluetoothDevice device) {
        final TextView status = (TextView) v.findViewById(android.R.id.text2);
        final int state = mAudioProxy.getConnectionState(device);

        if (state == BluetoothA2dpCompat.STATE_DISCONNECTED) {
            status.setText(R.string.state_connecting);
            mAudioProxy.connect(device);
        } else {
            status.setText(R.string.state_disconnecting);
            mAudioProxy.disconnect(device);
        }
    }

    private void onDeviceStateChanged() {
        mDeviceAdapter.notifyDataSetChanged();

        final boolean checked = mDeviceManagementBinder.getShowNotification();
        ((CheckBox) findViewById(R.id.show_notification)).setChecked(checked);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void attachDragToOpenListener(PopupMenu menu, View anchor) {
        anchor.setOnTouchListener(menu.getDragToOpenListener());
    }

    private void showContextMenuForDevice(View v, final BluetoothDevice device) {
        final PopupMenu deviceMenu = new PopupMenu(this, v);
        deviceMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                return onContextItemSelectedForDevice(item, device);
            }
        });

        final Menu menu = deviceMenu.getMenu();
        onCreateOptionsMenuForDevice(menu, device);
        deviceMenu.show();
    }

    protected void contactDeveloper() {
        String appVersion = "unknown";
        String appPackage = "unknown";
        final String phoneModel = android.os.Build.MODEL;
        final String osVersion = android.os.Build.VERSION.RELEASE;

        try {
            final PackageManager pm = getPackageManager();
            if (pm == null) {
                return;
            }

            final PackageInfo pi = pm.getPackageInfo(getPackageName(), 0);
            appVersion = pi.versionName;
            appPackage = pi.packageName;
        } catch (final NameNotFoundException e) {
            e.printStackTrace();
        }

        final String appName = getString(R.string.app_name);
        final String contactDev = getString(R.string.contact_dev);
        final String contactEmail = getString(R.string.contact_email);
        final String subject = getString(R.string.contact_subject, appName);
        final String body = getString(R.string.contact_body, appName, appPackage, appVersion,
                phoneModel, osVersion);

        final Intent sendIntent = new Intent(Intent.ACTION_SENDTO,
                Uri.fromParts("mailto", contactEmail, null));
        sendIntent.putExtra(Intent.EXTRA_TEXT, body);
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

        startActivity(Intent.createChooser(sendIntent, contactDev));
    }

    private final MainActivityHandler mHandler = new MainActivityHandler(this);

    private final OnClickListener mOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.enable:
                    attemptEnableBluetooth();
                    break;
            }
        }
    };

    private final OnItemClickListener mOnDeviceClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
            final BluetoothDevice device = (BluetoothDevice) v.getTag(R.id.tag_device);
            if (device != null) {
                toggleDeviceState(v, device);
            }
        }
    };

    private final OnClickListener mOnSettingsClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final BluetoothDevice device = (BluetoothDevice) v.getTag(R.id.tag_device);
            if (device != null) {
                showContextMenuForDevice(v, device);
            }
        }
    };

    private final DataSetObserver mDataSetObserver = new DataSetObserver() {
        @Override
        public void onChanged() {
            if (mDeviceAdapter.isEmpty()) {
                showFailure(R.string.not_paired);
            } else {
                showDeviceList();
            }
        }
    };

    private final OnCheckedChangeListener mOnCheckedListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (mDeviceManagementBinder != null) {
                mDeviceManagementBinder.setShowNotification(isChecked);
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                updateApplicationState();
            }
        }
    };

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            // TODO: Reconnect to device management service or show error.
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof DeviceManagementBinder) {
                onDeviceManagerConnected((DeviceManagementBinder) service);
            }
        }
    };

    private final DeviceDataCallback mDeviceDataCallback = new DeviceDataCallback.Stub() {
        @Override
        public void onDeviceDataChanged() throws RemoteException {
            mHandler.onDeviceDataChanged();
        }

        @Override
        public void onAudioProxyAvailable() throws RemoteException {
            mHandler.onAudioProxyAvailable();
        }
    };

    private static class MainActivityHandler extends WeakReferenceHandler<MainActivity> {
        private static final int STATE_CHANGED = 1;

        private static final int PROXY_AVAILABLE = 2;

        public MainActivityHandler(MainActivity parent) {
            super(parent);
        }

        @Override
        protected void handleMessage(Message msg, MainActivity parent) {
            switch (msg.what) {
                case STATE_CHANGED:
                    parent.onDeviceStateChanged();
                    break;
                case PROXY_AVAILABLE:
                    parent.onAudioProxyAvailable();
                    break;
            }
        }

        public void onDeviceDataChanged() {
            sendEmptyMessage(STATE_CHANGED);
        }

        public void onAudioProxyAvailable() {
            sendEmptyMessage(PROXY_AVAILABLE);
        }
    }
}
