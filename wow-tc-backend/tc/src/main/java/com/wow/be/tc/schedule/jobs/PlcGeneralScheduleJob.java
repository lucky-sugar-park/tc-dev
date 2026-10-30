package com.wow.be.tc.schedule.jobs;

import org.quartz.JobExecutionContext;

import com.wow.be.framework.MesGeneralException;
import com.wow.be.framework.dispatch.MessageDispatcher;
import com.wow.be.framework.dispatch.RequestMessage;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.delivery.ResultDeliveryService;
import com.wow.be.tc.dispatch.TcMessageRequest;
import com.wow.be.tc.dispatch.TcMessageResponse;
import com.wow.be.tc.schedule.ScheduleJob;

import lombok.extern.slf4j.Slf4j;

/**
 * 일정 주기로 인터페이스 하나를 실행하는 범용 PLC Job. REST 요청과 같은 디스패처·결과 전달 경로를 탄다.
 *
 * <ul>
 *   <li>JobDataMap 의 {@code interfaceName} 이 opcode 다. JobDataMap 의 나머지 값도 요청 data 에 넣는다(레거시와 같음).</li>
 *   <li>결과(성공·실패)는 응답 인터페이스의 WebHook 으로 보낸다. 레거시처럼 이 Job 이 직접 WebHook 을 부르지 않는다.</li>
 *   <li>디스패치가 실패하거나 PLC 가 오류 종료 코드를 돌려주면, 결과를 전달한 뒤 ToolControlException 을 던져
 *       실행 이력이 FAIL 로 남는다.</li>
 * </ul>
 */
@Slf4j
public class PlcGeneralScheduleJob extends ScheduleJob {

	public static final String SENDER = "Internal-Schedule-Job";

	public static final String RECEIVER = "ToolControl-System";

	private final MessageDispatcher messageDispatcher;

	private final ResultDeliveryService resultDeliveryService;

	public PlcGeneralScheduleJob() {
		super();
		messageDispatcher = applicationContext.getBean(MessageDispatcher.class);
		resultDeliveryService = applicationContext.getBean(ResultDeliveryService.class);
	}

	@Override
	public void executeJob(JobExecutionContext context) throws ToolControlException {
		String interfaceName = (String) context.getMergedJobDataMap().get("interfaceName");
		if (interfaceName == null || interfaceName.isBlank()) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST,
				"스케줄 Job 에 interfaceName 이 없습니다: " + context.getJobDetail().getKey().getName());
		}

		TcMessageRequest request = new TcMessageRequest();
		request.setTransactionId(RequestMessage.generateTransactionId());
		request.setMessageId(RequestMessage.generateMessageId());
		request.setOpcode(interfaceName);
		request.setSender(SENDER);
		request.setReceiver(RECEIVER);
		context.getJobDetail().getJobDataMap().forEach(request::addData);

		TcMessageResponse response;
		try {
			response = messageDispatcher.dispatch(request);
		} catch (MesGeneralException e) {
			TcMessageResponse failure = new TcMessageResponse().fillReplyHeader(request);
			failure.fail(e.getErrorCode(), e.getMessage());
			resultDeliveryService.deliver(resultDeliveryService.targetsOf(request, false), request, failure);
			throw new ToolControlException(e.getErrorCode(), "스케줄 PLC 요청을 처리하지 못했습니다: " + interfaceName + " — " + e.getMessage(), e);
		}

		resultDeliveryService.deliver(resultDeliveryService.targetsOf(request, false), request, response);
		if (Boolean.FALSE.equals(response.getSuccess())) {
			// PLC 가 오류 종료 코드를 돌려줌 — 결과는 전달하되 실행 이력은 FAIL 로 남긴다
			throw ToolControlException.of(response.getErrorCode(), response.getErrorMessage());
		}
		context.setResult(response);
	}
}
