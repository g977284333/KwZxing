package com.kw_support.zxing.activity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.kw_support.R;
import com.kw_support.zxing.camera.CameraManager;
import com.kw_support.zxing.constant.ZXingConfig;
import com.kw_support.zxing.decode.RGBLuminanceSource;
import com.kw_support.zxing.interfaces.FinishListener;
import com.kw_support.zxing.manager.AmbientLightManager;
import com.kw_support.zxing.manager.BeepManager;
import com.kw_support.zxing.manager.CaptureActivityHandler;
import com.kw_support.zxing.manager.InactivityTimer;
import com.kw_support.zxing.widget.ViewfinderView;

public final class CaptureActivity extends Activity implements SurfaceHolder.Callback {
	private static final String TAG = CaptureActivity.class.getSimpleName();

	public static final int HISTORY_REQUEST_CODE = 0x0000bacc;

	private CameraManager cameraManager;
	private BeepManager beepManager;
	private AmbientLightManager ambientLightManager;
	private MultiFormatReader multiFormatReader;

	private CaptureActivityHandler handler;
	private Result savedResultToShow;
	private ViewfinderView viewfinderView;
	private InactivityTimer inactivityTimer;
	private ProgressDialog mLoadingDialog;

	private Collection<BarcodeFormat> decodeFormats;
	private Map<DecodeHintType, ?> decodeHints;
	private String characterSet;

	private boolean hasSurface;
	private boolean isOpenedSplash;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_capture);

		hasSurface = false;
		inactivityTimer = new InactivityTimer(this);
		beepManager = new BeepManager(this);
		ambientLightManager = new AmbientLightManager(this);

		findViewById(R.id.btn_zxing_back).setOnClickListener(mBtnBackOnClickListener);
		findViewById(R.id.btn_zxing_light).setOnClickListener(mSplashBtnOnClickListener);
	}

	@Override
	protected void onResume() {
		super.onResume();

		// CameraManager must be initialized here, not in onCreate(). This is
		// necessary because we don't
		// want to open the camera driver and measure the screen size if we're
		// going to show the help on
		// first launch. That led to bugs where the scanning rectangle was the
		// wrong size and partially
		// off screen.
		cameraManager = new CameraManager(getApplication());

		viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
		viewfinderView.setCameraManager(cameraManager);

		handler = null;

		if (ZXingConfig.DISABLE_AUTO_ORIENTATION) {
			setRequestedOrientation(getCurrentOrientation());
		} else {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
		}

		resetStatusView();

		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			// The activity was paused but not stopped, so the surface still
			// exists. Therefore
			// surfaceCreated() won't be called, so init the camera here.
			initCamera(surfaceHolder);
		} else {
			// Install the callback and wait for surfaceCreated() to init the
			// camera.
			surfaceHolder.addCallback(this);
		}

		beepManager.updatePrefs();
		ambientLightManager.start(cameraManager);

		inactivityTimer.onResume();

		decodeFormats = null;
		characterSet = null;
	}

	private int getCurrentOrientation() {
		int rotation = getWindowManager().getDefaultDisplay().getRotation();
		switch (rotation) {
		case Surface.ROTATION_0:
		case Surface.ROTATION_90:
			return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
		default:
			return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
		}
	}

	@Override
	protected void onPause() {
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}

		inactivityTimer.onPause();
		ambientLightManager.stop();
		beepManager.close();
		cameraManager.closeDriver();

		if (!hasSurface) {
			SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
			SurfaceHolder surfaceHolder = surfaceView.getHolder();
			surfaceHolder.removeCallback(this);
		}

		super.onPause();
	}

	@Override
	protected void onDestroy() {
		inactivityTimer.shutdown();
		super.onDestroy();
	}

	private OnClickListener mBtnBackOnClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			finish();
		}
	};

	private OnClickListener mSplashBtnOnClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			openOrCloseSplash();
		}
	};

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (resultCode == RESULT_OK) {
			if (requestCode == 1) {
				Uri uri = intent.getData();

				if (uri == null) {
					return;
				}

				try {
					String[] projStrings = { MediaStore.Images.Media.DATA };
					Cursor cursor = getContentResolver().query(uri, projStrings, null, null, null);
					int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
					cursor.moveToFirst();
					final String path = cursor.getString(columnIndex);

					if (TextUtils.isEmpty(path)) {
						return;
					}

					Log.d(TAG, "ImagePath: " + path);
					
					
					mLoadingDialog = new ProgressDialog(CaptureActivity.this);
					mLoadingDialog.setMessage("稍后...");
					mLoadingDialog.setCancelable(false);
					mLoadingDialog.show();
					
					new Thread(new Runnable() {
						
						@Override
						public void run() {
							Result result = scanningImage(path);
							if(result == null) {
								mLoadingDialog.dismiss();
								// to do show failed
							} else {
								dealWithResult(result);
								mLoadingDialog.dismiss();
							}
						}
					}).start();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		}
	}

	private void openOrCloseSplash() {
		if (isOpenedSplash) {
			cameraManager.setTorch(false);
			isOpenedSplash = false;
		} else {
			cameraManager.setTorch(true);
			isOpenedSplash = true;
		}
	}

	private void decodeOrStoreSavedBitmap(Bitmap bitmap, Result result) {
		// Bitmap isn't used yet -- will be used soon
		if (handler == null) {
			savedResultToShow = result;
		} else {
			if (result != null) {
				savedResultToShow = result;
			}
			if (savedResultToShow != null) {
				Message message = Message.obtain(handler, R.id.decode_succeeded, savedResultToShow);
				handler.sendMessage(message);
			}
			savedResultToShow = null;
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (holder == null) {
			Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
		}
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

	}

	/**
	 * A valid barcode has been found, so give an indication of success and show
	 * the results.
	 * 
	 * @param rawResult
	 *            The contents of the barcode.
	 * @param scaleFactor
	 *            amount by which thumbnail was scaled
	 * @param barcode
	 *            A greyscale bitmap of the camera data which was decoded.
	 */
	public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
		inactivityTimer.onActivity();
		boolean fromLiveScan = barcode != null;
		if (fromLiveScan) {
			beepManager.playBeepSoundAndVibrate();
		}
		dealWithResult(rawResult);
	}

	private void dealWithResult(Result rawResult) {
		Toast.makeText(this, "success：" + rawResult.toString(), Toast.LENGTH_LONG).show();
		String Barcode = rawResult.getBarcodeFormat().toString();
		String Text = rawResult.getText().toString();
		if ("QR_CODE".equals(Barcode) || "DATA_MATRIX".equals(Barcode)) {
			if (Text.length() > 7) {
				String s = Text.substring(0, 7);
				if ("http://".equals(s)) {
					Intent viewIntent = new Intent("android.intent.action.VIEW", Uri.parse(Text));
					startActivity(viewIntent);

				} else {
					// to do something

				}
			} else {
				// to do something

			}

		} else if ("EAN_13".equals(Barcode)) {
			// to do something

		}
	}

	private void initCamera(SurfaceHolder surfaceHolder) {
		if (surfaceHolder == null) {
			throw new IllegalStateException("No SurfaceHolder provided");
		}
		if (cameraManager.isOpen()) {
			Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
			return;
		}
		try {
			cameraManager.openDriver(surfaceHolder);
			// Creating the handler starts the preview, which can also throw a
			// RuntimeException.
			if (handler == null) {
				handler = new CaptureActivityHandler(this, decodeFormats, decodeHints, characterSet, cameraManager);
			}
			decodeOrStoreSavedBitmap(null, null);
		} catch (IOException ioe) {
			Log.w(TAG, ioe);
			displayFrameworkBugMessageAndExit();
		} catch (RuntimeException e) {
			// Barcode Scanner has seen crashes in the wild of this variety:
			// java.?lang.?RuntimeException: Fail to connect to camera service
			Log.w(TAG, "Unexpected error initializing camera", e);
			displayFrameworkBugMessageAndExit();
		}
	}

	private void displayFrameworkBugMessageAndExit() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.app_name));
		builder.setMessage(getString(R.string.msg_camera_framework_bug));
		builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
		builder.setOnCancelListener(new FinishListener(this));
		builder.show();
	}

	public void restartPreviewAfterDelay(long delayMS) {
		if (handler != null) {
			handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
		}
		resetStatusView();
	}

	private void resetStatusView() {
		viewfinderView.setVisibility(View.VISIBLE);
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();
	}

	public ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	public CameraManager getCameraManager() {
		return cameraManager;
	}
	
	public Result scanningImage(String path) {
		if(TextUtils.isEmpty(path)){
			return null;
		}
		multiFormatReader = new MultiFormatReader();
	    
		//BufferedImage image =null;
		Hashtable<DecodeHintType, String> hints = new Hashtable<DecodeHintType, String>();
		hints.put(DecodeHintType.CHARACTER_SET, "UTF8"); 
		
		multiFormatReader.setHints(hints);
		
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		Bitmap scanBitmap = BitmapFactory.decodeFile(path, options);
		options.inJustDecodeBounds = false; 
		int sampleSize = (int) (options.outHeight / (float) 200);
		if (sampleSize <= 0)
			sampleSize = 1;
		scanBitmap = BitmapFactory.decodeFile(path, options);
		
		RGBLuminanceSource source = new RGBLuminanceSource(scanBitmap);
		BinaryBitmap bitmap1 = new BinaryBitmap(new HybridBinarizer(source));
	    try {
	    	return multiFormatReader.decodeWithState(bitmap1);
	    } catch (ReaderException re) {
	      // continue
	    } finally {
	      multiFormatReader.reset();
	    }
		return null;
	}
	public byte[] Bitmap2Bytes(Bitmap bm) {
		  ByteArrayOutputStream baos = new ByteArrayOutputStream();
		  bm.compress(Bitmap.CompressFormat.PNG, 100, baos);
		  return baos.toByteArray();
	} 
}
