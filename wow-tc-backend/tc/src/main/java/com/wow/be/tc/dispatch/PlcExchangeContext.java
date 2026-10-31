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
 * {@link PlcInterfaceCommand} 가 한 번의 PLC 교환에 쓰는 정보.
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

	/** 점 목록으로 만든 작업 */
	private final PlcOperation operation;

	/** 작업 결과 — 교환 뒤에 채워진다 */
	@Setter
	private PlcResult result;
}
