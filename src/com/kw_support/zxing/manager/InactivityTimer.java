package com.kw_support.zxing.manager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.util.Log;

/**
 * Finishes an activity after a period of inactivity if the device is on battery
 * power.
 */
@SuppressLint("NewApi")
public final class InactivityTimer {
	private static final String TAG = InactivityTimer.class.getSimpleName();

	private static final long INACTIVITY_DELAY_MS = 5 * 60 * 1000L;

	private final Activity mActivity;
	private final BroadcastReceiver mPowerStatusReceiver;
	private boolean mRegistered;
	private AsyncTask<Object, Object, Object> mInactivityTask;

	public InactivityTimer(Activity activity) {
		this.mActivity = activity;
		mPowerStatusReceiver = new PowerStatusReceiver();
		mRegistered = false;
		onActivity();
	}

	public synchronized void onActivity() {
		cancel();
		mInactivityTask = new InactivityAsyncTask();
		mInactivityTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	public synchronized void onPause() {
		cancel();
		
		if (mRegistered) {
			mActivity.unregisterReceiver(mPowerStatusReceiver);
			mRegistered = false;
		} else {
			Log.w(TAG, "PowerStatusReceiver was never registered?");
		}
	}

	public synchronized void onResume() {
		if (mRegistered) {
			Log.w(TAG, "PowerStatusReceiver was already registered?");
		} else {
			mActivity.registerReceiver(mPowerStatusReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
			mRegistered = true;
		}
		onActivity();
	}

	private synchronized void cancel() {
		AsyncTask<?, ?, ?> task = mInactivityTask;
		
		if (task != null) {
			task.cancel(true);
			mInactivityTask = null;
		}
	}

	public void shutdown() {
		cancel();
	}

	private final class PowerStatusReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
				// 0 indicates that we're on battery
				boolean onBatteryNow = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) <= 0;
				if (onBatteryNow) {
					InactivityTimer.this.onActivity();
				} else {
					InactivityTimer.this.cancel();
				}
			}
		}
	}

	private final class InactivityAsyncTask extends AsyncTask<Object, Object, Object> {
		@Override
		protected Object doInBackground(Object... objects) {
			try {
				Thread.sleep(INACTIVITY_DELAY_MS);
				Log.i(TAG, "Finishing activity due to inactivity");
				mActivity.finish();
			} catch (InterruptedException e) {
				// continue without killing
			}
			return null;
		}
	}

}
