package com.wow.be.tc.dispatch;

import com.wow.be.tc.interfs.InterfaceInfo;
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

	private final PlcConnectorInfo plcConnector;

	private final ProgrammableControllerAdapter adapter;

	/** 점 목록으로 만든 작업. {@code beforeExchange} 에서 바꿀 수 있다(새 작업으로) */
	@Setter
	private PlcOperation operation;

	/** 작업 결과 — 교환 뒤에 채워진다 */
	@Setter
	private PlcResult result;
}
