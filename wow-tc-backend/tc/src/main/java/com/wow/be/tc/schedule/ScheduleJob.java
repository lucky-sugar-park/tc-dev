package com.wow.be.tc.schedule;

import java.util.Date;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;

import com.wow.be.tc.ToolControlException;

import lombok.extern.slf4j.Slf4j;

/**
 * 스케줄 Job 의 공통 틀 — 실행하고, 실행마다 실행 이력(SCHEDULE_JOB_EXEC_HIST) 한 행을 남긴다.
 *
 * <ul>
 *   <li>{@link #executeJob} 가 정상으로 끝나면 SUCCESS, 예외를 던지면 FAIL 이다. 돌려준 문자열(성공)이나 예외의 코드·사유(실패)가 비고가 된다.
 *       예전에는 "{@code context.getResult()} 가 null 이 아니면 SUCCESS" 여서, 실패를 결과 "FAIL" 로 남긴 정리 Job 이 SUCCESS 로,
 *       결과를 넣지 않은 Job 은 늘 FAIL 로 남았다.</li>
 *   <li>이력에는 실행한 인스턴스(Quartz instanceId)와 걸린 시간도 남긴다 — 다중화에서 어느 서버가 돌렸는지 알 수 있다.</li>
 *   <li>예외는 Quartz 로 넘기지 않는다(넘기면 Quartz 가 오류로 기록할 뿐 다시 돌리지 않는다). 이력 저장이 실패해도 로그만 남긴다.</li>
 *   <li>필요한 빈은 필드에 {@code @Autowired} 로 받는다 — {@link AutowiringSpringBeanJobFactory} 가 Job 을 만들 때 주입한다.</li>
 * </ul>
 */
@Slf4j
public abstract class ScheduleJob implements Job {

	public static final String SUCCESS = "SUCCESS";

	public static final String FAIL = "FAIL";

	@Autowired
	protected ScheduleJobExecHistoryService execHistoryService;

	@Override
	public final void execute(JobExecutionContext context) {
		String jobName = context.getJobDetail().getKey().getName();
		long start = System.currentTimeMillis();
		String result = SUCCESS;
		String remark;
		try {
			remark = executeJob(context);
		} catch (ToolControlException e) {
			result = FAIL;
			remark = e.getErrorCode() + ": " + e.getMessage();
			log.warn("스케줄 Job 실패: {} — {}", jobName, remark);
		} catch (Exception e) {
			result = FAIL;
			remark = e.getClass().getSimpleName() + ": " + e.getMessage();
			log.error("스케줄 Job 실패: {}", jobName, e);
		}
		long elapsed = System.currentTimeMillis() - start;
		context.setResult(remark);
		log.info("스케줄 Job 끝: {}, {}, {}ms", jobName, result, elapsed);

		ScheduleJobExecHistoryInfo history = new ScheduleJobExecHistoryInfo();
		history.setJobName(jobName);
		history.setInstanceId(instanceIdOf(context));
		history.setExecutionResult(result);
		history.setExecutionTime(context.getFireTime() != null ? context.getFireTime() : new Date(start));
		history.setNextFireTime(context.getNextFireTime());
		history.setElapsedMs(elapsed);
		history.setRemark(remark);
		try {
			execHistoryService.saveScheduleJobExecHistory(history);
		} catch (RuntimeException e) {
			log.error("스케줄 Job 실행 이력을 남기지 못했습니다: {} {}", jobName, result, e);
		}
	}

	private static String instanceIdOf(JobExecutionContext context) {
		try {
			return context.getScheduler() != null ? context.getScheduler().getSchedulerInstanceId() : null;
		} catch (SchedulerException e) {
			return null;
		}
	}

	/**
	 * 할 일을 한다.
	 *
	 * @return 실행 이력 비고에 남길 짧은 요약
	 * @throws Exception 실패 — 코드·사유가 비고에 남는다({@link ToolControlException} 이면 오류 코드도)
	 */
	protected abstract String executeJob(JobExecutionContext context) throws Exception;
}
