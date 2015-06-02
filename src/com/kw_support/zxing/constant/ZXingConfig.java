package com.kw_support.zxing.constant;

public class ZXingConfig {
	
	public static final boolean DISABLE_AUTO_ORIENTATION = false;	// û���Զ���ת
	public static final boolean FLASH_LIGHT = false;				// �����
	public static final boolean AUTO_FOCUS = true;					// �Զ��Խ�
	public static final boolean DISABLE_CONTINUOUS_FOCUS = true;	//��û�г�����ע
	public static final boolean INVERT_SCAN = false;				// ����ɨ��
	public static final boolean DISABLE_BARCODE_SCENE_MODE = true;	// û��������ĳ���ģʽ
	public static final boolean DISABLE_METERING = true;			// �޼���
	public static final boolean DISABLE_EXPOSURE = true;			// �޼���
	public static final boolean VIBRATE = false;					// ��
	
	public static final boolean DECODE_1D_PRODUCT = true;			// 1D��Ʒ
	public static final boolean DECODE_1D_INDUSTRIAL = true;		// 1D��ҵ
	public static final boolean DECODE_QR = true;					// QR��
	public static final boolean DECODE_DATA_MATRIX = true;			// DM��
	public static final boolean DECODE_AZTEC = false;				// ����̨����
	public static final boolean DECODE_PDF417 = false;				// PDF417
	
	public static final String FLIGHT_MODE = FlightMode.OFF;		// �����ģʽ
	
	public interface FlightMode {
		String ON = "on";
		String OFF = "off";
		String AUTO = "auto";
	}
}
