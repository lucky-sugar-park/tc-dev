package com.wow.be.tc.vplc.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

public class ByteUtil {

	public enum Endian {
		LITTLE, BIG
	}
	
	/**
	 * List<Map<String,String>> 데이터를 list 사이즈만큼의 byte를 만들고 byte[seq]=packet 의값으로 리턴. type=tag,param은 제외하고 값셋팅.
	 * 
	 * @param srcData
	 * @return
	 */
	public static byte[] listMapToByte(List<Map<String,String>> srcData){
		byte[] rtnData = null;
		int len = 0, idx=0;
		String str;
		int bint;
		
		if(srcData==null || srcData.size() < 1) return rtnData;
		
		len = srcData.size();
		rtnData = new byte[len];
				
		for(Map<String,String> map : srcData){
			if( map.get("type").equals("tag") || map.get("type").equals("param") ) {
				continue;
			}
			idx = Integer.parseInt(map.get("seq"));
			str = map.get("packet").replaceAll("0x", "").toLowerCase();
			bint = Integer.parseInt(str, 16);
			rtnData[idx] = (byte)bint;
		}
		return rtnData;
	}
	
	public static String byteToHexString(byte[] b){
		int i;
		StringBuffer sb = new StringBuffer();
		if( b == null || b.length == 0 ) return null;
		for(i=0; i < b.length; i++) {
			sb.append(toHexString(b[i]));
		}
		return sb.toString();
	}
	
	/**
	 * ByteUtils.toHexString((byte)1) = "01" 
	 * ByteUtils.toHexString((byte)255) = "ff"
	 * 
	 * @param b
	 * @return
	 */
	public static String toHexString(byte b) {
		StringBuffer result = new StringBuffer();
		result.append(Integer.toString((b&0xFF) + 0x100, 16).substring(1));
		return result.toString();
	}
	
	public static int bytesToInt(byte[] bytes, Endian endian) {
		switch(bytes.length) {
		case 1:
			return bytes[0] & 0xFF;
		case 2:
			if(endian == Endian.BIG) return bytes[0] << 8  | bytes[1] & 0xFF;
			else return bytes[1] << 8 | bytes[0] & 0xFF;
		case 3:
			if(endian == Endian.BIG) return bytes[0] << 16 | (bytes[1] & 0xFF) << 8  | (bytes[2] & 0xFF);
			else return bytes[2] << 16 | (bytes[1] & 0xFF) << 8 | bytes[0] & 0xFF;
		default:
			if(endian == Endian.BIG) return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
			else return bytes[3] << 24 | (bytes[2] & 0xFF) << 16 | (bytes[1] & 0xFF) << 8 | bytes[0] & 0xFF;
		}
	}

	public static byte[] intToBytes(int value, int length, Endian endian) {
		switch (length) {
		case 1:
			return new byte[] { (byte) value };
		case 2:
			if(endian == Endian.BIG) return new byte[] { (byte) (value >> 8), (byte) value };
			else return new byte[] { (byte) value , (byte) (value >> 8)};
		case 3:
			if(endian == Endian.BIG) return new byte[] { (byte) (value >> 16), (byte) (value >> 8), (byte) value };
			else return new byte[] { (byte) value, (byte) (value >> 8), (byte) (value >> 16) };
		default:
			if(endian == Endian.BIG) return new byte[] { (byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value };
			else return new byte[] {  (byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24) };
		}
	}
		
//	public static void putBytes(ByteBuffer bb, byte[] source, int size) {
//		for(int i = 0; i < size; i++) {
//			if(i >= source.length) bb.put((byte)0x00);
//			else bb.put(source[i]);
//		}
//	}
	
	public final static int BIG_EDIAN    = 1;
	public final static int Little_EDIAN = 2;
	
	public static byte[] stringToBytes(String value, Endian order) {
		byte[] temp = value.getBytes();
		temp = changeByteOrder(temp, order);
		return temp;
	}

	/**
	 * @param value 읽을 바이트 배열
	 * @return String
	 */
	public static String byteArrayToString(byte[] value, Endian order) {
		byte[] temp = value;
		temp = changeByteOrder(temp, order);
		return new String(temp);
	}

	/**
	 * @param oridata 읽을 바이트 배열
	 * @param start 읽을 바이트 배열 시작점
	 * @param len 읽을 바이트 배열 길이
	 * @return 스트링 문자열
	 */
	public static String bytesToString(byte[] oridata, int start, int len, Endian Order) {
		byte[] temp = new byte[len];
		System.arraycopy(oridata, start, temp, 0, len);
		temp = changeByteOrder(temp, Order);
		return new String(temp);
	}

	/**
	 * @param oridata 읽을 바이트 배열
	 * @param start 읽을 바이트 배열 시작점
	 * @param len 읽을 바이트 배열 길이
	 * @return 스트링 문자열
	 */
	public static String byteShortToString(byte[] oridata, int start, int len, Endian Order) {
		byte[] temp = new byte[len];
		short newValue=0;
		System.arraycopy(oridata, start, temp, 0, len);
		
		//if(len==1) return String.valueOf(newValue);
		
		temp = changeByteOrder(temp, Order);
		newValue |= (((short) temp[0]) << 8) & 0xFF00;
		if( len == 2 ) newValue |= (((short) temp[1])) & 0xFF;
		return String.valueOf(newValue);
	}
	
	/**
	 * @param oridata 읽을 바이트 배열
	 * @param start 읽을 바이트 배열 시작점
	 * @param len 읽을 바이트 배열 길이
	 * @return 스트링 문자열
	 */
	public static String byteShortToAsciiString(byte[] oridata, int start, int len, Endian order) {
		byte[] temp = new byte[len];
		String newValue= "";
		System.arraycopy(oridata, start, temp, 0, len);

		temp = changeByteOrder(temp, order);
		
		for(int val : temp) newValue += String.valueOf((char)val);
		
		return newValue;
	}

	public static byte[] shortToBytes(short value, Endian order) {
		byte[] temp;
		temp = new byte[] { (byte) ((value & 0xFF00) >> 8),
				(byte) (value & 0x00FF) };
		temp = changeByteOrder(temp, order);
		return temp;
	}
	
	public static void shortToBytes(byte[] oridata, int start, short value, Endian order){
		byte b1,b2;
		b1= (byte) (value & 0x00FF);
		b2 = (byte) ((value & 0xFF00) >> 8);
		oridata[start] = b1;
		oridata[start+1] = b2;
	}

	/**
	 * @param Value  short형으로 바뀔 바이트
	 * @param Order  BIG_EDIAN,Little_EDIAN 변수에 적용되어 있음
	 * @return
	 */
	public static short bytesToShort(byte[] value, Endian order) {
		short newValue = 0;
		byte[] temp = value;

		temp = changeByteOrder(temp, order);

		newValue |= (((short) temp[0]) << 8) & 0xFF00;
		newValue |= (((short) temp[1])) & 0xFF;
		return newValue;
	}

	/**
	 * @param oridata 읽을 바이트 배열
	 * @param start 읽을 바이트의 시작점
	 * @param len 읽을 바이트 배열 길이
	 * @param Order BIG_EDIAN,Little_EDIAN 변수에 적용되어 있음
	 * @return short
	 */
	public static short bytesToShort(byte[] oridata, int start, int len, Endian order) {
		short newValue = 0;
		byte[] temp = new byte[len];

		System.arraycopy(oridata, start, temp, 0, len);

		temp = changeByteOrder(temp, order);

		newValue |= (((short) temp[1])) & 0xFF;
		newValue |= (((short) temp[0]) << 8) & 0xFF00;

		return newValue;
	}

	/**
	 * @param Value 변환될 인트
	 * @param Order BIG_EDIAN,Little_EDIAN 변수에 적용되어 있음
	 * @return 변환된 바이트 배열
	 */
	public static byte[] intToBytes(int value, Endian order) {
		byte[] temp = new byte[4];
		ByteBuffer buff = ByteBuffer.allocate(Integer.SIZE / 8);
		buff.putInt(value);
		temp = changeByteOrder(buff.array(), order);
		return temp;
	}

	/**
	 * @param Value int로 변환할 바이트 배열
	 * @param Order BIG_EDIAN,Little_EDIAN 변수에 적용되어 있음
	 * @return int
	 */
	public static int bytesToInt2(byte[] value, Endian order) {
		ByteBuffer buff = ByteBuffer.allocate(4);
		buff = ByteBuffer.wrap(value);

		if (order == Endian.BIG) buff.order(ByteOrder.BIG_ENDIAN);
		else if (order == Endian.LITTLE) buff.order(ByteOrder.LITTLE_ENDIAN);
		return buff.getInt();
	}

	/**
	 * @param oridata 읽을 바이트 배열
	 * @param start 읽을 바이트의 시작점
	 * @param len 읽을 바이트 배열 길이
	 * @param Order BIG_EDIAN,Little_EDIAN 변수에 적용되어 있음
	 * @return int
	 */
	public static int bytesToInt(byte[] oridata, int start, int len, Endian order) {
		byte[] temp = new byte[len];
		System.arraycopy(oridata, start, temp, 0, len);
		ByteBuffer buff = ByteBuffer.allocate(4);
		buff = ByteBuffer.wrap(temp);

		if (order == Endian.BIG) buff.order(ByteOrder.BIG_ENDIAN);
		else if (order == Endian.LITTLE) buff.order(ByteOrder.LITTLE_ENDIAN);
		return buff.getInt();
	}

	/**
	 * @param value 바이트로 변경할 float형 Value
	 * @param Order BIG_EDIAN,Little_EDIAN 변수에 적용되어 있음
	 * @return 바이트 어레이
	 */
	public static byte[] floatTobytes(float value, Endian order) {
		byte[] temp = new byte[4];

		int intBits = Float.floatToIntBits(value);

		temp[0] = (byte) ((intBits & 0x000000ff) >> 0);
		temp[1] = (byte) ((intBits & 0x0000ff00) >> 8);
		temp[2] = (byte) ((intBits & 0x00ff0000) >> 16);
		temp[3] = (byte) ((intBits & 0xff000000) >> 24);

		temp = changeByteOrder(temp, order);
		return temp;
	}

	// ======byteArray to Float====//
	/**
	 * @param Value float으로 변경 할 byteArray
	 * @param Order BIG_EDIAN,Little_EDIAN 변수에 적용되어 있음
	 * @return float
	 */
	public static float bytesToFloat(byte[] value, Endian order) {
		int accum = 0;
		int i = 0;
		byte[] temp = value;

		temp = changeByteOrder(temp, order);

		for (int shiftBy = 0; shiftBy < 32; shiftBy += 8) {
			accum |= ((long) (temp[i] & 0xff)) << shiftBy;
			i++;
		}
		return Float.intBitsToFloat(accum);
	}

	/**
	 * @param oridata 읽을 바이트 배열
	 * @param start 읽을 바이트의 시작점
	 * @param len 읽을 바이트 배열 길이
	 * @param Order BIG_EDIAN,Little_EDIAN 변수에 적용되어 있음
	 * @return float
	 */
	public static float bytesTofloat(byte[] oridata, int start, int len, Endian order) {
		int accum = 0;
		int i = 0;
		byte[] temp = new byte[len];
		System.arraycopy(oridata, start, temp, 0, len);

		temp = changeByteOrder(temp, order);
		for (int shiftBy = 0; shiftBy < 32; shiftBy += 8) {
			accum |= ((long) (temp[i] & 0xff)) << shiftBy;
			i++;
		}
		return Float.intBitsToFloat(accum);
	}

	private static byte[] changeByteOrder(byte[] value, Endian order) {
		int idx = value.length;
		byte[] temp = new byte[idx];

		if (order == Endian.BIG) temp = value;
		else if (order == Endian.LITTLE) {
			for (int i = 0; i < idx; i++) temp[i] = value[idx - (i + 1)];
		}

		return temp;
	}
	
	/**
	 * value의 bitDigit번째의 값을 String으로 리턴.
	 * @param value
	 * @param bitDigit
	 * @return
	 */
	public static String intToBitDigitValue(int value, int bitDigit){
		if( integerToBitDigitValue(value, bitDigit)) return new String("1");
		return new String("0");
	}
	
	/**
	 * value의 bitDigit번째가1이면 true를 아니면 false를 리턴.
	 * @param value
	 * @param bitDigit
	 * @return
	 */
	public static boolean integerToBitDigitValue(int value, int bitDigit) {
		int bitVal = ((Double) Math.pow(2, bitDigit)).intValue();
		return (value & bitVal) == bitVal;
	}
	
	/**
	 * hex to byte[]
	 * @param hex
	 * @return
	 */
	public static byte[] hexToByteArray(String hex) {
	    if (hex == null || hex.length() == 0) return null;
	 
	    byte[] ba = new byte[hex.length() / 2];
	    for (int i = 0; i < ba.length; i++) ba[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
	    
	    return ba;
	}
}

