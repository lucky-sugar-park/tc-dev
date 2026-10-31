package com.wow.be.tc.plc.connector.melsec;

import java.nio.ByteBuffer;
import java.util.Map;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.FrameFormat;
import com.wow.be.tc.Types.RequestType;
import com.wow.be.tc.plc.PlcRequest;
import com.wow.be.tc.utils.ByteUtil.Endian;
import com.wow.be.tc.utils.ByteUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MelsecPlcRequest extends PlcRequest {
	
	private static final int MAX_REQ_DATA_LEN=2048;

	@Override
	public byte[] convertRequest() throws ToolControlException {
		log.debug("");
		byte[] requestData=null;

		switch(super.getFrameFormat()) {
		case ASCII:
			requestData=convertAsAscii();
			break;
		case BINARY:
		default:
			requestData=convertAsBinary();
			break;
		}
		
		log.debug("CONVERTED PLC REQ: {}", java.util.HexFormat.of().withUpperCase().formatHex(requestData));
		return requestData;
	}
	
	private void checkLength(String name, FrameFormat frameFormat, int realSize, int expectedSize) throws ToolControlException {
		if(realSize!=expectedSize) {
			throw new ToolControlException("헤더 길이가 맞지 않습니다: " + name + " (" + frameFormat + ", 기대 " + expectedSize + ", 실제 " + realSize + ")");
		}
	}
	
	private byte[] convertAsAscii() throws ToolControlException {
		Map<String, Object> headers=super.getHeaders();
		StringBuffer sb=new StringBuffer();
		
		String subHeader=(String)headers.get("subHeader");
		checkLength("subHeader", super.getFrameFormat(), subHeader.length(), 4);
		sb.append(subHeader);
		
		String netNumber=(String)headers.get("netNumber");
		checkLength("netNumber", super.getFrameFormat(), netNumber.length(), 2);
		sb.append(netNumber);
		
		String plcNumber=(String) headers.get("plcNumber");
		checkLength("plcNumber", super.getFrameFormat(), plcNumber.length(), 2);
		sb.append(plcNumber);
		
		String opponentIONumber=(String) headers.get("opponentIONumber");
		checkLength("opponentIONumber", super.getFrameFormat(), opponentIONumber.length(), 4);
		sb.append(opponentIONumber);
		
		String opponentNumber=(String) headers.get("opponentNumber");
		checkLength("opponentNumber", super.getFrameFormat(), opponentNumber.length(), 2);
		sb.append(opponentNumber);
		
		String timer=(String) headers.get("timer");
		checkLength("timer", super.getFrameFormat(), timer.length(), 4);
		
		String command=(String) headers.get("command");
		checkLength("command", super.getFrameFormat(), command.length(), 4);
		
		String subCommand=(String) headers.get("subCommand");
		checkLength("subCommand", super.getFrameFormat(), subCommand.length(), 4);
		
		String deviceCode=(String) headers.get("deviceCode");
		checkLength("deviceCode", super.getFrameFormat(), deviceCode.length(), 2);
		
		String startAddress=(String) headers.get("startAddress");
		checkLength("startAddress", super.getFrameFormat(), startAddress.length(), 6);
		
		int wordCountLen=4, realDataLen=super.getData()==null?0:super.getData().length;
		int reqDataLen=timer.length()+command.length()+subCommand.length()+deviceCode.length()+startAddress.length()+wordCountLen+realDataLen;
		
		sb.append(ByteUtil.byteToString(ByteUtil.intToBytes(reqDataLen, 2, Endian.LITTLE)));
		sb.append(timer);
		sb.append(command);
		sb.append(subCommand);
		sb.append(deviceCode);
		sb.append(startAddress);
		sb.append(ByteUtil.byteToString(ByteUtil.intToBytes(realDataLen>>1, 2, Endian.LITTLE)));
		
		byte[] headerBytes=sb.toString().getBytes();
		ByteBuffer bb=ByteBuffer.allocate(headerBytes.length+realDataLen);
		bb.put(headerBytes);
		if(super.getData()!=null) {
			bb.put(super.getData());
		}

		return bb.array();
	}
	
	private byte[] convertAsBinary() throws ToolControlException {
		int len=0;
		Map<String, Object> headers=super.getHeaders();
		
		byte[] subHeaderProp=(byte[])headers.get("subHeader");
		checkLength("subHeader", super.getFrameFormat(), subHeaderProp.length, 2);
		len+=subHeaderProp.length;
		
		byte[] netNumberProp=(byte[]) headers.get("netNumber");
		checkLength("netNumber", super.getFrameFormat(), netNumberProp.length, 1);
		len+=netNumberProp.length;
		
		byte[] plcNumberProp=(byte[]) headers.get("plcNumber");
		checkLength("plcNumber", super.getFrameFormat(), plcNumberProp.length, 1);
		len+=plcNumberProp.length;
		
		byte[] opponentIONumberProp=(byte[]) headers.get("opponentIONumber");
		checkLength("opponentIONumber", super.getFrameFormat(), opponentIONumberProp.length, 2);
		len+=opponentIONumberProp.length;
		
		byte[] opponentNumberProp=(byte[]) headers.get("opponentNumber");
		checkLength("opponentNumber", super.getFrameFormat(), opponentNumberProp.length, 1);
		len+=opponentNumberProp.length;
		
		byte[] reqDataLenProp=(byte[]) headers.get("reqDataLen");
		
		byte[] timerProp=(byte[]) headers.get("timer");
		checkLength("timer", super.getFrameFormat(), timerProp.length, 2);
		
		byte[] commandProp=(byte[]) headers.get("command");
		checkLength("command", super.getFrameFormat(), commandProp.length, 2);
		// 0401 일괄 읽기(01 04), 1401 일괄 쓰기(01 14). 예전에는 && 로 묶어 READ 에 쓰기 명령이 와도 통과했다(결함 M2)
		boolean read=commandProp[0]==0x01 && commandProp[1]==0x04;
		boolean write=commandProp[0]==0x01 && commandProp[1]==0x14;
		if((super.getReqType()==RequestType.READ && !read) || (super.getReqType()==RequestType.WRITE && !write)) {
			throw ToolControlException.of(ToolControlException.INTERFACE_MISCONFIGURED,
				"요청 유형(" + super.getReqType() + ")에 맞지 않는 명령(command)입니다: " + java.util.HexFormat.of().formatHex(commandProp));
		}
		byte[] subCommandProp=(byte[]) headers.get("subCommand");
		checkLength("subCommand", super.getFrameFormat(), subCommandProp.length, 2);

		byte[] startAddressProp=(byte[]) headers.get("startAddress");
		checkLength("startAddress", super.getFrameFormat(), startAddressProp.length, 3);

		byte[] deviceCodeProp=(byte[]) headers.get("deviceCode");
		checkLength("deviceCode", super.getFrameFormat(), deviceCodeProp.length, 1);
		
		byte[] wordCountProp=(byte[]) headers.get("wordCount");
		checkLength("wordCount", super.getFrameFormat(), wordCountProp.length, 2);
		// 쓰기 데이터의 워드 수가 헤더의 wordCount 와 같아야 한다 — 다르면 PLC 가 엉뚱한 범위를 쓰거나 오류로 답한다(결함 M3)
		if(write && super.getData()!=null) {
			int wordCount=ByteUtil.bytesToInt(wordCountProp, ByteUtil.Endian.LITTLE);
			if(super.getData().length!=wordCount*2) {
				throw ToolControlException.of(ToolControlException.INTERFACE_MISCONFIGURED,
					"쓰기 데이터(" + super.getData().length + "바이트)가 헤더 wordCount(" + wordCount + "워드)와 맞지 않습니다: " + super.getReqName());
			}
		}
		
		int reqDataLenPropLen=reqDataLenProp.length;
//		int wordCount=(super.getData()==null?0:super.getData().length)>>1;
		int reqLen=timerProp.length+commandProp.length+subCommandProp.length+startAddressProp.length+deviceCodeProp.length+wordCountProp.length;
		reqLen+=(super.getData()==null || (commandProp[0]==0x01 && commandProp[1]==0x04) ? 0 : super.getData().length);
		
		ByteBuffer bb=ByteBuffer.allocate(len+reqDataLenPropLen+reqLen);
		bb.put(subHeaderProp);
		bb.put(netNumberProp);
		bb.put(plcNumberProp);
		bb.put(opponentIONumberProp);
		bb.put(opponentNumberProp);
		bb.put(ByteUtil.intToBytes(reqLen, 2, ByteUtil.Endian.LITTLE));
		bb.put(timerProp);
		bb.put(commandProp);
		bb.put(subCommandProp);
		bb.put(startAddressProp);
		bb.put(deviceCodeProp);
//		bb.put(ByteUtil.intToBytes(wordCount, 2, ByteUtil.Endian.LITTLE));
		bb.put(wordCountProp);
		
		if(super.getData()!=null) {
			bb.put(super.getData());
		}
		
		return bb.array();
	}
}
