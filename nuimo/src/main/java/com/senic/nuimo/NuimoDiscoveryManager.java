/*
 * Copyright (c) 2015 Senic. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package com.senic.nuimo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;

public class NuimoDiscoveryManager {

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    public NuimoDiscoveryManager(Context context) {
        mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
    }

}
