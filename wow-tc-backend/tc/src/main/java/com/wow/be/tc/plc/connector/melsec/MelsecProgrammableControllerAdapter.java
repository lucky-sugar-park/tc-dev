package com.wow.be.tc.plc.connector.melsec;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.plc.PlcRequest;
import com.wow.be.tc.plc.PlcResponse;
import com.wow.be.tc.plc.connector.tcp.TcpPlcAdapter;
import com.wow.be.tc.plc.protocol.melsec.MelsecDriver;
import com.wow.be.tc.utils.ByteUtil;
import com.wow.be.tc.utils.ByteUtil.Endian;

/**
 * MELSEC MC 프로토콜(3E·4E 프레임) 어댑터. 연결·재연결·시간 초과·연결 확인(루프백)은 {@link TcpPlcAdapter}, 프레임은
 * {@link MelsecDriver}·{@code McFrames}. 여기는 RAW 인터페이스(헤더·데이터 바이트 정의)의 요청·응답 객체만 다룬다.
 *
 * <p>DB 의 커넥터 {@code adapterClassName} 이 이 클래스 이름을 들고 있어 이름을 바꾸지 않는다.
 */
public class MelsecProgrammableControllerAdapter extends TcpPlcAdapter {

	@Override
	protected String protocol() {
		return MelsecDriver.PROTOCOL;
	}

	@Override
	public void request(PlcRequest req, PlcResponse res) throws ToolControlException {
		completeResponse(res, exchangeFrame(req.getReqId(), req.convertRequest()));
	}

	@Override
	public void completeResponse(PlcResponse res, byte[] frame) throws ToolControlException {
		res.splitHeaderAndReplyData(frame);
		int resultCode = ByteUtil.bytesToInt((byte[]) res.getReplyHeaders().get("resultCode"), Endian.LITTLE);
		res.setResultCode(resultCode);
		res.setResultDescription(resultCode == 0 ? "SUCCESS" : "ERROR");
	}

	@Override
	public PlcRequest newPlcRequestInstance() {
		return new MelsecPlcRequest();
	}

	@Override
	public PlcResponse newPlcResponseInstance() {
		return new MelsecPlcResponse();
	}
}
