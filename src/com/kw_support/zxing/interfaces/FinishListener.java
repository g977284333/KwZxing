package com.kw_support.zxing.interfaces;

import android.app.Activity;
import android.content.DialogInterface;

/**
 * Simple listener used to exit the app in a few cases.
 * 
 * @author Sean Owen
 */
public final class FinishListener implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {

	private final Activity mActivityToFinish;

	public FinishListener(Activity activityToFinish) {
		this.mActivityToFinish = activityToFinish;
	}

	@Override
	public void onCancel(DialogInterface dialogInterface) {
		run();
	}

	@Override
	public void onClick(DialogInterface dialogInterface, int i) {
		run();
	}

	private void run() {
		mActivityToFinish.finish();
	}

}
