package com.kw_support.zxing.interfaces;

import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;
import com.kw_support.zxing.widget.ViewfinderView;

public final class ViewfinderResultPointCallback implements ResultPointCallback {

	private final ViewfinderView viewfinderView;

	public ViewfinderResultPointCallback(ViewfinderView viewfinderView) {
		this.viewfinderView = viewfinderView;
	}

	@Override
	public void foundPossibleResultPoint(ResultPoint point) {
		viewfinderView.addPossibleResultPoint(point);
	}

}
