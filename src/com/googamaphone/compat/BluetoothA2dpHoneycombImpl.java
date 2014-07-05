
package com.googamaphone.compat;

import java.lang.reflect.Method;
import java.util.List;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.Context;

import com.googamaphone.compat.BluetoothA2dpCompat.BluetoothA2dpStubCallback;
import com.googamaphone.compat.BluetoothA2dpCompat.BluetoothA2dpStubImpl;

@TargetApi(11)
class BluetoothA2dpHoneycombImpl extends BluetoothA2dpStubImpl {
    private static final Method METHOD_connect = CompatUtils.getMethod(BluetoothA2dp.class,
            "connect", BluetoothDevice.class);
    private static final Method METHOD_disconnect = CompatUtils.getMethod(BluetoothA2dp.class,
            "disconnect", BluetoothDevice.class);

    @Override
    public boolean obtain(Context context, final BluetoothA2dpStubCallback callback) {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return false;
        }

        final ServiceListener serviceListener = new ServiceListener() {
            @Override
            public void onServiceDisconnected(int profile) {
                callback.onProxyDisconnected();
            }

            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                callback.onProxyConnected(proxy);
            }
        };

        adapter.getProfileProxy(context, serviceListener, BluetoothProfile.A2DP);

        return true;
    }

    @Override
    public boolean connect(Object receiver, BluetoothDevice device) {
        return (Boolean) CompatUtils.invoke(receiver, false, METHOD_connect, device);
    }

    @Override
    public boolean disconnect(Object receiver, BluetoothDevice device) {
        return (Boolean) CompatUtils.invoke(receiver, false, METHOD_disconnect, device);
    }

    @Override
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(Object receiver, int[] states) {
        return ((BluetoothA2dp) receiver).getDevicesMatchingConnectionStates(states);
    }

    @Override
    public int getConnectionState(Object receiver, BluetoothDevice device) {
        return ((BluetoothA2dp) receiver).getConnectionState(device);
    }

    @Override
    public void shutdown(Object receiver) {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter == null) {
            return;
        }

        adapter.closeProfileProxy(BluetoothProfile.A2DP, (BluetoothA2dp) receiver);
    }

    @Override
    public String getStateChangedAction() {
        return BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED;
    }

    @Override
    public String getExtraState() {
        return BluetoothA2dp.EXTRA_STATE;
    }

    @Override
    public String getExtraPreviousState() {
        return BluetoothA2dp.EXTRA_PREVIOUS_STATE;
    }
}
