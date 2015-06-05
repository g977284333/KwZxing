package com.kw_support.zxing.camera;

import android.graphics.Point;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

final class PreviewCallback implements Camera.PreviewCallback {
	private static final String TAG = PreviewCallback.class.getSimpleName();

	private final CameraConfigurationManager mConfigManager;
	
	private Handler mPreviewHandler;
	
	private int mPreviewMessage;

	PreviewCallback(CameraConfigurationManager configManager) {
		this.mConfigManager = configManager;
	}

	public void setHandler(Handler previewHandler, int previewMessage) {
		this.mPreviewHandler = previewHandler;
		this.mPreviewMessage = previewMessage;
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		Point cameraResolution = mConfigManager.getCameraResolution();
		Handler thePreviewHandler = mPreviewHandler;
		if (cameraResolution != null && thePreviewHandler != null) {
			Message message = thePreviewHandler.obtainMessage(mPreviewMessage, cameraResolution.x, cameraResolution.y, data);
			message.sendToTarget();
			mPreviewHandler = null;
		} else {
			Log.d(TAG, "Got preview callback, but no handler or resolution available");
		}
	}

}
