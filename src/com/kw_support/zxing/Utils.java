package com.kw_support.zxing;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/**
 * @author: gchen
 * @description:
 * @version 1.0
 * @date：2015-6-5 下午11:55:16
 * 
 */
public class Utils {

	public static int px2sp(Context context, float pxValue) {
		final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
		return (int) (pxValue / fontScale + 0.5f);
	}

	public static int sp2px(Context context, float spValue) {
		final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
		return (int) (spValue * fontScale + 0.5f);
	}

	public static int dip2px(Context context, float dipValue) {
		final float scale = context.getResources().getDisplayMetrics().density;
		return (int) (dipValue * scale + 0.5f);
	}

	public static int px2dip(Context context, float pxValue) {
		final float scale = context.getResources().getDisplayMetrics().density;
		return (int) (pxValue / scale + 0.5f);
	}

	public static void openLocalImageGallery(Activity act) {
		Intent getAlbum = new Intent(Intent.ACTION_GET_CONTENT);

		getAlbum.setType("image/*");

		act.startActivityForResult(getAlbum, 1);
	}
}
