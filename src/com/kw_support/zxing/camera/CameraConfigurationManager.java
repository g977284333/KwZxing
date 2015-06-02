package com.kw_support.zxing.camera;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.kw_support.zxing.constant.ZXingConfig;

/**
 * A class which deals with reading, parsing, and setting the camera parameters
 * which are used to configure the camera hardware.
 */
@SuppressWarnings("deprecation")
final class CameraConfigurationManager {

	private static final String TAG = "CameraConfiguration";

	private final Context context;
	private Point screenResolution;
	private Point cameraResolution;

	public CameraConfigurationManager(Context context) {
		this.context = context;
	}

	/**
	 * Reads, one time, values from the camera that are needed by the app.
	 */
	public void initFromCameraParameters(Camera camera) {
		Camera.Parameters parameters = camera.getParameters();
		WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		Display display = manager.getDefaultDisplay();
		Point theScreenResolution = new Point(display.getWidth(), display.getHeight());
		screenResolution = theScreenResolution;
		Log.i(TAG, "Screen resolution: " + screenResolution);
		cameraResolution = CameraConfigurationUtils.findBestPreviewSizeValue(parameters, screenResolution);
		Log.i(TAG, "Camera resolution: " + cameraResolution);
	}

	public void setDesiredCameraParameters(Camera camera, boolean safeMode) {
		Camera.Parameters parameters = camera.getParameters();

		if (parameters == null) {
			Log.w(TAG, "Device error: no camera parameters are available. Proceeding without configuration.");
			return;
		}

		Log.i(TAG, "Initial camera parameters: " + parameters.flatten());

		if (safeMode) {
			Log.w(TAG, "In camera config safe mode -- most settings will not be honored");
		}

		// SharedPreferences prefs =
		// PreferenceManager.getDefaultSharedPreferences(context);

		// false 不开启闪光灯
		doSetTorch(parameters, ZXingConfig.FLASH_LIGHT, safeMode);

		CameraConfigurationUtils.setFocus(parameters, ZXingConfig.AUTO_FOCUS, ZXingConfig.DISABLE_CONTINUOUS_FOCUS, safeMode);

		if (!safeMode) {
			if (ZXingConfig.INVERT_SCAN) {
				CameraConfigurationUtils.setInvertColor(parameters);
			}

			if (!ZXingConfig.DISABLE_BARCODE_SCENE_MODE) {
				CameraConfigurationUtils.setBarcodeSceneMode(parameters);
			}

			if (!ZXingConfig.DISABLE_METERING) {
				if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
					CameraConfigurationUtils.setVideoStabilization(parameters);
					CameraConfigurationUtils.setFocusArea(parameters);
					CameraConfigurationUtils.setMetering(parameters);
				}
			}

		}

		parameters.setPreviewSize(cameraResolution.x, cameraResolution.y);

		Log.i(TAG, "Final camera parameters: " + parameters.flatten());

		camera.setParameters(parameters);

		Camera.Parameters afterParameters = camera.getParameters();
		Camera.Size afterSize = afterParameters.getPreviewSize();
		if (afterSize != null && (cameraResolution.x != afterSize.width || cameraResolution.y != afterSize.height)) {
			Log.w(TAG, "Camera said it supported preview size " + cameraResolution.x + 'x' + cameraResolution.y + ", but after setting it, preview size is " + afterSize.width + 'x' + afterSize.height);
			cameraResolution.x = afterSize.width;
			cameraResolution.y = afterSize.height;
		}
	}

	Point getCameraResolution() {
		return cameraResolution;
	}

	Point getScreenResolution() {
		return screenResolution;
	}

	boolean getTorchState(Camera camera) {
		if (camera != null) {
			Camera.Parameters parameters = camera.getParameters();
			if (parameters != null) {
				String flashMode = parameters.getFlashMode();
				return flashMode != null && (Camera.Parameters.FLASH_MODE_ON.equals(flashMode) || Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode));
			}
		}
		return false;
	}

	void setTorch(Camera camera, boolean newSetting) {
		Camera.Parameters parameters = camera.getParameters();
		doSetTorch(parameters, newSetting, false);
		camera.setParameters(parameters);
	}

	@SuppressWarnings("unused")
	private void doSetTorch(Camera.Parameters parameters, boolean newSetting, boolean safeMode) {
		CameraConfigurationUtils.setTorch(parameters, newSetting);
		 if (!safeMode && !ZXingConfig.DISABLE_EXPOSURE) {
			 CameraConfigurationUtils.setBestExposure(parameters, newSetting);
		 }
	}

}
