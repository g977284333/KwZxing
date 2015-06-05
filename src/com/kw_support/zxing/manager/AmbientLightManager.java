package com.kw_support.zxing.manager;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.kw_support.zxing.camera.CameraManager;
import com.kw_support.zxing.constant.ZXingConfig;

/**
 * Detects ambient light and switches on the front light when very dark, and off
 * again when sufficiently light.
 * 
 * @author Sean Owen
 * @author Nikolaus Huber
 */
public final class AmbientLightManager implements SensorEventListener {

	private static final float TOO_DARK_LUX = 45.0f;
	private static final float BRIGHT_ENOUGH_LUX = 450.0f;

	private final Context mContext;
	
	private CameraManager mCameraManager;
	
	private Sensor mLightSensor;

	public AmbientLightManager(Context context) {
		this.mContext = context;
	}

	public void start(CameraManager cameraManager) {
		this.mCameraManager = cameraManager;
		
		if (ZXingConfig.FLIGHT_MODE.equals(ZXingConfig.FlightMode.AUTO)) {
			SensorManager sensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
			mLightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
			if (mLightSensor != null) {
				sensorManager.registerListener(this, mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
			}
		}
	}

	public void stop() {
		if (mLightSensor != null) {
			SensorManager sensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
			sensorManager.unregisterListener(this);
			mCameraManager = null;
			mLightSensor = null;
		}
	}

	@Override
	public void onSensorChanged(SensorEvent sensorEvent) {
		float ambientLightLux = sensorEvent.values[0];
		if (mCameraManager != null) {
			if (ambientLightLux <= TOO_DARK_LUX) {
				mCameraManager.setTorch(true);
			} else if (ambientLightLux >= BRIGHT_ENOUGH_LUX) {
				mCameraManager.setTorch(false);
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// do nothing
	}

}
