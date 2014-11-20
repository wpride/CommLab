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

public class FlowDeviceActivity extends Activity implements AcmDevicePermissionCallback{

	private static final boolean DEBUG = true;

	public static final String ACTION_USB_PERMISSION = "org.ros.android.USB_PERMISSION";
	
	AcmDevice flowDevice;

	private UsbManager usbManager;
	private PendingIntent usbPermissionIntent;
	private BroadcastReceiver usbDevicePermissionReceiver;
	private BroadcastReceiver usbDeviceDetachedReceiver;
	
	private TextView mTextView;
	private TextView mResultText;
	private Button transferButton;
	private Button clearButton;



	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_main);
		
		mTextView = (TextView)findViewById(R.id.statusText);
		mResultText = (TextView)findViewById(R.id.resultText);
		transferButton = (Button)findViewById(R.id.transferButton);
		clearButton = (Button)findViewById(R.id.clearButton);
		
		transferButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) 
			{
				mTextView.setText("Transferring...");
				String readData = initiateTransfer();
				int peakFlow = processRawData(readData);
				mResultText.setText("PeakFlow: " + peakFlow);
			}    
		});
		
		clearButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) 
			{
				mTextView.setText("Clearing...");
				boolean cleared = initiateClear();
				if(cleared){
					mTextView.setText("Successfully cleared device");
				} else{
					mTextView.setText("Clear failed.");
				}
			}    
		});

		usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

		registerReceiver(usbDevicePermissionReceiver, new IntentFilter(ACTION_USB_PERMISSION));
		registerReceiver(usbDeviceDetachedReceiver, new IntentFilter(
				UsbManager.ACTION_USB_DEVICE_DETACHED));
		
		mTextView.setText("Application initiated");

		onUsbDeviceAttached(getIntent());
	}

	private void onUsbDeviceAttached(Intent intent) {
		if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
			UsbDevice usbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

			if(isPeakFlowMeter(usbDevice)){
				newAcmDevice(usbDevice);
				mTextView.setText("PeakFlow device attached successfully!");
			} else{
				mTextView.setText("USB Device not recognized.");
			}
		}
	}
	
	private boolean isPeakFlowMeter(UsbDevice usbDevice){
		return(usbDevice.getVendorId()==1204 && usbDevice.getProductId()==21760);
	}
	
	private boolean initiateClear(){
		
		if (DEBUG) {
			System.out.println("Initiating clear");
		}
		
		OutputStream mOutputStream = flowDevice.getOutputStream();
		
		byte[] firstWrite = {(byte)0x80,0x25,0x00,0x00,0x03};
		byte[] secondWrite = {0x03,0x2D,0x7B,0x7d,0x60,(byte)0xAD,0x64,(byte)0xFF};
		
		try {
			System.out.println("Trying write");
			mOutputStream.write(firstWrite);
			mOutputStream.write(secondWrite);
			mOutputStream.close();
			return true;
		} catch (IOException e) {
			System.out.println("Write failed");
			e.printStackTrace();
		}
		
		return false;
	}
	
	private String initiateTransfer(){
		
		if (DEBUG) {
			System.out.println("Initiating transfer");
		}
		
		OutputStream mOutputStream = flowDevice.getOutputStream();
		
		byte[] firstWrite = {(byte)0x80,0x25,0x00,0x00,0x03};
		byte[] secondWrite = {0x03,0x2F,0x7B,0x7D,(byte)0xD8,0x52,0x68,(byte)0xFF};
		
		try {
			System.out.println("Trying write");
			mOutputStream.write(firstWrite);
			mOutputStream.write(secondWrite);
			mOutputStream.close();
		} catch (IOException e) {
			System.out.println("Write failed");
			e.printStackTrace();
		}
		
		InputStream mInputStream = flowDevice.getInputStream();
		
		String readData = "";
		
		try {
			System.out.println("Trying read");
			
			byte [] buffer = new byte[8];
			int bytesRead = 0;
			while((bytesRead = mInputStream.read(buffer)) != -1) {
				
				byte[] newBuffer = Arrays.copyOfRange(buffer,1,8);
				
				readData = readData + HexUtils.byteArrayToHexString(newBuffer);
				System.out.println("PFODK old: " + HexUtils.byteArrayToHexString(buffer));
			    System.out.println("PFODK new: " + HexUtils.byteArrayToHexString(newBuffer));
			}
			mInputStream.close();
			
			System.out.println("PFODK Read: " + readData);
			
		} catch (IOException e) {
			System.out.println("Read failed");
			e.printStackTrace();
		}
		
		return readData;
	}
	
	private int processRawData(String rawData){
		
		String[] splitRawData = rawData.split("7d");
		
		int max = -1;
		
		for(String split: splitRawData){
			
			if(split.length() < 8) break;
			
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
				System.out.println("PFODK couldn't convert number: " + nfe.getMessage());
			}
			
			System.out.println("PFODK result: " + pfString);
			
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
			
			
			FlowDeviceActivity.this.onPermissionGranted(acmDevice);
		} catch(IllegalStateException e) {
			System.out.println("A precondition failed: " + e);
		} catch(IllegalArgumentException e) {
			System.out.println("Hey Failed to create ACM device: " + e + " : " + e.getMessage());
		}
	}

	protected Collection<UsbDevice> getUsbDevices(int vendorId, int productId) {
		Collection<UsbDevice> allDevices = usbManager.getDeviceList().values();
		Collection<UsbDevice> matchingDevices = Lists.newArrayList();
		for (UsbDevice device : allDevices) {
			if (device.getVendorId() == vendorId && device.getProductId() == productId) {
				matchingDevices.add(device);
			}
		}
		return matchingDevices;
	}

	@Override
	public void onPermissionGranted(AcmDevice acmDevice) {
		System.out.println("Permission granted.");

	}

	@Override
	public void onPermissionDenied() {
		System.out.println("Permission denied.");
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
