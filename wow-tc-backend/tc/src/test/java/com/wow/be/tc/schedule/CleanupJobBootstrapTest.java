package com.wow.be.tc.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.wow.be.tc.schedule.jobs.DataTableFlashJob;
import com.wow.be.tc.schedule.jobs.PlcGeneralScheduleJob;

/** 기본 정리 Job 은 그 표를 정리하는 Job 이 없을 때만 만든다(docs/data-retention.md §3). */
class CleanupJobBootstrapTest {

	@Test
	void 같은_표를_정리하는_Job_이_있으면_있다고_본다() {
		ScheduleJobInfo job = job(DataTableFlashJob.class.getName(), "EXCHANGED_MESSAGE_AUDIT");
		assertThat(CleanupJobBootstrap.cleans(job, CleanupTarget.EXCHANGED_MESSAGE_AUDIT)).isTrue();
		assertThat(CleanupJobBootstrap.cleans(job, CleanupTarget.SCHEDULE_JOB_EXEC_HIST)).isFalse();
	}

	@Test
	void 다른_종류의_Job_은_정리_Job_이_아니다() {
		ScheduleJobInfo job = job(PlcGeneralScheduleJob.class.getName(), "EXCHANGED_MESSAGE_AUDIT");
		assertThat(CleanupJobBootstrap.cleans(job, CleanupTarget.EXCHANGED_MESSAGE_AUDIT)).isFalse();
	}

	private static ScheduleJobInfo job(String className, String target) {
		ScheduleJobInfo job = new ScheduleJobInfo();
		job.setJobClassName(className);
		ScheduleJobDetailInfo d = new ScheduleJobDetailInfo();
		d.setEntryKey(DataTableFlashJob.CLEANUP_TARGET);
		d.setEntryValue(target);
		job.addJobDetail(d);
		return job;
	}
}
