package org.commcare.commlab.FlowDK;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class CommCareReceiverActivity extends Activity {
	
	public Button returnButton;
	public String mAnswer;
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		returnButton = (Button)findViewById(R.id.returnButton);
		
		FlowDeviceActivity mFlowDeviceActivity = FlowDeviceActivity.mFlowDeviceActivity;
		mAnswer = mFlowDeviceActivity.getPeakFlow();
		
		returnButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) 
			{
				sendAnswerBackToApp(mAnswer);
			}    
		});
		
	}
	
	private void sendAnswerBackToApp(String mAnswer) {
		Intent intent = new Intent();
		intent.putExtra("odk_intent_data", String.valueOf(mAnswer));
		setResult(RESULT_OK, intent);
		finish();
	}
	
	
}
