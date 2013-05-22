
package com.googamaphone.a2dpswitcher;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.googamaphone.compat.BluetoothA2dpCompat;
import com.googamaphone.utils.BluetoothDeviceUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BluetoothListAdapter extends BaseAdapter implements ListAdapter {
    private static final String TAG = BluetoothListAdapter.class.getSimpleName();

    private static final int[] ALL_A2DP_STATES = new int[]{
            BluetoothA2dpCompat.STATE_DISCONNECTED, BluetoothA2dpCompat.STATE_CONNECTING,
            BluetoothA2dpCompat.STATE_CONNECTED, BluetoothA2dpCompat.STATE_DISCONNECTING,
            BluetoothA2dpCompat.STATE_PLAYING
    };

    /**
     * Intent filter used for watching Bluetooth changes.
     */
    private static final IntentFilter INTENT_FILTER = new IntentFilter();

    static {
        INTENT_FILTER.addAction(BluetoothA2dpCompat.ACTION_CONNECTION_STATE_CHANGED);
        INTENT_FILTER.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        INTENT_FILTER.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        INTENT_FILTER.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        INTENT_FILTER.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        INTENT_FILTER.addAction(BluetoothDevice.ACTION_FOUND);
    }

    /**
     * Interval between running device discovery.
     */
    private static final long DISCOVERY_INTERVAL = 15000;

    /**
     * Maximum age of a device that can be considered "present".
     */
    private static final long PRESENCE_TIMEOUT = 30000;

    private final ArrayList<BluetoothDevice> mAudioDevices = new ArrayList<BluetoothDevice>();
    private final HashMap<BluetoothDevice, Long> mPresence = new HashMap<BluetoothDevice, Long>();

    private final Context mContext;
    private final LayoutInflater mLayoutInflater;

    private final int mViewResId;
    private final int mLabelResId;
    private final int mStatusResId;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothA2dpCompat mAudioProxy;
    private OnClickListener mSettingsClickListener;

    private boolean mDiscoveryEnabled;
    private boolean mShowAllDevices;

    public BluetoothListAdapter(Context context, int viewResId, int labelResId, int statusResId) {
        mContext = context;
        mViewResId = viewResId;
        mLabelResId = labelResId;
        mStatusResId = statusResId;

        mLayoutInflater = LayoutInflater.from(context);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        mShowAllDevices = false;
    }

    @Override
    public int getCount() {
        return mAudioDevices.size();
    }

    @Override
    public BluetoothDevice getItem(int position) {
        // TODO: Error handling.
        return mAudioDevices.get(position);
    }

    @Override
    public long getItemId(int position) {
        final BluetoothDevice device = getItem(position);

        return BluetoothDeviceUtils.getDeviceId(device);
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            final LinearLayout outerView = (LinearLayout) mLayoutInflater.inflate(
                    R.layout.menuitem_button, parent, false);
            final View innerView = mLayoutInflater.inflate(mViewResId, outerView, false);
            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.weight = 1.0f;

            assert innerView != null;
            innerView.setId(R.id.list_entry);
            innerView.setOnClickListener(mSettingsClickListener);

            assert outerView != null;
            outerView.addView(innerView, 0, params);
            outerView.findViewById(R.id.device_settings).setOnClickListener(mSettingsClickListener);

            convertView = outerView;
        }


        final TextView labelView = (TextView) convertView.findViewById(mLabelResId);
        final TextView statusView = (TextView) convertView.findViewById(mStatusResId);
        final View presenceIcon = convertView.findViewById(R.id.presence_indicator);

        final BluetoothDevice device = getItem(position);
        final String deviceName = getDeviceName(position);
        final int state = mAudioProxy.getConnectionState(device);
        final int statusResId = getResourceForDeviceState(state);

        // Ensure settings button has correct tag.
        convertView.findViewById(R.id.list_entry).setTag(R.id.tag_device, device);
        convertView.findViewById(R.id.device_settings).setTag(R.id.tag_device, device);

        labelView.setText(deviceName);
        statusView.setText(statusResId);

        // TODO: This should just use a different layout (or style?).
        if (mShowAllDevices && !isDeviceVisible(device)) {
            convertView.setBackgroundColor(0x33000000);
            labelView.setTextColor(0x99FFFFFF);
            statusView.setTextColor(0x66FFFFFF);
        } else {
            convertView.setBackgroundColor(0);
            labelView.setTextColor(0xFFFFFFFF);
            statusView.setTextColor(0xCCFFFFFF);
        }

        if (isStatePresent(state) || isDevicePresent(device)) {
            presenceIcon.setVisibility(View.VISIBLE);
        } else {
            presenceIcon.setVisibility(View.INVISIBLE);
        }

        return convertView;
    }

    public void setOnSettingsClickListener(OnClickListener listener) {
        mSettingsClickListener = listener;
    }

    /**
     * Manages the adapter's discovery state.
     * <p/>
     * When discovery is enabled, the adapter will automatically detect nearby
     * devices and display a presence indicator.
     *
     * @param enabled {@code true} to enable discovery.
     */
    public void setDiscoveryEnabled(boolean enabled) {
        mDiscoveryEnabled = enabled;

        final boolean isDiscovering = mBluetoothAdapter.isDiscovering();

        if (!enabled) {
            mHandler.removeCallbacks(mDiscoveryRunnable);
            mBluetoothAdapter.cancelDiscovery();
        } else if (!isDiscovering) {
            mBluetoothAdapter.startDiscovery();
        }
    }

    /**
     * Returns whether discovery is enabled.
     *
     * @return {@code true} if discovery is enabled.
     * @see #setDiscoveryEnabled(boolean)
     */
    public boolean isDiscoveryEnabled() {
        return mDiscoveryEnabled;
    }

    public void setAudioProxy(BluetoothA2dpCompat audioProxy) {
        mAudioProxy = audioProxy;

        reloadDevices();
    }

    /**
     * Sets whether this list adapter should ignore the result of
     * {@link #isDeviceVisible(BluetoothDevice)} and show all devices.
     *
     * @param enabled {@code true} to show all devices.
     */
    public void setShowAllDevices(boolean enabled) {
        final boolean previousValue = mShowAllDevices;

        mShowAllDevices = enabled;

        if (enabled != previousValue) {
            reloadDevices();
        }
    }

    /**
     * Returns whether this adapter is showing all devices.
     *
     * @return {@code true} if this adapter is showing all devices.
     * @see #setShowAllDevices(boolean)
     */
    public boolean isShowingAllDevices() {
        return mShowAllDevices;
    }

    /**
     * Registers this list adapter to receive changes in device and Bluetooth
     * adapter state.
     * <p/>
     * This method should be called after resuming any containing activities.
     */
    public void register() {
        mContext.registerReceiver(mBroadcastReceiver, INTENT_FILTER);
    }

    /**
     * Unregisters this list adapter and prevents it from receiving changes in
     * device and Bluetooth adapter state.
     * <p/>
     * This method should be called before pausing any containing activities.
     */
    public void unregister() {
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    /**
     * Returns the display name for the device at the specified position in the
     * adapter's data set.
     *
     * @param position The position of the device within the adapter's data set.
     * @return The display name for the device.
     */
    public String getDeviceName(int position) {
        return getDeviceName(getItem(position));
    }

    /**
     * Internal method used to determine the display name for a device.
     * <p/>
     * Default implementation returns the device's preferred name. Override this
     * method to customize the displayed names for devices.
     *
     * @param device The device to query.
     * @return The name to display for the device.
     */
    protected String getDeviceName(BluetoothDevice device) {
        return device.getName();
    }

    /**
     * Internal method used to determine whether the specified device should be
     * shown in the list.
     * <p/>
     * Default implementation always returns {@code true}. Override this method
     * to customize when devices are shown.
     *
     * @param device The device to query.
     * @return {@code true} if the device should be shown in the list.
     */
    protected boolean isDeviceVisible(BluetoothDevice device) {
        return true;
    }

    public void reloadDevices() {
        mAudioDevices.clear();

        if (mAudioProxy == null) {
            notifyDataSetChanged();
            return;
        }

        final List<BluetoothDevice> devices = mAudioProxy
                .getDevicesMatchingConnectionStates(ALL_A2DP_STATES);

        if (mShowAllDevices) {
            mAudioDevices.addAll(devices);
        } else {
            for (BluetoothDevice device : devices) {
                if (isDeviceVisible(device)) {
                    mAudioDevices.add(device);
                }
            }
        }

        notifyDataSetChanged();
    }

    private void onDeviceFound(BluetoothDevice device) {
        if (!mAudioDevices.contains(device)) {
            return;
        }

        mPresence.put(device, SystemClock.uptimeMillis());

        reloadDevices();
    }

    /**
     * Returns whether the specified device has recently (e.g. within
     * {@link #PRESENCE_TIMEOUT} milliseconds) advertised its presence.
     *
     * @param device The device to query.
     * @return {@code true} if the device is present.
     */
    private boolean isDevicePresent(BluetoothDevice device) {
        if (!mPresence.containsKey(device)) {
            return false;
        }

        final long elapsed = (SystemClock.uptimeMillis() - mPresence.get(device));

        return (elapsed < PRESENCE_TIMEOUT);
    }

    /**
     * Returns whether a connectivity state implies that the associated device
     * is present.
     *
     * @param state The connectivity state for a device.
     * @return {@code true} if the connectivity state implies that the device is
     *         present.
     */
    private static boolean isStatePresent(int state) {
        switch (state) {
            case BluetoothA2dp.STATE_DISCONNECTING:
            case BluetoothA2dp.STATE_CONNECTED:
            case BluetoothA2dp.STATE_PLAYING:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns the string resource ID describing a particular connectivity
     * state.
     *
     * @param state The connectivity state for a device.
     * @return The string resource ID describing the connectivity state.
     */
    private static int getResourceForDeviceState(int state) {
        switch (state) {
            case BluetoothA2dp.STATE_DISCONNECTED:
                return R.string.state_disconnected;
            case BluetoothA2dp.STATE_CONNECTING:
                return R.string.state_connecting;
            case BluetoothA2dp.STATE_CONNECTED:
                return R.string.state_connected;
            case BluetoothA2dp.STATE_DISCONNECTING:
                return R.string.state_disconnecting;
            case BluetoothA2dp.STATE_PLAYING:
                return R.string.state_playing;
        }

        Log.e(TAG, "Unknown Bluetooth state: " + state);

        return R.string.state_disconnected;
    }

    private final Handler mHandler = new Handler();

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothA2dpCompat.ACTION_CONNECTION_STATE_CHANGED.equals(action)
                    || BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)
                    || BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                reloadDevices();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // When discovery is enabled, restart discovery after a short delay.
                if (mDiscoveryEnabled) {
                    mHandler.postDelayed(mDiscoveryRunnable, DISCOVERY_INTERVAL);
                }
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                final BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    onDeviceFound(device);
                }
            }
        }
    };

    private final Runnable mDiscoveryRunnable = new Runnable() {
        @Override
        public void run() {
            mBluetoothAdapter.startDiscovery();
        }
    };
}
