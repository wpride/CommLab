/*
 * Copyright (C) 2011 Google Inc.
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

package org.commcare.commlabs.utilities;

import java.util.Map;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
public final class UsbDeviceDetachedReceiver extends BroadcastReceiver {

  private static final boolean DEBUG = true;

  private final Map<String, AcmDevice> acmDevices;

  public UsbDeviceDetachedReceiver(Map<String, AcmDevice> acmDevices) {
    this.acmDevices = acmDevices;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    UsbDevice usbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    String deviceName = usbDevice.getDeviceName();
    AcmDevice acmDevice = acmDevices.remove(deviceName);
    if (acmDevice != null) {
      try {
        acmDevice.close();
      } catch (RuntimeException e) {
        // Ignore spurious errors on disconnect.
      }
    }
    if (DEBUG) {
    	System.out.println("USB device removed: " + deviceName);
    }
  }
}