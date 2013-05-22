
package com.googamaphone.compat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.AsyncTask;

import com.googamaphone.compat.BluetoothA2dpCompat.BluetoothA2dpStubCallback;
import com.googamaphone.compat.BluetoothA2dpCompat.BluetoothA2dpStubImpl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class BluetoothA2dpGingerbreadImpl extends BluetoothA2dpStubImpl {
    public static final String ACTION_SINK_STATE_CHANGED =
            "android.bluetooth.a2dp.action.SINK_STATE_CHANGED";
    public static final String EXTRA_STATE =
            "android.bluetooth.a2dp.extra.SINK_STATE";
    public static final String EXTRA_PREVIOUS_STATE =
            "android.bluetooth.a2dp.extra.PREVIOUS_SINK_STATE";

    private static final Class<?> CLASS_BluetoothA2dp = CompatUtils
            .getClass("android.bluetooth.BluetoothA2dp");
    private static final Constructor<?> CONSTRUCTOR_BluetoothA2dp = CompatUtils.getConstructor(
            CLASS_BluetoothA2dp, Context.class);
    private static final Method METHOD_connectSink = CompatUtils.getMethod(CLASS_BluetoothA2dp,
            "connectSink", BluetoothDevice.class);
    private static final Method METHOD_disconnectSink = CompatUtils.getMethod(CLASS_BluetoothA2dp,
            "disconnectSink", BluetoothDevice.class);
    private static final Method METHOD_getSinkState = CompatUtils.getMethod(CLASS_BluetoothA2dp,
            "getSinkState", BluetoothDevice.class);
    private static final Method METHOD_getNonDisconnectedSinks = CompatUtils.getMethod(
            CLASS_BluetoothA2dp, "getNonDisconnectedSinks");

    @Override
    public boolean obtain(Context context, final BluetoothA2dpStubCallback callback) {
        new ConnectProxyTask(context) {
            @Override
            protected void onPostExecute(Object result) {
                if (result != null) {
                    callback.onProxyConnected(result);
                } else {
                    callback.onProxyDisconnected();
                }
            }
        }.execute();

        return true;
    }

    @Override
    public boolean connect(Object receiver, BluetoothDevice device) {
        final Set<BluetoothDevice> sinks = getNonDisconnectedSinks(receiver);

        // To meet the API specification, we need to disconnect all connected A2DP devices.
        for (BluetoothDevice sink : sinks) {
            disconnect(receiver, sink);
        }

        return (Boolean) CompatUtils.invoke(receiver, false, METHOD_connectSink, device);
    }

    @Override
    public boolean disconnect(Object receiver, BluetoothDevice device) {
        return (Boolean) CompatUtils.invoke(receiver, false, METHOD_disconnectSink, device);
    }

    @Override
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(Object receiver, int[] states) {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return Collections.emptyList();
        }

        final Set<BluetoothDevice> devices = adapter.getBondedDevices();
        if (devices == null) {
            return Collections.emptyList();
        }

        final List<BluetoothDevice> result = new ArrayList<BluetoothDevice>(devices.size());
        final long bitmask = collapseValuesToBitmask(states);

        for (BluetoothDevice device : devices) {
            if (!doesDeviceSuportA2dp(device)) {
                continue;
            }

            final int sinkState = getConnectionState(receiver, device);
            if (!checkBitmaskForValue(bitmask, sinkState)) {
                continue;
            }

            result.add(device);
        }

        return result;
    }

    @Override
    public int getConnectionState(Object receiver, BluetoothDevice device) {
        return (Integer) CompatUtils.invoke(receiver, BluetoothA2dpCompat.STATE_DISCONNECTED,
                METHOD_getSinkState, device);
    }

    @Override
    public String getStateChangedAction() {
        return ACTION_SINK_STATE_CHANGED;
    }

    @Override
    public String getExtraState() {
        return EXTRA_STATE;
    }

    @Override
    public String getExtraPreviousState() {
        return EXTRA_PREVIOUS_STATE;
    }

    @SuppressWarnings("unchecked")
    private Set<BluetoothDevice> getNonDisconnectedSinks(Object receiver) {
        return (Set<BluetoothDevice>) CompatUtils.invoke(receiver, null,
                METHOD_getNonDisconnectedSinks);
    }

    private static boolean doesDeviceSuportA2dp(BluetoothDevice device) {
        final BluetoothClass deviceClass = device.getBluetoothClass();
        if (deviceClass == null) {
            return false;
        }

        if (deviceClass.hasService(BluetoothClass.Service.RENDER)) {
            return true;
        }

        switch (deviceClass.getDeviceClass()) {
            case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO:
            case BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES:
            case BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO:
            case BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER:
                return true;
        }

        return false;
    }

    private static long collapseValuesToBitmask(int[] values) {
        long bitmask = 0;

        for (int value : values) {
            if (value < 0 || value > 30) {
                throw new IllegalArgumentException("Valid range is 0 <= value <= 30");
            }

            bitmask |= (1 << value);
        }

        return bitmask;
    }

    private static boolean checkBitmaskForValue(long bitmask, int value) {
        if (value < 0 || value > 30) {
            throw new IllegalArgumentException("Valid range is 0 <= value <= 30");
        }

        final long shifted = (1 << value);

        return ((bitmask & shifted) == shifted);
    }

    private static class ConnectProxyTask extends AsyncTask<Void, Void, Object> {
        private final Context mContext;

        public ConnectProxyTask(Context context) {
            mContext = context;
        }

        @Override
        protected Object doInBackground(Void... params) {
            return CompatUtils.newInstance(CONSTRUCTOR_BluetoothA2dp, mContext);
        }
    }
}
