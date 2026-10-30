package com.wow.be.tc.dispatch;

import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.plc.PlcRequest;
import com.wow.be.tc.plc.PlcResponse;
import com.wow.be.tc.plc.connector.ProgrammableControllerAdapter;
import com.wow.be.tc.plc.management.PlcConnectorInfo;

import lombok.AllArgsConstructor;
import lombok.Getter;

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

	private final PlcRequest plcRequest;

	private final PlcResponse plcResponse;
}
