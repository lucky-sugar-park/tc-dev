package com.wow.be.tc.schedule.jobs;

import java.util.List;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

import com.wow.be.framework.MesGeneralException;
import com.wow.be.framework.dispatch.MessageDispatcher;
import com.wow.be.framework.dispatch.RequestMessage;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.delivery.DeliveryTarget;
import com.wow.be.tc.delivery.ResultDeliveryService;
import com.wow.be.tc.dispatch.TcMessageRequest;
import com.wow.be.tc.dispatch.TcMessageResponse;
import com.wow.be.tc.schedule.ScheduleJob;

/**
 * 일정 주기로 인터페이스 하나를 실행하는 PLC Job. REST 요청과 같은 디스패처·결과 전달 경로를 탄다.
 *
 * <ul>
 *   <li>속성 {@code interfaceName} 이 opcode 다. 나머지 속성은 요청 data 다 — WRITE 인터페이스면 점 이름마다 쓸 값
 *       (저장할 때 {@code ScheduleJobValidator} 가 점 이름과 맞는지 본다).</li>
 *   <li>결과(성공·실패)는 인터페이스의 전달 정의대로 보낸다.</li>
 *   <li>디스패치가 실패하거나 PLC 가 오류 종료 코드를 돌려주면, 결과를 전달한 뒤 예외를 던져 실행 이력이 FAIL 로 남는다.</li>
 *   <li>속성 {@code auditMode} 가 {@code FAILURE_ONLY} 면 성공한 실행은 송수신 기록을 남기지 않는다 — 실패하면 요청·응답·실패한 전달을
 *       남긴다(docs/data-retention.md §6.1). 없거나 {@code ALL} 이면 모두 남긴다.</li>
 *   <li>비고는 거래 ID·값 수·전달 대상 수 요약이다 — 값 자체는 송수신 기록에서 거래 ID 로 본다.</li>
 *   <li>같은 Job 은 클러스터 전체에서 한 번에 하나만 돈다. PLC 가 느려 실행이 주기보다 길어지면 다음 실행은 앞 실행이 끝날 때까지
 *       기다린다 — 같은 PLC 수집이 겹쳐 값 순서가 섞이거나 결과 행이 겹치지 않게(docs/plc-ha-design.md §9.2).</li>
 * </ul>
 */
@DisallowConcurrentExecution
public class PlcGeneralScheduleJob extends ScheduleJob {

	public static final String INTERFACE_NAME = "interfaceName";

	public static final String AUDIT_MODE = "auditMode";

	public static final String AUDIT_ALL = "ALL";

	public static final String AUDIT_FAILURE_ONLY = "FAILURE_ONLY";

	public static final String SENDER = "Internal-Schedule-Job";

	public static final String RECEIVER = "ToolControl-System";

	@Autowired
	private MessageDispatcher messageDispatcher;

	@Autowired
	private ResultDeliveryService resultDeliveryService;

	@Override
	protected String executeJob(JobExecutionContext context) throws ToolControlException {
		JobDataMap properties = context.getMergedJobDataMap();
		String interfaceName = properties.getString(INTERFACE_NAME);
		if (interfaceName == null || interfaceName.isBlank()) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "속성 interfaceName 이 없습니다");
		}

		TcMessageRequest request = new TcMessageRequest();
		request.setTransactionId(RequestMessage.generateTransactionId());
		request.setMessageId(RequestMessage.generateMessageId());
		request.setOpcode(interfaceName);
		request.setSender(SENDER);
		request.setReceiver(RECEIVER);
		boolean failuresOnly = auditFailuresOnly(properties.getString(AUDIT_MODE));
		request.setAuditFailuresOnly(failuresOnly);
		properties.forEach((key, value) -> {
			if (!INTERFACE_NAME.equals(key) && !AUDIT_MODE.equals(key)) request.addData(key, value);
		});

		List<DeliveryTarget> targets = resultDeliveryService.targetsOf(request, false);
		TcMessageResponse response;
		try {
			response = messageDispatcher.dispatch(request);
		} catch (MesGeneralException e) {
			TcMessageResponse failure = new TcMessageResponse().fillReplyHeader(request);
			failure.fail(e.getErrorCode(), e.getMessage());
			resultDeliveryService.deliver(targets, request, failure);
			throw new ToolControlException(e.getErrorCode(),
				interfaceName + " 요청 실패 (trx " + request.getTransactionId() + ") — " + e.getMessage(), e);
		}

		resultDeliveryService.deliver(targets, request, response);
		if (Boolean.FALSE.equals(response.getSuccess())) {
			// PLC 가 오류 종료 코드를 돌려줌 — 결과는 전달하되 실행 이력은 FAIL 로 남긴다
			throw ToolControlException.of(response.getErrorCode(),
				interfaceName + " PLC 오류 (trx " + request.getTransactionId() + ") — " + response.getErrorMessage());
		}
		int values = response.getData() != null ? response.getData().size() : 0;
		return interfaceName + " 성공 (trx " + request.getTransactionId() + ") — 값 " + values + "개, 전달 " + targets.size() + "곳"
			+ (failuresOnly ? " (성공은 송수신 기록 생략)" : "");
	}

	/** 속성 auditMode — 비었으면 ALL. 그 밖의 값은 저장할 때 막는다({@code ScheduleJobValidator}) */
	public static boolean auditFailuresOnly(String auditMode) throws ToolControlException {
		if (auditMode == null || auditMode.isBlank() || AUDIT_ALL.equals(auditMode.trim())) return false;
		if (AUDIT_FAILURE_ONLY.equals(auditMode.trim())) return true;
		throw ToolControlException.of(ToolControlException.INVALID_REQUEST,
			"속성 auditMode 는 " + AUDIT_ALL + "·" + AUDIT_FAILURE_ONLY + " 중 하나입니다: " + auditMode);
	}
}
