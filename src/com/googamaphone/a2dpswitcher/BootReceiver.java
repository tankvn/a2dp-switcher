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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean showNotification = prefs.getBoolean(BluetoothSwitcherService.PREF_NOTIFY,
                BluetoothSwitcherService.PREF_NOTIFY_DEFAULT);

        if (showNotification) {
            final Intent serviceIntent = new Intent(context, BluetoothSwitcherService.class);
            context.startService(serviceIntent);
        }
    }
}
