package com.wow.be.tc.schedule.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.quartz.JobBuilder;

/**
 * 클러스터에서 같은 Job 이 겹쳐 돌지 않는다(docs/plc-ha-design.md §9.2 H10). Quartz 는 JobDetail 의 클래스에 붙은
 * {@code @DisallowConcurrentExecution} 을 보고 앞 실행이 끝날 때까지 같은 Job 의 다른 트리거를 막는다.
 */
class ScheduleJobConcurrencyTest {

	@Test
	void PLC_수집_Job_은_겹쳐_돌지_않는다() {
		assertThat(JobBuilder.newJob(PlcGeneralScheduleJob.class).build().isConcurrentExecutionDisallowed()).isTrue();
	}

	@Test
	void 정리_Job_은_겹쳐_돌지_않는다() {
		assertThat(JobBuilder.newJob(DataTableFlashJob.class).build().isConcurrentExecutionDisallowed()).isTrue();
	}
}
