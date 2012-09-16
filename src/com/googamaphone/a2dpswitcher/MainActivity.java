
package com.googamaphone.a2dpswitcher;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;

import com.googamaphone.a2dpswitcher.BluetoothSwitcherService.DeviceManagementBinder;
import com.googamaphone.compat.BluetoothA2dpCompat;
import com.googamaphone.utils.BluetoothDeviceUtils;
import com.googamaphone.utils.NfcUtils;
import com.googamaphone.utils.WeakReferenceHandler;

public class MainActivity extends FragmentActivity {
    public static final String QUERY_NAME = "name";
    public static final String QUERY_ADDRESS = "address";
    public static final String QUERY_VERSION = "version";

    public static final String ACTION_SWITCH_DEVICE = "com.googamphone.a2dpswitcher.SWITCH_DEVICE";
    public static final String EXTRA_DEVICE_ID = "device_id";

    public static final String URI_AUTHORITY = "connect";
    public static final String URI_SCHEME = "a2dp";

    /** The version of encoding used for NFC tags. */
    private static final int TAG_VERSION = 1;

    private static final String DIALOG_REMOVE = "dialog_remove";
    private static final String DIALOG_RENAME = "dialog_rename";

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothListAdapter mDeviceAdapter;
    private BluetoothA2dpCompat mAudioProxy;
    private DeviceManagementBinder mDeviceManagementBinder;

    private boolean mHasRegisteredObserver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        mDeviceAdapter = new BluetoothListAdapter(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, android.R.id.text2) {
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
        };
        mDeviceAdapter.setOnSettingsClickListener(mOnClickListener);

        findViewById(R.id.menu).setOnClickListener(mOnClickListener);

        final CheckBox checkBox = (CheckBox) findViewById(R.id.show_notification);
        checkBox.setOnCheckedChangeListener(mOnCheckedListener);

        final ListView listView = (ListView) findViewById(R.id.list_view);
        listView.setAdapter(mDeviceAdapter);
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

        mHandler.onDeviceDataChanged();
    }

    private void updateApplicationState() {
        if (mBluetoothAdapter == null) {
            showFailure(R.string.failure_bluetooth);
            return;
        }

        final boolean enabled = mBluetoothAdapter.isEnabled();

        updateDataSetObserver(enabled);

        if (!enabled) {
            showDeviceList();
        } else {
            showBluetoothDisabled();
        }
    }

    private void updateDataSetObserver(boolean enabled) {
        if (!enabled && mHasRegisteredObserver) {
            mDeviceAdapter.unregisterDataSetObserver(mDataSetObserver);
        } else if (enabled && !mHasRegisteredObserver) {
            mDeviceAdapter.registerDataSetObserver(mDataSetObserver);
            mHasRegisteredObserver = true;
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
    }

    @Override
    protected void onPause() {
        super.onPause();

        mDeviceAdapter.unregister();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (!(menuInfo instanceof AdapterContextMenuInfo)) {
            super.onCreateContextMenu(menu, v, menuInfo);
            return;
        }

        final AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
        final int position = adapterMenuInfo.position;
        final BluetoothDevice device = mDeviceAdapter.getItem(position);

        onCreateDeviceSettingsMenu(menu, device);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final ContextMenuInfo menuInfo = item.getMenuInfo();

        if (!(menuInfo instanceof AdapterContextMenuInfo)) {
            return super.onContextItemSelected(item);
        }

        final AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
        final int position = adapterMenuInfo.position;
        final BluetoothDevice device = mDeviceAdapter.getItem(position);

        if (performActionForDevice(item.getItemId(), device)) {
            return true;
        }

        return super.onContextItemSelected(item);
    }

    private void onCreateDeviceSettingsMenu(Menu menu, BluetoothDevice device) {
        menu.add(ContextMenu.NONE, R.string.menu_rename, ContextMenu.NONE, R.string.menu_rename);

        if (Build.VERSION.SDK_INT >= NfcUtils.MIN_SDK_VERSION) {
            menu.add(ContextMenu.NONE, R.string.menu_write_tag, ContextMenu.NONE,
                    R.string.menu_write_tag);
        }

        menu.add(ContextMenu.NONE, R.string.menu_remove, ContextMenu.NONE, R.string.menu_remove);
    }

    private boolean performActionForDevice(int itemId, BluetoothDevice device) {
        final String deviceName = mDeviceManagementBinder.getDeviceName(device);
        final int deviceId = BluetoothDeviceUtils.getDeviceId(device);

        switch (itemId) {
            case R.string.menu_remove: {
                final DialogFragment removeFragment = RemoveDialogFragment.newInstance(deviceId,
                        deviceName);
                removeFragment.show(getSupportFragmentManager(), DIALOG_REMOVE);
                return true;
            }
            case R.string.menu_rename: {
                final DialogFragment renameFragment = RenameDialogFragment.newInstance(deviceId,
                        deviceName);
                renameFragment.show(getSupportFragmentManager(), DIALOG_RENAME);
                return true;
            }
            case R.string.menu_write_tag: {
                final Uri.Builder builder = new Uri.Builder()
                        .scheme(URI_SCHEME).authority(URI_AUTHORITY)
                        .appendQueryParameter(QUERY_VERSION, Integer.toString(TAG_VERSION))
                        .appendQueryParameter(QUERY_ADDRESS, device.getAddress());

                if (!device.getName().equals(deviceName)) {
                    builder.appendQueryParameter(QUERY_NAME, deviceName);
                }

                final Intent intent = new Intent(this, WriteTagActivity.class);
                intent.putExtra(WriteTagActivity.EXTRA_URI, builder.build());
                intent.putExtra(WriteTagActivity.EXTRA_PACKAGE, getPackageName());

                startActivity(intent);
                return true;
            }
        }

        return false;
    }

    public void setDeviceName(int deviceId, String name) {
        mDeviceManagementBinder.setNameForDevice(deviceId, name);
    }

    public void setDeviceVisibility(int deviceId, boolean isVisible) {
        mDeviceManagementBinder.setDeviceVisibility(deviceId, false);
    }

    private void onAudioProxyAvailable() {
        mAudioProxy = mDeviceManagementBinder.getAudioProxy();
        mDeviceAdapter.setAudioProxy(mAudioProxy);
    }

    private void attemptEnableBluetooth() {
        setContentView(R.layout.dialog_waiting);

        if (!mBluetoothAdapter.enable()) {
            // TODO: Show "failed to enable Bluetooth" dialog.
            return;
        }
    }

    private void onDeviceManagerStateChanged() {
        mDeviceAdapter.notifyDataSetChanged();

        final boolean checked = mDeviceManagementBinder.getShowNotification();
        final CheckBox checkBox = (CheckBox) findViewById(R.id.show_notification);
        checkBox.setChecked(checked);
    }

    private final MainActivityHandler mHandler = new MainActivityHandler(this);

    private final OnClickListener mOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.enable:
                    attemptEnableBluetooth();
                    break;
                case R.id.menu:
                    // TODO: Show a menu.
                    break;
                case R.id.list_entry: {
                    final BluetoothDevice device = (BluetoothDevice) v.getTag(R.id.tag_device);
                    final int state = mAudioProxy.getConnectionState(device);

                    if (state == BluetoothA2dpCompat.STATE_DISCONNECTED) {
                        mAudioProxy.connect(device);
                    } else {
                        mAudioProxy.disconnect(device);
                    }
                } break;
                case R.id.device_settings: {
                    final BluetoothDevice device = (BluetoothDevice) v.getTag(R.id.tag_device);

                    if (Build.VERSION.SDK_INT <= 10) {
                        final ListView listView = (ListView) findViewById(R.id.list_view);
                        listView.showContextMenuForChild((View) v.getParent());
                    } else {
                        HoneycombHelper.showDeviceSettingsMenu(MainActivity.this, v, device);
                    }
                } break;
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
            // TODO Auto-generated method stub
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
                    parent.onDeviceManagerStateChanged();
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
    };

    @TargetApi(11)
    private static class HoneycombHelper {
        private static void showDeviceSettingsMenu(final MainActivity parent, View anchor, final BluetoothDevice device) {
            final PopupMenu deviceMenu = new PopupMenu(parent, anchor);

            deviceMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    return parent.performActionForDevice(item.getItemId(), device);
                }
            });

            parent.onCreateDeviceSettingsMenu(deviceMenu.getMenu(), device);
            deviceMenu.show();
        }
    }
}
