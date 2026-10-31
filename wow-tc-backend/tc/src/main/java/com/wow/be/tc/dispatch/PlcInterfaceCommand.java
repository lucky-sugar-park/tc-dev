package com.wow.be.tc.dispatch;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wow.be.framework.MesGeneralException;
import com.wow.be.framework.dispatch.AbstractMessageCommand;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.InterfaceService;
import com.wow.be.tc.plc.connector.PlcAdapterManager;
import com.wow.be.tc.plc.connector.ProgrammableControllerAdapter;
import com.wow.be.tc.plc.management.PlcConnectorInfo;
import com.wow.be.tc.plc.management.PlcConnectorManagementService;
import com.wow.be.tc.plc.protocol.PlcDriver;
import com.wow.be.tc.plc.protocol.PlcDrivers;
import com.wow.be.tc.plc.protocol.PlcOperation;
import com.wow.be.tc.plc.protocol.PlcResult;
import com.wow.be.tc.plc.protocol.PlcStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 인터페이스(opcode) 정의대로 PLC 를 읽거나 쓰는 기본 커맨드. 레거시 GenericMessageCommand 를 대신한다.
 *
 * <ol>
 *   <li>인터페이스와 PLC 커넥터를 찾는다</li>
 *   <li>점 목록과 유효 설정으로 PLC 작업({@link PlcOperation})을 만든다</li>
 *   <li>{@link #beforeExchange} → 프로토콜 드라이버로 읽기·쓰기 → {@link #afterExchange}</li>
 *   <li>PLC 상태가 정상이 아니면 응답을 업무 실패({@code ERR_502_TC_PLC_ERROR})로 표시한다</li>
 * </ol>
 *
 * <p>결과 전달(callback·WebHook)과 감사 기록은 커맨드 밖({@code ResultDeliveryService}, {@code AuditDispatchInterceptor})에서 한다.
 *
 * <p>의존성은 필드 주입이다 — 하위 클래스가 생성자를 다시 쓰지 않아도 되게 하기 위해서다.
 */
@Slf4j
@Component
public class PlcInterfaceCommand extends AbstractMessageCommand<TcMessageRequest, TcMessageResponse> {

	@Autowired
	protected InterfaceService interfaceService;

	@Autowired
	protected PlcConnectorManagementService plcConnectorService;

	@Autowired
	protected PlcResponseService plcResponseService;

	@Override
	protected TcMessageResponse createResponse(TcMessageRequest request) {
		return new TcMessageResponse().fillReplyHeader(request);
	}

	@Override
	protected void doExecute(TcMessageRequest request, TcMessageResponse response) throws MesGeneralException {
		PlcExchangeContext ctx = prepare(request, response);

		beforeExchange(ctx);

		exchange(ctx);

		afterExchange(ctx);

		log.debug("PLC 교환 완료: opcode={}, transactionId={}, success={}",
			request.getOpcode(), request.getTransactionId(), response.getSuccess());
	}

	/**
	 * 점 읽기·쓰기를 프로토콜 드라이버에 맡긴다(docs/plc-protocol-design.md §3). 쓰기에 readBack 이면 같은 점을
	 * 다시 읽어 응답 data 에 넣는다. 변경 감지는 값(data) 비교(결정 P10).
	 */
	private void exchange(PlcExchangeContext ctx) throws MesGeneralException {
		TcMessageRequest request = ctx.getRequest();
		TcMessageResponse response = ctx.getResponse();
		PlcOperation op = ctx.getOperation();
		PlcResult result = ctx.getAdapter().execute(op);
		if (result.status().ok() && op.kind() == PlcOperation.Kind.WRITE && ctx.getRequestInterface().isReadBack()) {
			result = ctx.getAdapter().execute(new PlcOperation(op.protocol(), op.settings(), PlcOperation.Kind.READ,
				op.points(), Map.of(), op.reqId()));
		}
		ctx.setResult(result);

		PlcStatus status = result.status();
		response.setPlcStatus(status);
		response.setResultCode((int) status.code());
		response.setResultDescription(status.ok() ? "SUCCESS" : "ERROR");
		response.getData().putAll(result.values());
		response.setChanged(plcResponseService.checkChangedAndRemember(request.getOpcode(), canonical(result.values())));
		if (!status.ok()) {
			response.fail(ToolControlException.PLC_ERROR, status.text() + " (" + request.getOpcode() + ")");
		}
	}

	private static final ObjectMapper CANONICAL = JsonMapper.builder()
		.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true).build();

	/** 값 비교용 — 키 순서를 고정한 JSON 바이트 */
	private static byte[] canonical(Map<String, Object> values) {
		try {
			return CANONICAL.writeValueAsBytes(values);
		} catch (JsonProcessingException e) {
			return null;  // 비교하지 못하면 "바뀜"
		}
	}

	/** PLC 교환 전에 호출된다. 요청 데이터를 고치거나, 조건이 맞지 않으면 예외로 막는다. */
	protected void beforeExchange(PlcExchangeContext ctx) throws MesGeneralException {
	}

	/** PLC 응답을 해석한 뒤 호출된다. 응답 data 를 가공하거나 더한다. */
	protected void afterExchange(PlcExchangeContext ctx) throws MesGeneralException {
	}

	/** 인터페이스·PLC 커넥터·어댑터를 찾고 점 목록으로 PLC 작업을 만든다. */
	protected PlcExchangeContext prepare(TcMessageRequest request, TcMessageResponse response) throws MesGeneralException {
		String opcode = request.getOpcode();

		InterfaceInfo iInfo = interfaceService.findInterfaceByName(opcode);
		if (iInfo == null) {
			// InterfaceCommandResolver 가 찾은 뒤 지워진 경우
			throw ToolControlException.of(ToolControlException.INTERFACE_MISCONFIGURED, "인터페이스가 없습니다: " + opcode);
		}
		if (!iInfo.isUse()) {
			throw ToolControlException.of(ToolControlException.INTERFACE_DISABLED, "사용하지 않는 인터페이스입니다: " + opcode);
		}
		PlcConnectorInfo pInfo = plcConnectorService.findPlcConnectorByName(iInfo.getPlcName());
		if (pInfo == null) {
			throw ToolControlException.of(ToolControlException.PLC_NOT_FOUND,
				"인터페이스에 연결된 PLC 커넥터가 없습니다: " + iInfo.getPlcName() + " (" + opcode + ")");
		}
		ProgrammableControllerAdapter adapter = PlcAdapterManager.getPlcManager().get(pInfo.getName());
		if (adapter == null) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE,
				"게시(publish)된 PLC 커넥터가 아닙니다: " + pInfo.getName());
		}
		if (iInfo.getPoints() == null || iInfo.getPoints().isEmpty()) {
			throw ToolControlException.of(ToolControlException.INTERFACE_MISCONFIGURED, "인터페이스에 점이 없습니다: " + opcode);
		}
		Map<String, Object> settings;
		try {
			PlcDriver driver = PlcDrivers.of(pInfo.getProtocol());
			// 유효 설정 = 커넥터 설정 ⊕ 인터페이스 settings(docs/interface-detail-design.md §3) — 원격 어댑터에도 작업에 실려 간다
			settings = driver.effectiveSettings(driver.normalizeSettings(pInfo.getSettings(),
				pInfo.getMessageFrameFormat() != null ? pInfo.getMessageFrameFormat().name() : null), iInfo.getSettings());
		} catch (IllegalArgumentException e) {
			throw ToolControlException.of(ToolControlException.INTERFACE_MISCONFIGURED, "PLC 커넥터·인터페이스 설정이 잘못되었습니다: " + e.getMessage());
		}
		PlcOperation.Kind kind = iInfo.getInterfaceType() == InterfaceType.WRITE ? PlcOperation.Kind.WRITE : PlcOperation.Kind.READ;
		Map<String, Object> values = kind == PlcOperation.Kind.WRITE && request.getData() != null ? request.getData() : Map.of();
		PlcOperation op = new PlcOperation(pInfo.getProtocol(), settings, kind, iInfo.getPoints(), values, request.getTransactionId());
		return new PlcExchangeContext(request, response, iInfo, pInfo, adapter, op, null);
	}
}
