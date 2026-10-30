package com.wow.be.tc.interfs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.wow.be.framework.AbstractInfo;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.PropType;
import com.wow.be.tc.utils.ByteUtil.Endian;
import com.wow.be.tc.utils.ByteUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@Data
@EqualsAndHashCode(callSuper=false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropInfo extends AbstractInfo {

	private long interfaceId;
	
	private String oper;

	private String name;
	
	private int order;

	private PropType type;

	@JsonIgnore
	private Object value;
	
	private String strValue;

	/**
	 * length for byte array
	 */
	private int length;

	private String description;

	public PropInfo() {
		super();
	}

	public void setValue(String value) throws ToolControlException {
		if(value==null || "".equals(value)) return;

		switch(type) {
		case BOOLEAN:
			this.value=Boolean.parseBoolean(value);
			break;
		case INT:
			this.value=Integer.parseInt(value);
			break;
		case SHORT:
			this.value=Short.parseShort(value);
			break;
		case LONG:
			this.value=Long.parseLong(value);
			break;
		case DOUBLE:
			this.value=Double.parseDouble(value);
			break;
		case BYTE:
			byte bytes[]=ByteUtil.hexToByteArray(value);
//			if(this.length!=bytes.length) {
//				throw new RuntimeException();
//			}
			this.value=bytes;
			break;
		case STRING:
		default:
			if(this.length!=value.length()) {
				throw new IllegalArgumentException("속성 값의 길이가 정의한 길이와 다릅니다: " + this.name + " (정의 " + this.length + ", 실제 " + value.length() + ")");
			}
			this.value=value;
			break;
		}
		this.strValue=value;
	}

	public Object getValue() {
		return this.value;
//		return getValueAsString();
	}

	@JsonIgnore
	public String getValueAsString() {
		if(value==null) return "";
		
		switch(type) {
		case BOOLEAN:
			return ((Boolean)value).toString();
		case INT:
			return ((Integer)value).toString();
		case SHORT:
			return ((Short)value).toString();
		case LONG:
			return ((Long)value).toString();
		case DOUBLE:
			return ((Double)value).toString();
		case BYTE:
			// 바이트마다 두 자리 16진수 — strValue 와 같은 형식("FF03"). 레거시는 첫 바이트만 반복하고 앞자리 0 을 빼먹었다
			return java.util.HexFormat.of().withUpperCase().formatHex((byte[])value);
		case STRING:
		default:
			return (String)value;
		}
	}
	
	/**
	 * 요청 값을 PLC 로 보낼 바이트로 바꾼다. 값은 JSON 에서 오므로 숫자는 Integer·Long·Double 중 무엇이든 올 수 있다 —
	 * 형에 맞춰 바꾸고, 범위를 넘으면 {@link IllegalArgumentException} 을 던진다(호출하는 쪽이 400 으로 바꾼다).
	 * 예전에는 {@code (Short) value} 처럼 강제 변환해 JSON 숫자로 SHORT·LONG·DOUBLE 을 쓰면 늘 ClassCastException(500)이었다.
	 */
	public byte[] extractRealDataAsBytes(Object realData, Endian endian) {
		switch(type) {
		case BOOLEAN:
			boolean vb=realData instanceof String str ? Boolean.parseBoolean(str.trim()) : (Boolean)realData;
			return vb?new byte[] { 0x01 }: new byte[] { 0x00 };
		case INT:
			return ByteUtil.intToBytes((int) toLong(realData, Integer.MIN_VALUE, Integer.MAX_VALUE), length, endian);
		case SHORT:
			return ByteUtil.shortToBytes((short) toLong(realData, Short.MIN_VALUE, Short.MAX_VALUE), length, endian);
		case LONG:
			return ByteUtil.longToBytes(toLong(realData, Long.MIN_VALUE, Long.MAX_VALUE), length, endian);
		case DOUBLE:
			double vd=realData instanceof String str ? Double.parseDouble(str.trim()) : ((Number)realData).doubleValue();
			return ByteUtil.doubleToBytes(vd, length, endian);
		case BYTE:
			return (byte[])realData;
		case STRING:
		default:
			return ((String)realData).getBytes();
		}
	}

	/** 정수 값. 문자열·정수형 숫자를 받고, 소수점이 있거나 범위를 넘으면 거부한다. */
	private long toLong(Object realData, long min, long max) {
		long v;
		if(realData instanceof String str) {
			v=Long.parseLong(str.trim());
		} else if(realData instanceof Number num) {
			if(num.doubleValue()!=Math.rint(num.doubleValue())) {
				throw new IllegalArgumentException(name + " 은 정수여야 합니다: " + realData);
			}
			v=num.longValue();
		} else {
			throw new IllegalArgumentException(name + " 값의 형이 맞지 않습니다(" + type + "): " + realData);
		}
		if(v<min || v>max) {
			throw new IllegalArgumentException(name + " 값이 " + type + " 범위(" + min + "~" + max + ")를 넘습니다: " + v);
		}
		return v;
	}

	@Override
	public String toPlainText() {
		StringBuffer sb=new StringBuffer();
		sb.append("interfaceId: " + interfaceId + "\n");
		sb.append("name: " + name + "\n");
		sb.append("order: " + order + "\n");		
		sb.append("type: " + type.name() + "\n");
		sb.append("length: " + length + "\n");
		sb.append("value: " + this.getValueAsString() + "\n");
		sb.append("description: " + description);

		return sb.toString();
	}
}
