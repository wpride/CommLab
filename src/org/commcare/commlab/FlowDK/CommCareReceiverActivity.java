package org.commcare.commlab.FlowDK;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * Simple activity for managing communication between FlowDeviceActivity and 
 * CommCareODK activity lifecycles
 * 
 * @author wspride
 */
public class CommCareReceiverActivity extends Activity {
	
	public Button returnButton;
	public TextView statusView;
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_receiver);
		
		returnButton = (Button)findViewById(R.id.receiverReturnButton);
		statusView = (TextView)findViewById(R.id.receiverStatusText);
		
		/*
		 * Lookup peak flow value in shared prefs. If exists, return it to ccodk. if not, notify.
		 */
		returnButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) 
			{
			    SharedPreferences settings = getSharedPreferences(FlowDeviceActivity.PREFS_NAME, 0);
			    int answer = settings.getInt(FlowDeviceActivity.PEAKFLOW_VALUE_KEY, -1);
			    
			    if(answer <0){
			    	statusView.setText("Couldn't find any peak flow value.");
			    }
			    else{
			    	sendAnswerBackToApp(""+answer);
			    }
			}    
		});
		
	}
	
	/*
	 * send answer back to CCODK
	 */
	private void sendAnswerBackToApp(String mAnswer) {
		Intent intent = new Intent();
		intent.putExtra("odk_intent_data", mAnswer);
		setResult(RESULT_OK, intent);
		finish();
	}
	
	
}
