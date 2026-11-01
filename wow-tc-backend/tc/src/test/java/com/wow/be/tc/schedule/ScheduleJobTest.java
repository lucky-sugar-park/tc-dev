package com.wow.be.tc.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;

import com.wow.be.tc.ToolControlException;

/**
 * 실행 이력의 성공·실패 판정 — 정상 종료는 SUCCESS(돌려준 요약이 비고), 예외는 FAIL(코드·사유가 비고).
 * 예전에는 "결과가 null 이 아니면 SUCCESS" 라 실패를 결과 "FAIL" 로 남긴 Job 이 SUCCESS 로 기록됐다.
 */
class ScheduleJobTest {

	static class FakeJob extends ScheduleJob {
		final Callable<String> body;

		FakeJob(Callable<String> body, ScheduleJobExecHistoryService history) {
			this.body = body;
			this.execHistoryService = history;
		}

		@Override
		protected String executeJob(JobExecutionContext context) throws Exception {
			return body.call();
		}
	}

	private static JobExecutionContext context() throws Exception {
		JobExecutionContext ctx = mock(JobExecutionContext.class);
		when(ctx.getJobDetail()).thenReturn(JobBuilder.newJob(FakeJob.class).withIdentity("job-a", "TC").build());
		Scheduler scheduler = mock(Scheduler.class);
		when(scheduler.getSchedulerInstanceId()).thenReturn("node-1");
		when(ctx.getScheduler()).thenReturn(scheduler);
		when(ctx.getFireTime()).thenReturn(new Date(1_000));
		when(ctx.getNextFireTime()).thenReturn(new Date(61_000));
		return ctx;
	}

	private static ScheduleJobExecHistoryInfo run(Callable<String> body) throws Exception {
		ScheduleJobExecHistoryService history = mock(ScheduleJobExecHistoryService.class);
		new FakeJob(body, history).execute(context());
		ArgumentCaptor<ScheduleJobExecHistoryInfo> saved = ArgumentCaptor.forClass(ScheduleJobExecHistoryInfo.class);
		verify(history).saveScheduleJobExecHistory(saved.capture());
		return saved.getValue();
	}

	@Test
	void 정상_종료는_SUCCESS_이고_요약이_비고() throws Exception {
		ScheduleJobExecHistoryInfo h = run(() -> "3건 삭제");
		assertThat(h.getExecutionResult()).isEqualTo("SUCCESS");
		assertThat(h.getRemark()).isEqualTo("3건 삭제");
		assertThat(h.getJobName()).isEqualTo("job-a");
		assertThat(h.getInstanceId()).isEqualTo("node-1");
		assertThat(h.getExecutionTime()).isEqualTo(new Date(1_000));
		assertThat(h.getNextFireTime()).isEqualTo(new Date(61_000));
		assertThat(h.getElapsedMs()).isNotNull().isGreaterThanOrEqualTo(0L);
	}

	@Test
	void ToolControlException_은_FAIL_이고_코드와_사유가_비고() throws Exception {
		ScheduleJobExecHistoryInfo h = run(() -> {
			throw ToolControlException.of(ToolControlException.PLC_TIMEOUT, "응답 없음");
		});
		assertThat(h.getExecutionResult()).isEqualTo("FAIL");
		assertThat(h.getRemark()).isEqualTo(ToolControlException.PLC_TIMEOUT + ": 응답 없음");
	}

	@Test
	void 다른_예외도_FAIL_이고_예외_이름과_사유가_비고() throws Exception {
		ScheduleJobExecHistoryInfo h = run(() -> {
			throw new IllegalStateException("DB 끊김");
		});
		assertThat(h.getExecutionResult()).isEqualTo("FAIL");
		assertThat(h.getRemark()).isEqualTo("IllegalStateException: DB 끊김");
	}

	@Test
	void 이력_저장이_실패해도_Quartz_로_던지지_않는다() throws Exception {
		ScheduleJobExecHistoryService history = mock(ScheduleJobExecHistoryService.class);
		doThrow(new RuntimeException("insert 실패")).when(history).saveScheduleJobExecHistory(any());
		new FakeJob(() -> "ok", history).execute(context());
		verify(history).saveScheduleJobExecHistory(any());
	}
}
