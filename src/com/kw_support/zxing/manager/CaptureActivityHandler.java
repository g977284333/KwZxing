package com.kw_support.zxing.manager;

import java.util.Collection;
import java.util.Map;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Browser;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.kw_support.R;
import com.kw_support.zxing.activity.CaptureActivity;
import com.kw_support.zxing.camera.CameraManager;
import com.kw_support.zxing.decode.DecodeThread;
import com.kw_support.zxing.interfaces.ViewfinderResultPointCallback;

/**
 * This class handles all the messaging which comprises the state machine for
 * capture.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CaptureActivityHandler extends Handler {
	private static final String TAG = CaptureActivityHandler.class.getSimpleName();

	private final CaptureActivity mActivity;

	private final DecodeThread mDecodeThread;

	private State mState;

	private final CameraManager mCameraManager;

	private enum State {
		PREVIEW, SUCCESS, DONE
	}

	public CaptureActivityHandler(CaptureActivity activity, Collection<BarcodeFormat> decodeFormats, Map<DecodeHintType, ?> baseHints, String characterSet, CameraManager cameraManager) {
		this.mActivity = activity;
		mDecodeThread = new DecodeThread(activity, decodeFormats, baseHints, characterSet, new ViewfinderResultPointCallback(activity.getViewfinderView()));
		mDecodeThread.start();
		mState = State.SUCCESS;

		// Start ourselves capturing previews and decoding.
		this.mCameraManager = cameraManager;
		cameraManager.startPreview();
		restartPreviewAndDecode();
	}

	@Override
	public void handleMessage(Message message) {
		switch (message.what) {
		case R.id.restart_preview:
			restartPreviewAndDecode();
			break;
		case R.id.decode_succeeded:
			mState = State.SUCCESS;
			Bundle bundle = message.getData();
			Bitmap barcode = null;
			float scaleFactor = 1.0f;
			
			if (bundle != null) {
				byte[] compressedBitmap = bundle.getByteArray(DecodeThread.BARCODE_BITMAP);
				
				if (compressedBitmap != null) {
					barcode = BitmapFactory.decodeByteArray(compressedBitmap, 0, compressedBitmap.length, null);
					// Mutable copy:
					barcode = barcode.copy(Bitmap.Config.ARGB_8888, true);
				}
				scaleFactor = bundle.getFloat(DecodeThread.BARCODE_SCALED_FACTOR);
			}
			mActivity.handleDecode((Result) message.obj, barcode, scaleFactor);
			break;
		case R.id.decode_failed:
			// We're decoding as fast as possible, so when one decode fails,
			// start another.
			mState = State.PREVIEW;
			mCameraManager.requestPreviewFrame(mDecodeThread.getHandler(), R.id.decode);
			break;
		case R.id.return_scan_result:
			mActivity.setResult(Activity.RESULT_OK, (Intent) message.obj);
			mActivity.finish();
			break;
		case R.id.launch_product_query:
			String url = (String) message.obj;

			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
			intent.setData(Uri.parse(url));

			ResolveInfo resolveInfo = mActivity.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
			String browserPackageName = null;
			
			if (resolveInfo != null && resolveInfo.activityInfo != null) {
				browserPackageName = resolveInfo.activityInfo.packageName;
				Log.d(TAG, "Using browser in package " + browserPackageName);
			}

			// Needed for default Android browser / Chrome only apparently
			if ("com.android.browser".equals(browserPackageName) || "com.android.chrome".equals(browserPackageName)) {
				intent.setPackage(browserPackageName);
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.putExtra(Browser.EXTRA_APPLICATION_ID, browserPackageName);
			}

			try {
				mActivity.startActivity(intent);
			} catch (ActivityNotFoundException ignored) {
				Log.w(TAG, "Can't find anything to handle VIEW of URI " + url);
			}
			break;
		}
	}

	public void quitSynchronously() {
		mState = State.DONE;
		mCameraManager.stopPreview();
		Message quit = Message.obtain(mDecodeThread.getHandler(), R.id.quit);
		quit.sendToTarget();
		
		try {
			// Wait at most half a second; should be enough time, and onPause()
			// will timeout quickly
			mDecodeThread.join(500L);
		} catch (InterruptedException e) {
			// continue
		}

		// Be absolutely sure we don't send any queued up messages
		removeMessages(R.id.decode_succeeded);
		removeMessages(R.id.decode_failed);
	}

	private void restartPreviewAndDecode() {
		if (mState == State.SUCCESS) {
			mState = State.PREVIEW;
			mCameraManager.requestPreviewFrame(mDecodeThread.getHandler(), R.id.decode);
			mActivity.drawViewfinder();
		}
	}

}
