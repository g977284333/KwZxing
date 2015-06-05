package com.kw_support.zxing.camera;

import java.lang.reflect.Method;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.kw_support.zxing.constant.ZXingConfig;

@SuppressWarnings("deprecation")
final class CameraConfigurationManager {
	private static final String TAG = "CameraConfiguration";

	private final Context mContext;
	private Point mScreenResolution;
	private Point mCameraResolution;

	public CameraConfigurationManager(Context context) {
		this.mContext = context;
	}

	/**
	 * Reads, one time, values from the camera that are needed by the app.
	 */
	public void initFromCameraParameters(Camera camera) {
		Camera.Parameters parameters = camera.getParameters();
		WindowManager manager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
		Display display = manager.getDefaultDisplay();
		Point theScreenResolution = new Point(display.getWidth(), display.getHeight());
		mScreenResolution = theScreenResolution;
		Log.i(TAG, "Screen resolution: " + mScreenResolution);
		Point screenResolutionForCamra = new Point();
		screenResolutionForCamra.x = mScreenResolution.x;
		screenResolutionForCamra.y = mScreenResolution.y;

		if (mScreenResolution.x < mScreenResolution.y) {
			screenResolutionForCamra.x = mScreenResolution.y;
			screenResolutionForCamra.y = mScreenResolution.x;
		}

		mCameraResolution = CameraConfigurationUtils.findBestPreviewSizeValue(parameters, screenResolutionForCamra);
		Log.i(TAG, "Camera resolution: " + mCameraResolution);
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
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
					CameraConfigurationUtils.setVideoStabilization(parameters);
					CameraConfigurationUtils.setFocusArea(parameters);
					CameraConfigurationUtils.setMetering(parameters);
				}
			}

		}

		parameters.setPreviewSize(mCameraResolution.x, mCameraResolution.y);

		Log.i(TAG, "Final camera parameters: " + parameters.flatten());

		setDisplayOrientation(camera, 90);
		camera.setParameters(parameters);

		Camera.Parameters afterParameters = camera.getParameters();
		Camera.Size afterSize = afterParameters.getPreviewSize();
		if (afterSize != null && (mCameraResolution.x != afterSize.width || mCameraResolution.y != afterSize.height)) {
			Log.w(TAG, "Camera said it supported preview size " + mCameraResolution.x + 'x' + mCameraResolution.y + ", but after setting it, preview size is " + afterSize.width + 'x'
					+ afterSize.height);
			mCameraResolution.x = afterSize.width;
			mCameraResolution.y = afterSize.height;
		}
	}

	Point getCameraResolution() {
		return mCameraResolution;
	}

	Point getScreenResolution() {
		return mScreenResolution;
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

	protected void setDisplayOrientation(Camera camera, int angle) {
		Method downPolymorphic;
		try {
			downPolymorphic = camera.getClass().getMethod("setDisplayOrientation", new Class[] { int.class });
			if (downPolymorphic != null)
				downPolymorphic.invoke(camera, new Object[] { angle });
		} catch (Exception e1) {
		}
	}

}
