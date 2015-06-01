package com.kw_support.zxing.decode;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.ResultPointCallback;
import com.kw_support.zxing.activity.CaptureActivity;
import com.kw_support.zxing.constant.ZXingConfig;

/**
 * This thread does all the heavy lifting of decoding the images.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class DecodeThread extends Thread {

	public static final String BARCODE_BITMAP = "barcode_bitmap";
	public static final String BARCODE_SCALED_FACTOR = "barcode_scaled_factor";

	private final CaptureActivity activity;
	private final Map<DecodeHintType, Object> hints;
	private Handler handler;
	private final CountDownLatch handlerInitLatch;

	public DecodeThread(CaptureActivity activity, Collection<BarcodeFormat> decodeFormats, Map<DecodeHintType, ?> baseHints, String characterSet, ResultPointCallback resultPointCallback) {

		this.activity = activity;
		handlerInitLatch = new CountDownLatch(1);

		hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
		if (baseHints != null) {
			hints.putAll(baseHints);
		}

		// The prefs can't change while the thread is running, so pick them up
		// once here.
		if (decodeFormats == null || decodeFormats.isEmpty()) {
			decodeFormats = EnumSet.noneOf(BarcodeFormat.class);
			if (ZXingConfig.DECODE_1D_PRODUCT) {
				decodeFormats.addAll(DecodeFormatManager.PRODUCT_FORMATS);
			}
			if (ZXingConfig.DECODE_1D_INDUSTRIAL) {
				decodeFormats.addAll(DecodeFormatManager.INDUSTRIAL_FORMATS);
			}
			if (ZXingConfig.DECODE_QR) {
				decodeFormats.addAll(DecodeFormatManager.QR_CODE_FORMATS);
			}
			if (ZXingConfig.DECODE_DATA_MATRIX) {
				decodeFormats.addAll(DecodeFormatManager.DATA_MATRIX_FORMATS);
			}
			if (ZXingConfig.DECODE_AZTEC) {
				decodeFormats.addAll(DecodeFormatManager.AZTEC_FORMATS);
			}
			if (ZXingConfig.DECODE_PDF417) {
				decodeFormats.addAll(DecodeFormatManager.PDF417_FORMATS);
			}
		}
		hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);

		if (characterSet != null) {
			hints.put(DecodeHintType.CHARACTER_SET, characterSet);
		}
		hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, resultPointCallback);
		Log.i("DecodeThread", "Hints: " + hints);
	}

	public Handler getHandler() {
		try {
			handlerInitLatch.await();
		} catch (InterruptedException ie) {
			// continue?
		}
		return handler;
	}

	@Override
	public void run() {
		Looper.prepare();
		handler = new DecodeHandler(activity, hints);
		handlerInitLatch.countDown();
		Looper.loop();
	}

}
