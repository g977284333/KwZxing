package com.kw_support.zxing;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageView;

import com.kw_support.R;
import com.kw_support.zxing.activity.CaptureActivity;
import com.kw_support.zxing.decode.QRCodeUtil;

public class MainActivity extends Activity {
	private static final String TAG = MainActivity.class.getSimpleName();

	private static final int SHOW_IMG = 1001;

	private EditText etUrl;
	private EditText etLogo;

	private ImageView ivQRCode;

	private String filePath;
	private String fileName;

	private Bitmap mLogo;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		filePath = Environment.getExternalStorageDirectory().getAbsoluteFile() + "/keepwalking";

		initView();
	}

	private void initView() {
		etUrl = (EditText) findViewById(R.id.et_url);
		etLogo = (EditText) findViewById(R.id.et_logo);

		etLogo.setText(filePath + "/" + "1206.jpg");

		ivQRCode = (ImageView) findViewById(R.id.iv_qr_code);
		ivQRCode.setOnClickListener(mQRCodeOnClickListener);
	}

	public void skipToCaptureActivity(View v) {
		Intent intent = new Intent(this, CaptureActivity.class);
		startActivity(intent);
	}

	private OnClickListener mQRCodeOnClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			final String url = etUrl.getText().toString().trim();
			final String logo = etLogo.getText().toString().trim();

			if (TextUtils.isEmpty(url)) {
				return;
			}

			if (!TextUtils.isEmpty(logo)) {
				mLogo = BitmapFactory.decodeFile(logo);
			}

			fileName = filePath + "/" + url + ".jpeg";

			new Thread(new Runnable() {

				@Override
				public void run() {
					QRCodeUtil.createQRImage(url, 150, 150, mLogo, fileName);

					Message msg = Message.obtain();
					msg.what = SHOW_IMG;
					mHander.sendMessage(msg);
				}
			}).start();

		}
	};

	private void showQRCode() {
		try {
			Bitmap QRCode = BitmapFactory.decodeFile(fileName);
			if (QRCode == null) {
				Log.d(TAG, "create QRCode failed");
				return;
			}
			ivQRCode.setImageBitmap(QRCode);
		} catch (Exception e) {
			Log.d(TAG, "create QRCode failed");
		}
	}

	private Handler mHander = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case SHOW_IMG:
				showQRCode();
				break;

			default:
				break;
			}
		};
	};
}
