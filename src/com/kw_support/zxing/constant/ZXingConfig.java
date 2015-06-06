package com.kw_support.zxing.constant;

public class ZXingConfig {
	
	public static final boolean DISABLE_AUTO_ORIENTATION = false;	// 没有自动旋转
	public static final boolean FLASH_LIGHT = false;				// 闪光灯
	public static final boolean AUTO_FOCUS = true;					// 自动对焦
	public static final boolean DISABLE_CONTINUOUS_FOCUS = true;	// 没有持续关注
	public static final boolean INVERT_SCAN = false;				// 反向扫描
	public static final boolean DISABLE_BARCODE_SCENE_MODE = true;	// 没有条形码的场景模式
	public static final boolean DISABLE_METERING = true;			// 无计量
	public static final boolean DISABLE_EXPOSURE = true;			// 无曝光
	public static final boolean VIBRATE = false;					// 震动
	
	public static final boolean DECODE_1D_PRODUCT = true;			// 1D产品
	public static final boolean DECODE_1D_INDUSTRIAL = true;		// 1D工业
	public static final boolean DECODE_QR = true;					// QR码
	public static final boolean DECODE_DATA_MATRIX = true;			// DM码
	public static final boolean DECODE_AZTEC = false;				// 阿兹台克人
	public static final boolean DECODE_PDF417 = false;				// PDF417
	
	public static final String FLIGHT_MODE = FlightMode.OFF;		// 闪光灯模式
	
	public interface FlightMode {
		String ON = "on";
		String OFF = "off";
		String AUTO = "auto";
	}
}
