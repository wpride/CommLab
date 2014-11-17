package org.commcare.FlowDK;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import aws.apps.usbDeviceEnumerator.data.DbAccessCompany;
import aws.apps.usbDeviceEnumerator.data.DbAccessUsb;
import aws.apps.usbDeviceEnumerator.data.ZipAccessCompany;
import aws.apps.usbDeviceEnumerator.usb.sysbususb.SysBusUsbManager;
import aws.apps.usbDeviceEnumerator.util.UsefulBits;


public class MainActivity extends ActionBarActivity {

	private TextView mTextView;

	private UsbManager mUsbManAndroid;

	private HashMap<String, UsbDevice> mAndroidUsbDeviceList;

	private Button transferButton;
	private Button clearButton;
	private Button permissionButton;

	private BroadcastReceiver mUsbReceiver;

	UsbDevice mDevice;

	protected final Object mReadBufferLock = new Object();

	/** Internal read buffer.  Guarded by {@link #mReadBufferLock}. */
	protected byte[] mReadBuffer;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mTextView = (TextView)findViewById(R.id.statusText);
		transferButton = (Button)findViewById(R.id.transferButton);
		clearButton = (Button)findViewById(R.id.clearButton);
		permissionButton = (Button)findViewById(R.id.action_permission);

		mUsbManAndroid = (UsbManager) getSystemService(Context.USB_SERVICE);

		transferButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) 
			{
				try {
					transfer();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}    
		});


		mUsbReceiver = new BroadcastReceiver() {
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				System.out.println("FlowDK BroadcastReceiver Event");
				if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
					/*
			        mSerial.usbAttached(intent);
			        mSerial.begin(mBaudrate);
			        loadDefaultSettingValues();
			        Run=true;
			        start();
					 */
					System.out.println("FlowDK BroadcastReceiver USB Connected");

					mAndroidUsbDeviceList = mUsbManAndroid.getDeviceList();

					System.out.println("Flowdk device list: " + mAndroidUsbDeviceList.toString());

					String[] array = mAndroidUsbDeviceList.keySet().toArray(new String[mAndroidUsbDeviceList.keySet().size()]);

					System.out.println("Flowdk size: " + array.length);

					if(array.length < 1){
						System.out.println("FlowDK Array length < 1: " + array.length);
						return;
					}

					String deviceName = array[0];

					System.out.println("FlowDK device name: " + deviceName);

					mDevice = mAndroidUsbDeviceList.get(deviceName);

					System.out.println("FlowDK mDevice: " + mDevice.toString());

					System.out.println("FlowDK mDeviceInterface: " + mDevice.getInterface(0));

					mTextView.setText("Device: " + mDevice.getDeviceName() + " id: " + mDevice.getDeviceId());

					try {
						transfer();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				} else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
					System.out.println("FlowDK BroadcastReceiver USB Disconnected");
				}
			}
		};

		IntentFilter filter = new IntentFilter();
		filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		this.registerReceiver(mUsbReceiver, filter);

	}


	public void transfer2Helper(){
		System.out.println("FlowDK transfer");

		int count = mDevice.getInterfaceCount();

		System.out.println("FlowDK count: " + count);

		UsbInterface mInterface = mDevice.getInterface(0);
		UsbEndpoint outEndpoint = mInterface.getEndpoint(0);
		UsbEndpoint inEndpoint = mInterface.getEndpoint(1);

		System.out.println("FlowDK outpoint: " + outEndpoint);
		System.out.println("FlowDK inPoint: " + inEndpoint);

		UsbDeviceConnection mConnection = mUsbManAndroid.openDevice(mDevice);
		
		byte[] dest = new byte[64];
		Arrays.fill(dest, (byte) 0);
		
		transfer2(dest, 3000, mConnection, outEndpoint, inEndpoint);
	}


	public void transfer2(byte[] dest, int timeoutMillis, UsbDeviceConnection mConnection, UsbEndpoint mEndpointOut, UsbEndpoint mEndpointIn) {

		int bufferMaxLength=mEndpointOut.getMaxPacketSize();
		ByteBuffer buffer = ByteBuffer.allocate(bufferMaxLength);
		UsbRequest request = new UsbRequest(); // create an URB
		request.initialize(mConnection, mEndpointOut);
		buffer.put(hexStringToByteArray("8025000003032F7B7DD85268FF"));

		// queue the outbound request
		boolean retval = request.queue(buffer, 1); 
		if (mConnection.requestWait() == request) { 
			// wait for confirmation (request was sent)
			UsbRequest inRequest = new UsbRequest(); 
			// URB for the incoming data
			inRequest.initialize(mConnection, mEndpointIn); 
			// the direction is dictated by this initialisation to the incoming endpoint.
			if(inRequest.queue(buffer, bufferMaxLength) == true){
				mConnection.requestWait(); 
				mprint("transfer2 finished");
				mprint("transfer2 bytes: " + buffer.capacity() + " : " + buffer);
				for(int i=0; i< buffer.capacity(); i++){
					mprint("transfer2 buffer: " + buffer.get(i));
				}
			}
		}
	}

	public void transfer() throws IOException{

		System.out.println("FlowDK transfer");

		int count = mDevice.getInterfaceCount();

		System.out.println("FlowDK count: " + count);

		UsbInterface mInterface = mDevice.getInterface(0);
		UsbEndpoint outEndpoint = mInterface.getEndpoint(0);
		UsbEndpoint inEndpoint = mInterface.getEndpoint(1);

		System.out.println("FlowDK outpoint: " + outEndpoint);
		System.out.println("FlowDK inPoint: " + inEndpoint);
		
		int bufferMaxLength=outEndpoint.getMaxPacketSize();

		UsbDeviceConnection mConnection = mUsbManAndroid.openDevice(mDevice);
		mConnection.claimInterface(mInterface, true);

		final UsbRequest request = new UsbRequest();
		request.initialize(mConnection, outEndpoint);

		byte[] send = hexStringToByteArray("8025000003032F7B7DD85268FF");

		final ByteBuffer buf = ByteBuffer.wrap(send);
		if (!request.queue(buf, send.length)) {
			throw new IOException("Error queueing request.");
		}
		
		byte[] read = read(mConnection, inEndpoint);

		System.out.println("FlowDK read: " + read);

		for(int i = 0; i< read.length; i++){
			System.out.println("FlowDK Read: " + read[i]);
		}

	}
	
	public int write(UsbDeviceConnection mConnection, UsbEndpoint mWriteEndpoint){
		
		int amtWritten;
		
		byte[] sendOne = {(byte)0x80,0x25,0x00,0x00,0x03};
		byte[] sendTwo = {0x03,0x2F,0x7B,0x7D,(byte)0xD8,0x52,0x68,(byte)0xFF};
		
		byte[] writeBufferOne = new byte[sendOne.length];
		byte[] writeBufferTwo = new byte[sendTwo.length];
		
        amtWritten = mConnection.bulkTransfer(mWriteEndpoint, writeBufferOne, writeBufferOne.length,
                5000);
        
        System.out.println("FlowDK amtWritten 1: " + amtWritten);
        
        amtWritten = mConnection.bulkTransfer(mWriteEndpoint, writeBufferTwo, writeBufferTwo.length,
                5000);
        
        System.out.println("FlowDK amtWritten 2: " + amtWritten);
		
		return -1;
	}

	public int read(byte[] dest, int timeoutMillis, UsbDeviceConnection mConnection, UsbEndpoint mReadEndpoint) throws IOException {

		final UsbRequest request = new UsbRequest();
		try {
			request.initialize(mConnection, mReadEndpoint);
			final ByteBuffer buf = ByteBuffer.wrap(dest);
			if (!request.queue(buf, dest.length)) {
				throw new IOException("Error queueing request.");
			}

			final UsbRequest response = mConnection.requestWait();
			if (response == null) {
				throw new IOException("Null response");
			}

			final int nread = buf.position();
			if (nread > 0) {
				//Log.d(TAG, HexDump.dumpHexString(dest, 0, Math.min(32, dest.length)));
				return nread;
			} else {
				return 0;
			}
		} finally {
			request.close();
		}
	}

	public void mprint(String txt){
		System.out.println("FlowDK: " + txt);
	}

	public byte[] read(UsbDeviceConnection connection, UsbEndpoint input)
	{
		
		// reinitialize read value byte array
		byte[] readBytes = new byte[32];
		Arrays.fill(readBytes, (byte) 0);

		// wait for some data from the mcu


		int recvBytes = connection.bulkTransfer(input, readBytes, readBytes.length, 3000);

		mprint("recvBytes: " + recvBytes);


		if(recvBytes > 0)
		{
			System.out.println("FlowDK Got some data: " + new String(readBytes));
		}
		else
		{
			System.out.println("FlowDK Did not get any data: " + recvBytes);
		}

		//return Integer.toString(recvBytes);
		
		byte[] dest = new byte[recvBytes];
		
		System.arraycopy(readBytes, 0, dest, 0, recvBytes);
		
		return dest;
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		if (id == R.id.action_refresh) {
			//refreshUsbDevices();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onStop()
	{
		unregisterReceiver(mUsbReceiver);
		super.onStop();
	}

	public byte[] hexConvert(String input){
		byte[] bytes = new byte[input.length() / 2];

		for( int i = 0; i < input.length(); i+=2)
		{
			bytes[i/2] = Integer.decode( "0x" + input.substring( i, i + 2 )  ).byteValue();
		}
		return bytes;
	}

	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
					+ Character.digit(s.charAt(i+1), 16));
		}
		return data;
	}

	
	/*
	private void refreshUsbDevices(){

		// Getting devices from API
		{
			mAndroidUsbDeviceList = mUsbManAndroid.getDeviceList();
			String[] array = mAndroidUsbDeviceList.keySet().toArray(new String[mAndroidUsbDeviceList.keySet().size()]);

			if(array.length < 1){return;}

			String deviceName = array[0];

			mDevice = mAndroidUsbDeviceList.get(deviceName);

			Arrays.sort(array);

			mTextView.setText("Device: " + mDevice.getDeviceName() + " id: " + mDevice.getDeviceId());

			try {
				transfer();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	 */
}
