/*
 * Copyright (C) 2012 Google Inc.
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

package com.googamaphone.compat;

import java.lang.reflect.Method;

import android.bluetooth.BluetoothDevice;

public class BluetoothDeviceCompatUtils {
    private static final Class<?> CLASS_BluetoothDevice = BluetoothDevice.class;
    private static final Method METHOD_createBond = CompatUtils.getMethod(CLASS_BluetoothDevice,
            "createBond");

    /**
     * Start the bonding (pairing) process with the remote device.
     * <p>
     * This is an asynchronous call, it will return immediately. Register for
     * {@link BluetoothDevice#ACTION_BOND_STATE_CHANGED} intents to be notified
     * when the bonding process completes, and its result.
     * <p>
     * Android system services will handle the necessary user interactions to
     * confirm and complete the bonding process.
     * <p>
     * Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}.
     *
     * @return false on immediate error, true if bonding will begin
     */
    public static boolean createBond(BluetoothDevice receiver) {
        return (Boolean) CompatUtils.invoke(receiver, false, METHOD_createBond);
    }
}
