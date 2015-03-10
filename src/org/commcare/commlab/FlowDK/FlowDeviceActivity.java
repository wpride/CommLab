package org.commcare.commlab.FlowDK;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.commcare.commlab.utilities.HexUtils;
import org.commcare.commlab.utilities.PeakFlowDevice;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.widget.Toast;

import com.google.common.base.Preconditions;

/***
 * Activity for handling communication between Android device and PeakFlow meter. On each plug in
 * of the meter, this activity initiates communication with device, pulls the data from the USB device,
 * determines the highest PeakFlow value, stores this value in SharedPreferences, then clears the device.
 * 
 * @author wspride
 *
 */

public class FlowDeviceActivity extends Activity {

	private static final boolean DEBUG = false;

	public static final String ACTION_USB_PERMISSION = "org.commcarecommlab.USB_PERMISSION";
	
	// strings for storing the peakflow value
	public static final String PREFS_NAME = "MyPrefsFile";
	public static final String PEAKFLOW_VALUE_KEY = "peakflow-answer";

	// the current peak flow device
	PeakFlowDevice flowDevice;

	// functional items
	private UsbManager usbManager;
	private PendingIntent usbPermissionIntent;
	private BroadcastReceiver usbDevicePermissionReceiver;


	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// initialize the needed android resources
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

		onUsbDeviceAttached(getIntent());
	}

	/**
	 * Called when a new USB device is connected. Check if its peak flow and config if so
	 * @param intent OS generated intent with information about the device connection
	 */
	private void onUsbDeviceAttached(Intent intent) {
		if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
			UsbDevice usbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
			if(isPeakFlowMeter(usbDevice)){
				newAcmDevice(usbDevice);
				
				String answer = initiateTransfer();
				// peak flow values in a comma separated string
				String answerString = processRawData(answer);
				
				// only clear device if we successfully save the answer
				if(storePeakFlowValue(answerString)){
					Toast.makeText(getApplicationContext(), "Answer: " + answerString, Toast.LENGTH_LONG).show();
					initiateClear();
				} else{
					Toast.makeText(getApplicationContext(), "Serious error! Couldn't store value: " +  answer, Toast.LENGTH_LONG).show();
				}
			}
		}
		finish();
	}
	
	/*
	 * Store the value @answer in the SharedPreferences. 
	 * return true if the save is successful, false otherwise
	 */
	private boolean storePeakFlowValue(String answer){	
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(FlowDeviceActivity.PEAKFLOW_VALUE_KEY, answer);
		
		if(editor.commit()){
			return true;
		}else{
			return false;
		}
	}

	/**
	 * @param usbDevice Usb Device to be tested
	 * @return true if the usbDevice is the MicroLife Peak Flow Meter
	 */
	private boolean isPeakFlowMeter(UsbDevice usbDevice){
		return(usbDevice.getVendorId()==1204 && usbDevice.getProductId()==21760);
	}

	/**
	 *  send the clear command to the device to return it to its fresh state
	 * @return true if the clear was successful
	 */
	private boolean initiateClear(){

		OutputStream mOutputStream = flowDevice.getOutputStream();

		// clear messages
		byte[] firstWrite = {(byte)0x80,0x25,0x00,0x00,0x03};
		byte[] secondWrite = {0x03,0x2D,0x7B,0x7d,0x60,(byte)0xAD,0x64,(byte)0xFF};

		try {
			mOutputStream.write(firstWrite);
			mOutputStream.write(secondWrite);
			return true;
		} catch (IOException e) {
		} finally{
			try{
				mOutputStream.close();
			} catch(IOException e){
				//ignore
			}
		}

		return false;
	}

	/**
	 * Initiate the transfer from the PeakFlow device to the Android device.
	 * @return the raw bytes read from the buffer as a String
	 */

	private String initiateTransfer(){

		OutputStream mOutputStream = flowDevice.getOutputStream();

		byte[] firstWrite = {(byte)0x80,0x25,0x00,0x00,0x03};
		byte[] secondWrite = {0x03,0x2F,0x7B,0x7D,(byte)0xD8,0x52,0x68,(byte)0xFF};

		try {
			mOutputStream.write(firstWrite);
			mOutputStream.write(secondWrite);
			mOutputStream.close();
		} catch (IOException e) {
		}

		InputStream mInputStream = flowDevice.getInputStream();

		String readData = "";

		try {
			byte [] buffer = new byte[8];
			int bytesRead = 0;
			while((bytesRead = mInputStream.read(buffer)) != -1) {
				byte[] newBuffer = Arrays.copyOfRange(buffer,1,8);
				readData = readData + HexUtils.byteArrayToHexString(newBuffer);
			}
		} catch (IOException e) {
			
			if(readData.length()<8){
			}
		} finally{
			try{
				mInputStream.close();
			} catch (IOException ioex) {
				//omitted.
			}
		}
		return readData;
	}

	/**
	 * Process the raw data from the usb read (converted to a String from a byte array)
	 * Return the maximum reading (which is the only thing we care about)
	 * @param rawData the rawData read from the device
	 * @return the maximum peak flow reading
	 */
	private String processRawData(String rawData){

		// "7d" reliably demarcates each measurement
		String[] splitRawData = rawData.split("7d");

		int max = -1;
		String accumulator ="";
		
		for(String split: splitRawData){

			if(split.length() < 16) break;

			// little endian encoding, so this is how we pull out the values from the stream
			// measurements are reliably this distance from the end of the string
			char hundreds = split.charAt(split.length() - 5);
			char tens = split.charAt(split.length() - 8);
			char ones = split.charAt(split.length() - 7);

			String pfString = hundreds + "" + tens + "" + ones;

			try{
				int pfInteger = Integer.parseInt(pfString);
				accumulator += pfString + ",";
			} catch(NumberFormatException nfe){
				// will happen sometimes for the trailing cruft of the last buffer; just ignore. 
				System.out.println("PFODK couldn't convert number: " + nfe.getMessage());
			}
		}
		return accumulator.substring(0,accumulator.length()-1);
	}

	/**
	 * Creates a new AcmDevice for the newly connected
	 */
	private void newAcmDevice(UsbDevice usbDevice) {
		try {
			Preconditions.checkNotNull(usbDevice);
			String deviceName = usbDevice.getDeviceName();

			Preconditions.checkState(usbManager.hasPermission(usbDevice), "Permission denied: "
					+ deviceName);

			UsbDeviceConnection usbDeviceConnection = usbManager.openDevice(usbDevice);
			Preconditions.checkNotNull(usbDeviceConnection, "Failed to open device: " + deviceName);
			if (DEBUG) {
				System.out.println("Adding new ACM device: " + deviceName);
			}
			PeakFlowDevice acmDevice = new PeakFlowDevice(usbDeviceConnection, usbDevice);

			flowDevice = acmDevice;

		} catch(IllegalStateException e) {
			System.out.println("A precondition failed: " + e);
		} catch(IllegalArgumentException e) {
			System.out.println("Hey Failed to create ACM device: " + e + " : " + e.getMessage());
		}
	}

	@Override
	protected void onDestroy() {
		if (usbDevicePermissionReceiver != null) {
			unregisterReceiver(usbDevicePermissionReceiver);
		}
		flowDevice.close();
		super.onDestroy();
	}
}
