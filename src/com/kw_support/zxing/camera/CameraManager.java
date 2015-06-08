package com.kw_support.zxing.camera;

import java.io.IOException;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.zxing.PlanarYUVLuminanceSource;
import com.kw_support.zxing.camera.open.OpenCameraInterface;

public final class CameraManager {
	private static final String TAG = CameraManager.class.getSimpleName();

	private final Context mContext;

	private final CameraConfigurationManager mConfigManager;

	private Camera mCamera;

	private AutoFocusManager mAutoFocusManager;

	private Rect mFramingRect;
	private Rect mFramingRectInPreview;

	private boolean mInitialized;
	private boolean mPreviewing;

	private int mRequestedCameraId = OpenCameraInterface.NO_REQUESTED_CAMERA;
	private int mRequestedFramingRectWidth;
	private int mRequestedFramingRectHeight;

	/**
	 * Preview frames are delivered here, which we pass on to the registered
	 * handler. Make sure to clear the handler so it will only receive one
	 * message.
	 */
	private final PreviewCallback mPreviewCallback;

	public CameraManager(Context context) {
		this.mContext = context;
		this.mConfigManager = new CameraConfigurationManager(context);
		mPreviewCallback = new PreviewCallback(mConfigManager);
	}

	/**
	 * Opens the camera driver and initializes the hardware parameters.
	 * 
	 * @param holder
	 *            The surface object which the camera will draw preview frames
	 *            into.
	 * @throws IOException
	 *             Indicates the camera driver failed to open.
	 */
	public synchronized void openDriver(SurfaceHolder holder) throws IOException {
		Camera theCamera = mCamera;
		
		if (theCamera == null) {
			theCamera = OpenCameraInterface.open(mRequestedCameraId);
			if (theCamera == null) {
				throw new IOException();
			}
			mCamera = theCamera;
		}
		
		theCamera.setPreviewDisplay(holder);
		
		if (!mInitialized) {
			mInitialized = true;
			mConfigManager.initFromCameraParameters(theCamera);
			if (mRequestedFramingRectWidth > 0 && mRequestedFramingRectHeight > 0) {
				setManualFramingRect(mRequestedFramingRectWidth, mRequestedFramingRectHeight);
				mRequestedFramingRectWidth = 0;
				mRequestedFramingRectHeight = 0;
			}
		}

		Camera.Parameters parameters = theCamera.getParameters();
		String parametersFlattened = parameters == null ? null : parameters.flatten(); // Save
		
		try {
			mConfigManager.setDesiredCameraParameters(theCamera, false);
		} catch (RuntimeException re) {
			// Driver failed
			Log.w(TAG, "Camera rejected parameters. Setting only minimal safe-mode parameters");
			Log.i(TAG, "Resetting to saved camera params: " + parametersFlattened);
			
			// Reset:
			if (parametersFlattened != null) {
				parameters = theCamera.getParameters();
				parameters.unflatten(parametersFlattened);

				try {
					theCamera.setParameters(parameters);
					mConfigManager.setDesiredCameraParameters(theCamera, true);
				} catch (RuntimeException re2) {
					// Well, darn. Give up
					Log.w(TAG, "Camera rejected even safe-mode parameters! No configuration");
				}
			}
		}

	}

	public synchronized boolean isOpen() {
		return mCamera != null;
	}

	/**
	 * Closes the camera driver if still in use.
	 */
	public synchronized void closeDriver() {
		if (mCamera != null) {
			mCamera.release();
			mCamera = null;
			// Make sure to clear these each time we close the camera, so that
			// any scanning rect
			// requested by intent is forgotten.
			mFramingRect = null;
			mFramingRectInPreview = null;
		}
	}

	/**
	 * Asks the camera hardware to begin drawing preview frames to the screen.
	 */
	public synchronized void startPreview() {
		Camera theCamera = mCamera;
		if (theCamera != null && !mPreviewing) {
			theCamera.startPreview();
			mPreviewing = true;
			mAutoFocusManager = new AutoFocusManager(mContext, mCamera);
		}
	}

	/**
	 * Tells the camera to stop drawing preview frames.
	 */
	public synchronized void stopPreview() {
		if (mAutoFocusManager != null) {
			mAutoFocusManager.stop();
			mAutoFocusManager = null;
		}
		if (mCamera != null && mPreviewing) {
			mCamera.stopPreview();
			mPreviewCallback.setHandler(null, 0);
			mPreviewing = false;
		}
	}

	/**
	 * Convenience method for
	 * {@link com.google.zxing.client.android.CaptureActivity}
	 * 
	 * @param newSetting
	 *            if {@code true}, light should be turned on if currently off.
	 *            And vice versa.
	 */
	public synchronized void setTorch(boolean newSetting) {
		if (newSetting != mConfigManager.getTorchState(mCamera)) {
			if (mCamera != null) {
				if (mAutoFocusManager != null) {
					mAutoFocusManager.stop();
				}
				mConfigManager.setTorch(mCamera, newSetting);
				if (mAutoFocusManager != null) {
					mAutoFocusManager.start();
				}
			}
		}
	}

	/**
	 * A single preview frame will be returned to the handler supplied. The data
	 * will arrive as byte[] in the message.obj field, with width and height
	 * encoded as message.arg1 and message.arg2, respectively.
	 * 
	 * @param handler
	 *            The handler to send the message to.
	 * @param message
	 *            The what field of the message to be sent.
	 */
	public synchronized void requestPreviewFrame(Handler handler, int message) {
		Camera theCamera = mCamera;
		if (theCamera != null && mPreviewing) {
			mPreviewCallback.setHandler(handler, message);
			theCamera.setOneShotPreviewCallback(mPreviewCallback);
		}
	}

	/**
	 * Calculates the framing rect which the UI should draw to show the user
	 * where to place the barcode. This target helps with alignment as well as
	 * forces the user to hold the device far enough away to ensure the image
	 * will be in focus.
	 * 
	 * @return The rectangle to draw on screen in window coordinates.
	 */
	public synchronized Rect getFramingRect() {
		if (mFramingRect == null) {
			if (mCamera == null) {
				return null;
			}
			Point screenResolution = mConfigManager.getScreenResolution();
			if (screenResolution == null) {
				// Called early, before init even finished
				return null;
			}

			DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
			int width = (int) (metrics.widthPixels * 0.6);
			int height = (int) (width * 0.9);

			int leftOffset = (screenResolution.x - width) / 2;
			int topOffset = (screenResolution.y - height) / 4;
			mFramingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
			Log.d(TAG, "Calculated framing rect: " + mFramingRect);
		}
		return mFramingRect;
	}

	/**
	 * Like {@link #getFramingRect} but coordinates are in terms of the preview
	 * frame, not UI / screen.
	 * 
	 * @return {@link Rect} expressing barcode scan area in terms of the preview
	 *         size
	 */
	public synchronized Rect getFramingRectInPreview() {
		if (mFramingRectInPreview == null) {
			Rect framingRect = getFramingRect();
			if (framingRect == null) {
				return null;
			}
			Rect rect = new Rect(framingRect);
			Point cameraResolution = mConfigManager.getCameraResolution();
			Point screenResolution = mConfigManager.getScreenResolution();
			if (cameraResolution == null || screenResolution == null) {
				// Called early, before init even finished
				return null;
			}
			rect.left = rect.left * cameraResolution.y / screenResolution.x;
			rect.right = rect.right * cameraResolution.y / screenResolution.x;
			rect.top = rect.top * cameraResolution.x / screenResolution.y;
			rect.bottom = rect.bottom * cameraResolution.x / screenResolution.y;
			mFramingRectInPreview = rect;
		}
		return mFramingRectInPreview;
	}

	/**
	 * Allows third party apps to specify the camera ID, rather than determine
	 * it automatically based on available cameras and their orientation.
	 * 
	 * @param cameraId
	 *            camera ID of the camera to use. A negative value means
	 *            "no preference".
	 */
	public synchronized void setManualCameraId(int cameraId) {
		mRequestedCameraId = cameraId;
	}

	/**
	 * Allows third party apps to specify the scanning rectangle dimensions,
	 * rather than determine them automatically based on screen resolution.
	 * 
	 * @param width
	 *            The width in pixels to scan.
	 * @param height
	 *            The height in pixels to scan.
	 */
	public synchronized void setManualFramingRect(int width, int height) {
		if (mInitialized) {
			Point screenResolution = mConfigManager.getScreenResolution();
			if (width > screenResolution.x) {
				width = screenResolution.x;
			}
			if (height > screenResolution.y) {
				height = screenResolution.y;
			}
			int leftOffset = (screenResolution.x - width) / 2;
			int topOffset = (screenResolution.y - height) / 2;
			mFramingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
			Log.d(TAG, "Calculated manual framing rect: " + mFramingRect);
			mFramingRectInPreview = null;
		} else {
			mRequestedFramingRectWidth = width;
			mRequestedFramingRectHeight = height;
		}
	}

	/**
	 * A factory method to build the appropriate LuminanceSource object based on
	 * the format of the preview buffers, as described by Camera.Parameters.
	 * 
	 * @param data
	 *            A preview frame.
	 * @param width
	 *            The width of the image.
	 * @param height
	 *            The height of the image.
	 * @return A PlanarYUVLuminanceSource instance.
	 */
	public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
		Rect rect = getFramingRectInPreview();
		if (rect == null) {
			return null;
		}
		// Go ahead and assume it's YUV rather than die.
		return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top, rect.width(), rect.height(), false);
	}

}
