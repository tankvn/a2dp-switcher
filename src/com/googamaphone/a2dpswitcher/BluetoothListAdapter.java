
package com.googamaphone.a2dpswitcher;

import java.util.ArrayList;
import java.util.List;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

public class BluetoothListAdapter extends BaseAdapter implements ListAdapter {
    private static final String TAG = BluetoothListAdapter.class.getSimpleName();

    private static final int[] ALL_A2DP_STATES = new int[] {
            BluetoothA2dpCompat.STATE_DISCONNECTED, BluetoothA2dpCompat.STATE_CONNECTING,
            BluetoothA2dpCompat.STATE_CONNECTED, BluetoothA2dpCompat.STATE_DISCONNECTING,
            BluetoothA2dpCompat.STATE_PLAYING
    };

    private final ArrayList<BluetoothDevice> mAudioDevices = new ArrayList<BluetoothDevice>();

    private final Context mContext;
    private final LayoutInflater mLayoutInflater;

    private final int mViewResId;
    private final int mLabelResId;
    private final int mStatusResId;

    private BluetoothA2dpCompat mAudioProxy;
    private OnClickListener mSettingsClickListener;

    public BluetoothListAdapter(Context context, int viewResId, int labelResId, int statusResId) {
        mContext = context;
        mViewResId = viewResId;
        mLabelResId = labelResId;
        mStatusResId = statusResId;

        mLayoutInflater = LayoutInflater.from(context);
    }

    public void setOnSettingsClickListener(OnClickListener listener) {
        mSettingsClickListener = listener;
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

            innerView.setId(R.id.list_entry);
            innerView.setOnClickListener(mSettingsClickListener);

            outerView.addView(innerView, 0, params);
            outerView.findViewById(R.id.device_settings).setOnClickListener(mSettingsClickListener);

            convertView = outerView;
        }

        final TextView labelView = (TextView) convertView.findViewById(mLabelResId);
        final TextView statusView = (TextView) convertView.findViewById(mStatusResId);

        final BluetoothDevice device = getItem(position);
        final String deviceName = getDeviceName(position);
        final int state = mAudioProxy.getConnectionState(device);
        final int statusResId = getResourceForDeviceState(state);

        // Ensure settings button has correct tag.
        convertView.findViewById(R.id.list_entry).setTag(R.id.tag_device, device);
        convertView.findViewById(R.id.device_settings).setTag(R.id.tag_device, device);

        labelView.setText(deviceName);
        statusView.setText(statusResId);

        return convertView;
    }

    private int getResourceForDeviceState(int state) {
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

        return 0;
    }

    public void setAudioProxy(BluetoothA2dpCompat audioProxy) {
        mAudioProxy = audioProxy;

        reloadDevices();
    }

    public void register() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothA2dpCompat.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);

        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    public void unregister() {
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    public String getDeviceName(int position) {
        return getDeviceName(getItem(position));
    }

    protected String getDeviceName(BluetoothDevice device) {
        return device.getName();
    }

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

        for (BluetoothDevice device : devices) {
            if (isDeviceVisible(device)) {
                mAudioDevices.add(device);
            }
        }

        notifyDataSetChanged();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadDevices();
        }
    };
}
