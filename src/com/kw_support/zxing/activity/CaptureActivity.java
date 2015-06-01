package com.kw_support.zxing.activity;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.kw_support.R;
import com.kw_support.zxing.camera.CameraManager;
import com.kw_support.zxing.constant.Intents;
import com.kw_support.zxing.constant.ZXingConfig;
import com.kw_support.zxing.decode.DecodeFormatManager;
import com.kw_support.zxing.manager.AmbientLightManager;
import com.kw_support.zxing.manager.BeepManager;
import com.kw_support.zxing.manager.CaptureActivityHandler;
import com.kw_support.zxing.manager.InactivityTimer;
import com.kw_support.zxing.widget.ViewfinderView;

/**
 * This activity opens the camera and does the actual scanning on a background
 * thread. It draws a viewfinder to help the user place the barcode correctly,
 * shows feedback as the image processing is happening, and then overlays the
 * results when a scan is successful.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class CaptureActivity extends Activity implements SurfaceHolder.Callback {

	private static final String TAG = CaptureActivity.class.getSimpleName();

	private static final long DEFAULT_INTENT_RESULT_DURATION_MS = 1500L;
	private static final long BULK_MODE_SCAN_DELAY_MS = 1000L;

	private static final String[] ZXING_URLS = { "http://zxing.appspot.com/scan", "zxing://scan/" };

	public static final int HISTORY_REQUEST_CODE = 0x0000bacc;

	private static final Collection<ResultMetadataType> DISPLAYABLE_METADATA_TYPES = EnumSet.of(ResultMetadataType.ISSUE_NUMBER, ResultMetadataType.SUGGESTED_PRICE,
			ResultMetadataType.ERROR_CORRECTION_LEVEL, ResultMetadataType.POSSIBLE_COUNTRY);

	private CameraManager cameraManager;
	private CaptureActivityHandler handler;
	private Result savedResultToShow;
	private ViewfinderView viewfinderView;
	private Result lastResult;
	private boolean hasSurface;
	private boolean copyToClipboard;
	private Collection<BarcodeFormat> decodeFormats;
	private Map<DecodeHintType, ?> decodeHints;
	private String characterSet;
	private InactivityTimer inactivityTimer;
	private BeepManager beepManager;
	private AmbientLightManager ambientLightManager;

	public ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	public CameraManager getCameraManager() {
		return cameraManager;
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.capture);

		hasSurface = false;
		inactivityTimer = new InactivityTimer(this);
		beepManager = new BeepManager(this);
		ambientLightManager = new AmbientLightManager(this);

		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
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
		lastResult = null;

		if (ZXingConfig.DISABLE_AUTO_ORIENTATION) {
			setRequestedOrientation(getCurrentOrientation());
		} else {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
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

		Intent intent = getIntent();

		copyToClipboard = ZXingConfig.COPY_TO_CLIPBOARD && (intent == null || intent.getBooleanExtra(Intents.Scan.SAVE_HISTORY, true));

		decodeFormats = null;
		characterSet = null;

		if (intent != null) {

			String action = intent.getAction();
			String dataString = intent.getDataString();

			if (Intents.Scan.ACTION.equals(action)) {

				// Scan the formats the intent requested, and return the result
				// to the calling activity.
				decodeFormats = DecodeFormatManager.parseDecodeFormats(intent);
				// decodeHints = DecodeHintManager.parseDecodeHints(intent);

				if (intent.hasExtra(Intents.Scan.WIDTH) && intent.hasExtra(Intents.Scan.HEIGHT)) {
					int width = intent.getIntExtra(Intents.Scan.WIDTH, 0);
					int height = intent.getIntExtra(Intents.Scan.HEIGHT, 0);
					if (width > 0 && height > 0) {
						cameraManager.setManualFramingRect(width, height);
					}
				}

				if (intent.hasExtra(Intents.Scan.CAMERA_ID)) {
					int cameraId = intent.getIntExtra(Intents.Scan.CAMERA_ID, -1);
					if (cameraId >= 0) {
						cameraManager.setManualCameraId(cameraId);
					}
				}

				String customPromptMessage = intent.getStringExtra(Intents.Scan.PROMPT_MESSAGE);
				if (customPromptMessage != null) {
				}

			} else if (dataString != null && dataString.contains("http://www.google") && dataString.contains("/m/products/scan")) {

				// Scan only products and send the result to mobile Product
				// Search.
				decodeFormats = DecodeFormatManager.PRODUCT_FORMATS;

			} else if (isZXingURL(dataString)) {

				// Scan formats requested in query string (all formats if none
				// specified).
				// If a return URL is specified, send the results there.
				// Otherwise, handle it ourselves.
				Uri inputUri = Uri.parse(dataString);
				decodeFormats = DecodeFormatManager.parseDecodeFormats(inputUri);
				// Allow a sub-set of the hints to be specified by the caller.
				// decodeHints = DecodeHintManager.parseDecodeHints(inputUri);

			}

			characterSet = intent.getStringExtra(Intents.Scan.CHARACTER_SET);

		}
	}

	private int getCurrentOrientation() {
		int rotation = getWindowManager().getDefaultDisplay().getRotation();
		switch (rotation) {
		case Surface.ROTATION_0:
		case Surface.ROTATION_90:
			return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
		default:
			return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
		}
	}

	private static boolean isZXingURL(String dataString) {
		if (dataString == null) {
			return false;
		}
		for (String url : ZXING_URLS) {
			if (dataString.startsWith(url)) {
				return true;
			}
		}
		return false;
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

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			break;
		case KeyEvent.KEYCODE_FOCUS:
		case KeyEvent.KEYCODE_CAMERA:
			// Handle these events so they don't launch the Camera app
			return true;
			// Use volume up/down to turn on light
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			cameraManager.setTorch(false);
			return true;
		case KeyEvent.KEYCODE_VOLUME_UP:
			cameraManager.setTorch(true);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (resultCode == RESULT_OK) {
			if (requestCode == HISTORY_REQUEST_CODE) {
				// int itemNumber =
				// intent.getIntExtra(Intents.History.ITEM_NUMBER, -1);
				// if (itemNumber >= 0) {
				// decodeOrStoreSavedBitmap(null, historyItem.getResult());
				// }
			}
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
		// inactivityTimer.onActivity();
		// lastResult = rawResult;
		// ResultHandler resultHandler =
		// ResultHandlerFactory.makeResultHandler(this, rawResult);
		//
		// boolean fromLiveScan = barcode != null;
		// if (fromLiveScan) {
		// // Then not from history, so beep/vibrate and we have an image to
		// draw on
		// beepManager.playBeepSoundAndVibrate();
		// drawResultPoints(barcode, scaleFactor, rawResult);
		// }
		//
		// handleDecodeExternally(rawResult, resultHandler, barcode);
		// handleDecodeInternally(rawResult, resultHandler, barcode);
		// handleDecodeExternally(rawResult, resultHandler, barcode);
		// // Wait a moment or else it will scan the same barcode continuously
		// about 3 times
		// restartPreviewAfterDelay(BULK_MODE_SCAN_DELAY_MS);
		// handleDecodeInternally(rawResult, resultHandler, barcode);
	}

	/**
	 * Superimpose a line for 1D or dots for 2D to highlight the key features of
	 * the barcode.
	 * 
	 * @param barcode
	 *            A bitmap of the captured image.
	 * @param scaleFactor
	 *            amount by which thumbnail was scaled
	 * @param rawResult
	 *            The decoded results which contains the points to draw.
	 */
	private void drawResultPoints(Bitmap barcode, float scaleFactor, Result rawResult) {
		ResultPoint[] points = rawResult.getResultPoints();
		if (points != null && points.length > 0) {
			Canvas canvas = new Canvas(barcode);
			Paint paint = new Paint();
			paint.setColor(getResources().getColor(R.color.result_points));
			if (points.length == 2) {
				paint.setStrokeWidth(4.0f);
				drawLine(canvas, paint, points[0], points[1], scaleFactor);
			} else if (points.length == 4 && (rawResult.getBarcodeFormat() == BarcodeFormat.UPC_A || rawResult.getBarcodeFormat() == BarcodeFormat.EAN_13)) {
				// Hacky special case -- draw two lines, for the barcode and
				// metadata
				drawLine(canvas, paint, points[0], points[1], scaleFactor);
				drawLine(canvas, paint, points[2], points[3], scaleFactor);
			} else {
				paint.setStrokeWidth(10.0f);
				for (ResultPoint point : points) {
					if (point != null) {
						canvas.drawPoint(scaleFactor * point.getX(), scaleFactor * point.getY(), paint);
					}
				}
			}
		}
	}

	private static void drawLine(Canvas canvas, Paint paint, ResultPoint a, ResultPoint b, float scaleFactor) {
		if (a != null && b != null) {
			canvas.drawLine(scaleFactor * a.getX(), scaleFactor * a.getY(), scaleFactor * b.getX(), scaleFactor * b.getY(), paint);
		}
	}

	// Put up our own UI for how to handle the decoded contents.
	// private void handleDecodeInternally(Result rawResult, ResultHandler
	// resultHandler, Bitmap barcode) {
	//
	// CharSequence displayContents = resultHandler.getDisplayContents();
	//
	// if (copyToClipboard && !resultHandler.areContentsSecure()) {
	// ClipboardInterface.setText(displayContents, this);
	// }
	//
	// SharedPreferences prefs =
	// PreferenceManager.getDefaultSharedPreferences(this);
	//
	// if (resultHandler.getDefaultButtonID() != null &&
	// prefs.getBoolean(CameraConfigurationUtils.KEY_AUTO_OPEN_WEB, false)) {
	// resultHandler.handleButtonPress(resultHandler.getDefaultButtonID());
	// return;
	// }
	//
	// viewfinderView.setVisibility(View.GONE);
	//
	// if (barcode == null) {
	// } else {
	// }
	//
	//
	//
	// DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT,
	// DateFormat.SHORT);
	//
	//
	// Map<ResultMetadataType,Object> metadata = rawResult.getResultMetadata();
	// if (metadata != null) {
	// StringBuilder metadataText = new StringBuilder(20);
	// for (Map.Entry<ResultMetadataType,Object> entry : metadata.entrySet()) {
	// if (DISPLAYABLE_METADATA_TYPES.contains(entry.getKey())) {
	// metadataText.append(entry.getValue()).append('\n');
	// }
	// }
	// if (metadataText.length() > 0) {
	// metadataText.setLength(metadataText.length() - 1);
	// }
	// }
	//
	// int scaledSize = Math.max(22, 32 - displayContents.length() / 4);
	//
	// if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
	// CameraConfigurationUtils.KEY_SUPPLEMENTAL, true)) {
	// }
	//
	// int buttonCount = resultHandler.getButtonCount();
	//
	//
	// }

	// // Briefly show the contents of the barcode, then handle the result
	// outside Barcode Scanner.
	// private void handleDecodeExternally(Result rawResult, ResultHandler
	// resultHandler, Bitmap barcode) {
	//
	// if (barcode != null) {
	// viewfinderView.drawResultBitmap(barcode);
	// }
	//
	//
	// }

	private void sendReplyMessage(int id, Object arg, long delayMS) {
		if (handler != null) {
			Message message = Message.obtain(handler, id, arg);
			if (delayMS > 0L) {
				handler.sendMessageDelayed(message, delayMS);
			} else {
				handler.sendMessage(message);
			}
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
		// AlertDialog.Builder builder = new AlertDialog.Builder(this);
		// builder.setTitle(getString(R.string.app_name));
		// builder.setMessage(getString(R.string.msg_camera_framework_bug));
		// builder.setPositiveButton(R.string.button_ok, new
		// FinishListener(this));
		// builder.setOnCancelListener(new FinishListener(this));
		// builder.show();
	}

	public void restartPreviewAfterDelay(long delayMS) {
		if (handler != null) {
			handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
		}
		resetStatusView();
	}

	private void resetStatusView() {
		viewfinderView.setVisibility(View.VISIBLE);
		lastResult = null;
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();
	}
}
