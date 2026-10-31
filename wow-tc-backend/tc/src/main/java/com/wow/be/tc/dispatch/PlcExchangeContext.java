package com.wow.be.tc.dispatch;

import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.plc.PlcRequest;
import com.wow.be.tc.plc.PlcResponse;
import com.wow.be.tc.plc.connector.ProgrammableControllerAdapter;
import com.wow.be.tc.plc.management.PlcConnectorInfo;
import com.wow.be.tc.plc.protocol.PlcOperation;
import com.wow.be.tc.plc.protocol.PlcResult;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * {@link PlcInterfaceCommand} 가 한 번의 PLC 교환에 쓰는 정보. 하위 커맨드의 전·후처리 훅에 넘긴다.
 */
@Getter
@AllArgsConstructor
public class PlcExchangeContext {

	private final TcMessageRequest request;

	private final TcMessageResponse response;

	/** 요청 인터페이스 (opcode) */
	private final InterfaceInfo requestInterface;

	/** 응답 인터페이스 — PLC 응답 헤더·데이터를 해석하는 정의 */
	private final InterfaceInfo replyInterface;

	private final PlcConnectorInfo plcConnector;

	private final ProgrammableControllerAdapter adapter;

	/** RAW 인터페이스의 PLC 요청 — POINTS 면 null */
	private final PlcRequest plcRequest;

	/** RAW 인터페이스의 PLC 응답 — POINTS 면 null */
	private final PlcResponse plcResponse;

	/** POINTS 인터페이스의 작업 — RAW 면 null. {@code beforeExchange} 에서 바꿀 수 있다(새 작업으로) */
	@Setter
	private PlcOperation operation;

	/** POINTS 인터페이스의 결과 — 교환 뒤에 채워진다 */
	@Setter
	private PlcResult result;

	public PlcExchangeContext(TcMessageRequest request, TcMessageResponse response, InterfaceInfo requestInterface,
			InterfaceInfo replyInterface, PlcConnectorInfo plcConnector, ProgrammableControllerAdapter adapter,
			PlcRequest plcRequest, PlcResponse plcResponse) {
		this(request, response, requestInterface, replyInterface, plcConnector, adapter, plcRequest, plcResponse, null, null);
	}

	public boolean isPoints() {
		return operation != null;
	}
}
