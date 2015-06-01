package com.kw_support.zxing.constant;

public class ZXingConfig {
	
	public static final boolean DISABLE_AUTO_ORIENTATION = false;
	public static final boolean FLASH_LIGHT = false;
	public static final boolean AUTO_FOCUS = true;
	public static final boolean DISABLE_CONTINUOUS_FOCUS = true;
	public static final boolean INVERT_SCAN = false;
	public static final boolean DISABLE_BARCODE_SCENE_MODE = true;
	public static final boolean DISABLE_METERING = true;
	public static final boolean VIBRATE = false;
	public static final boolean COPY_TO_CLIPBOARD = true;
	
	public static final boolean DECODE_1D_PRODUCT = true;
	public static final boolean DECODE_1D_INDUSTRIAL = true;
	public static final boolean DECODE_QR = true;
	public static final boolean DECODE_DATA_MATRIX = true;
	public static final boolean DECODE_AZTEC = false;
	public static final boolean DECODE_PDF417 = false;
	
	public static final String FLIGHT_MODE = FlightMode.OFF;
	
	
	
	
	
	public interface FlightMode {
		String ON = "on";
		String OFF = "off";
		String AUTO = "auto";
	}
}
