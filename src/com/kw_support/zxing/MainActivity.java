package com.kw_support.zxing;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.kw_support.R;
import com.kw_support.zxing.activity.CaptureActivity;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}
	
	public void skipToCaptureActivity(View v) {
		Intent intent = new Intent(this,CaptureActivity.class);
		startActivity(intent);
	}
}
