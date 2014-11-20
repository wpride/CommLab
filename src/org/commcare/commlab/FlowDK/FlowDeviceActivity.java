package org.commcare.commlab.FlowDK;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;

import org.commcare.commlab.FlowDK.R;
import org.commcare.commlab.utilities.AcmDevice;
import org.commcare.commlab.utilities.AcmDevicePermissionCallback;
import org.commcare.commlab.utilities.HexUtils;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class FlowDeviceActivity extends Activity {

	private static final boolean DEBUG = true;

	public static final String ACTION_USB_PERMISSION = "org.commcarecommlab.USB_PERMISSION";
	
	AcmDevice flowDevice;

	// functional items
	private UsbManager usbManager;
	private PendingIntent usbPermissionIntent;
	private BroadcastReceiver usbDevicePermissionReceiver;
	private BroadcastReceiver usbDeviceDetachedReceiver;
	
	// views
	private TextView mStatusText;
	private TextView mResultText;
	private Button transferButton;
	private Button clearButton;


	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_main);
		
		// get views
		mStatusText = (TextView)findViewById(R.id.statusText);
		mResultText = (TextView)findViewById(R.id.resultText);
		transferButton = (Button)findViewById(R.id.transferButton);
		clearButton = (Button)findViewById(R.id.clearButton);
		
		// set click listeners
		transferButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) 
			{
				mStatusText.setText("Transferring...");
				String readData = initiateTransfer();
				int peakFlow = processRawData(readData);
				mResultText.setText("PeakFlow: " + peakFlow);
			}    
		});
		
		clearButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) 
			{
				mStatusText.setText("Clearing...");
				boolean cleared = initiateClear();
				if(cleared){
					mStatusText.setText("Successfully cleared device");
				} else{
					mStatusText.setText("Clear failed.");
				}
			}    
		});
		
		usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

		registerReceiver(usbDevicePermissionReceiver, new IntentFilter(ACTION_USB_PERMISSION));
		registerReceiver(usbDeviceDetachedReceiver, new IntentFilter(
				UsbManager.ACTION_USB_DEVICE_DETACHED));
		
		mStatusText.setText("Application initiated");

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
				mStatusText.setText("PeakFlow device attached successfully!");
			} else{
				mStatusText.setText("USB Device not recognized.");
			}
		}
	}
	
	/**
	 *  set the status text
	 * @param msg message to be displayed
	 * @param isError determines the coloring
	 */
	private void setStatusMessage(String msg, boolean isError){
		mStatusText.setText(msg);
		if(isError){
			mStatusText.setTextColor(Color.RED);
		} else{
			mStatusText.setTextColor(Color.BLACK);
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
			setStatusMessage("Error performing clear: " + e.getMessage(), true);
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
			setStatusMessage("Write failed: " + e.getMessage(), true);
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
			setStatusMessage("Read failed: " + e.getMessage(), true);
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
	private int processRawData(String rawData){
		
		// "7d" reliably demarcates each measurement
		String[] splitRawData = rawData.split("7d");
		
		int max = -1;
		for(String split: splitRawData){
			
			if(split.length() < 8) break;
			
			// little endian encoding, so this is how we pull out the values from the stream
			// measurements are reliably this distance from the end of the string
			char hundreds = split.charAt(split.length() - 5);
			char tens = split.charAt(split.length() - 8);
			char ones = split.charAt(split.length() - 7);
			
			String pfString = hundreds + "" + tens + "" + ones;
			
			try{
				int pfInteger = Integer.parseInt(pfString);
				if(pfInteger > max){
					max = pfInteger;
				}
			} catch(NumberFormatException nfe){
				// will happen sometimes for the trailing cruft of the last buffer; just ignore. 
				System.out.println("PFODK couldn't convert number: " + nfe.getMessage());
			}
		}
		return max;
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
			AcmDevice acmDevice = new AcmDevice(usbDeviceConnection, usbDevice);
			
			flowDevice = acmDevice;
			
		} catch(IllegalStateException e) {
			System.out.println("A precondition failed: " + e);
		} catch(IllegalArgumentException e) {
			System.out.println("Hey Failed to create ACM device: " + e + " : " + e.getMessage());
		}
	}

	@Override
	protected void onDestroy() {
		if (usbDeviceDetachedReceiver != null) {
			unregisterReceiver(usbDeviceDetachedReceiver);
		}
		if (usbDevicePermissionReceiver != null) {
			unregisterReceiver(usbDevicePermissionReceiver);
		}
		flowDevice.close();
		super.onDestroy();
	}
}
