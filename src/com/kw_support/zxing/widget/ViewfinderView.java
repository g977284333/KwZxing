package com.kw_support.zxing.widget;

import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;

import com.google.zxing.ResultPoint;
import com.kw_support.R;
import com.kw_support.zxing.camera.CameraManager;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder
 * rectangle and partial transparency outside it, as well as the laser scanner
 * animation and result points.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

	private static final int[] SCANNER_ALPHA = { 0, 64, 128, 192, 255, 192, 128, 64 };
	private static final long ANIMATION_DELAY = 80L;
	private static final int CURRENT_POINT_OPACITY = 0xA0;
	private static final int MAX_RESULT_POINTS = 20;
	private static final int POINT_SIZE = 6;

	private CameraManager cameraManager;
	private final Paint paint;
	private Bitmap resultBitmap;
	private final int maskColor;
	private final int resultColor;
	private final int cornerColor;
	private int scannerAlpha;
	private List<ResultPoint> possibleResultPoints;

	private boolean laserLinePortrait = true;
	Rect mRect;
	int i = 0;
	GradientDrawable mDrawable;
	Paint textPaint;

	// This constructor is used when the class is built from an XML resource.
	public ViewfinderView(Context context, AttributeSet attrs) {
		super(context, attrs);

		// Initialize these once for performance rather than calling them every
		// time in onDraw().
		paint = new Paint(Paint.ANTI_ALIAS_FLAG);

		textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		Resources resources = getResources();

		mRect = new Rect();
		int left = context.getResources().getColor(R.color.viewfinder_main_line_edge);
		int center = context.getResources().getColor(R.color.viewfinder_main_line);
		int right = context.getResources().getColor(R.color.viewfinder_main_line_edge);
		mDrawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] { left, center, right });

		maskColor = resources.getColor(R.color.viewfinder_mask);
		resultColor = resources.getColor(R.color.result_view);
		cornerColor = resources.getColor(R.color.viewfinder_corner);
		scannerAlpha = 0;
		possibleResultPoints = new ArrayList<ResultPoint>(5);
	}

	public void setCameraManager(CameraManager cameraManager) {
		this.cameraManager = cameraManager;
	}

	@SuppressLint("DrawAllocation")
	@Override
	public void onDraw(Canvas canvas) {
		if (cameraManager == null) {
			return; // not ready yet, early draw before done configuring
		}
		Rect frame = cameraManager.getFramingRect();
		Rect previewFrame = cameraManager.getFramingRectInPreview();
		if (frame == null || previewFrame == null) {
			return;
		}
		int width = canvas.getWidth();
		int height = canvas.getHeight();

		// Draw the exterior (i.e. outside the framing rect) darkened
		paint.setColor(resultBitmap != null ? resultColor : maskColor);
		canvas.drawRect(0, 0, width, frame.top, paint);
		canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
		canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
		canvas.drawRect(0, frame.bottom + 1, width, height, paint);

		if (resultBitmap != null) {
			// Draw the opaque result bitmap over the scanning rectangle
			paint.setAlpha(CURRENT_POINT_OPACITY);
			canvas.drawBitmap(resultBitmap, null, frame, paint);
		} else {

			paint.setColor(cornerColor);
			// 左上角
			canvas.drawRect(frame.left - 10, frame.top - 10, frame.left + 20, frame.top, paint);
			canvas.drawRect(frame.left - 10, frame.top - 10, frame.left, frame.top + 20, paint);

			// 右上角
			canvas.drawRect(frame.right - 20, frame.top - 10, frame.right + 10, frame.top, paint);
			canvas.drawRect(frame.right, frame.top - 10, frame.right + 10, frame.top + 20, paint);

			// 左下角
			canvas.drawRect(frame.left - 10, frame.bottom - 20, frame.left, frame.bottom + 10, paint);
			canvas.drawRect(frame.left - 10, frame.bottom, frame.left + 20, frame.bottom + 10, paint);

			// 右下角
			canvas.drawRect(frame.right, frame.bottom - 20, frame.right + 10, frame.bottom + 10, paint);
			canvas.drawRect(frame.right - 20, frame.bottom, frame.right + 10, frame.bottom + 10, paint);

			int middle = frame.width() / 2;
			textPaint.setTextSize(20);
			textPaint.setColor(Color.WHITE);
			String text = getResources().getString(R.string.dimension_content);
			canvas.drawText(text, middle - 112, frame.bottom + 60, textPaint);

			// Draw a red "laser scanner" line through the middle to show
			// decoding is active
			paint.setColor(Color.GREEN);
			paint.setAlpha(SCANNER_ALPHA[scannerAlpha]);
			scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.length;

			// 上下走的线
			if (laserLinePortrait) {

				if ((i += 5) < frame.bottom - frame.top) {
					/*
					 * canvas.drawRect(frame.left + 2, frame.top - 2 + i,
					 * frame.right - 1, frame.top + 2 + i, paint);
					 */
					int r = 8;
					mDrawable.setShape(GradientDrawable.RECTANGLE);
					mDrawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
					setCornerRadii(mDrawable, r, r, r, r);
					mRect.set(frame.left + 2, frame.top - 3 + i, frame.right - 1, frame.top + 3 + i);
					mDrawable.setBounds(mRect);
					mDrawable.draw(canvas);
					invalidate();
				} else {
					i = 0;
				}

			} else {
				float left = frame.left + (frame.right - frame.left) / 2 - 2;
				canvas.drawRect(left, frame.top, left + 2, frame.bottom - 2, paint);
			}

			// Request another update at the animation interval, but only
			// repaint the laser line,
			// not the entire viewfinder mask.
			postInvalidateDelayed(ANIMATION_DELAY, frame.left - POINT_SIZE, frame.top - POINT_SIZE, frame.right + POINT_SIZE, frame.bottom + POINT_SIZE);
		}
	}

	public void drawViewfinder() {
		Bitmap resultBitmap = this.resultBitmap;
		this.resultBitmap = null;
		if (resultBitmap != null) {
			resultBitmap.recycle();
		}
		invalidate();
	}

	/**
	 * Draw a bitmap with the result points highlighted instead of the live
	 * scanning display.
	 * 
	 * @param barcode
	 *            An image of the decoded barcode.
	 */
	public void drawResultBitmap(Bitmap barcode) {
		resultBitmap = barcode;
		invalidate();
	}

	public void addPossibleResultPoint(ResultPoint point) {
		List<ResultPoint> points = possibleResultPoints;
		synchronized (points) {
			points.add(point);
			int size = points.size();
			if (size > MAX_RESULT_POINTS) {
				// trim it
				points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
			}
		}
	}

	public void setCornerRadii(GradientDrawable drawable, float r0, float r1, float r2, float r3) {
		drawable.setCornerRadii(new float[] { r0, r0, r1, r1, r2, r2, r3, r3 });
	}

}
