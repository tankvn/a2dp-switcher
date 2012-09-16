
package com.googamaphone.a2dpswitcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean checked = prefs.getBoolean(BluetoothSwitcherService.PREF_NOTIFY,
                BluetoothSwitcherService.PREF_NOTIFY_DEFAULT);

        if (checked) {
            final Intent serviceIntent = new Intent(context, BluetoothSwitcherService.class);
            context.startService(serviceIntent);
        }
    }
}
