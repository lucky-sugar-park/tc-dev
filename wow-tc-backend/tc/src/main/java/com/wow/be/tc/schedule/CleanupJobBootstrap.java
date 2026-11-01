package com.wow.be.tc.schedule;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.wow.be.tc.schedule.jobs.DataTableFlashJob;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 기본 정리 Job (docs/data-retention.md §3). 기동할 때 <b>정리할 표마다, 그 표를 정리하는 Job 이 하나도 없으면</b>
 * 설정값으로 정리 Job({@link DataTableFlashJob})을 만들고 예약한다. 하나라도 있으면(멈춤·사용 안 함 포함) 손대지 않는다 —
 * 보관 일수·주기는 화면에서 그 Job 을 고쳐 바꾼다(재기동 없음).
 *
 * <pre>
 * tc.schedule.default-cleanup.enabled  : TC_SCHEDULE_DEFAULT_CLEANUP (기본 true)
 * tc.schedule.default-cleanup.cron     : 만들 Job 의 주기(Quartz cron)
 * tc.schedule.default-cleanup.days.*   : 표마다 보관 일수
 * </pre>
 * 지운 Job 은 다음 기동 때 다시 만들어진다 — 정리를 멈추려면 지우지 말고 사용을 끈다.
 * 다중화에서 두 서버가 함께 기동하면 한쪽은 같은 이름(409)으로 실패하고 넘어간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupJobBootstrap implements ApplicationRunner {

	private final ScheduleJobService scheduleJobService;

	@Value("${tc.schedule.default-cleanup.enabled:true}")
	private boolean enabled;

	@Value("${tc.schedule.default-cleanup.cron:0 10 4 * * ?}")
	private String cron;

	@Value("${tc.schedule.default-cleanup.days.exchanged-message-audit:90}")
	private int auditDays;

	@Value("${tc.schedule.default-cleanup.days.plc-connection-hist:90}")
	private int plcConnectionHistDays;

	@Value("${tc.schedule.default-cleanup.days.schedule-job-exec-hist:30}")
	private int execHistDays;

	@Override
	public void run(ApplicationArguments args) {
		if (!enabled) {
			return;
		}
		Map<CleanupTarget, Integer> days = Map.of(
			CleanupTarget.EXCHANGED_MESSAGE_AUDIT, auditDays,
			CleanupTarget.PLC_CONNECTION_HIST, plcConnectionHistDays,
			CleanupTarget.SCHEDULE_JOB_EXEC_HIST, execHistDays);
		List<ScheduleJobInfo> jobs = scheduleJobService.searchScheduleJobs();
		for (CleanupTarget target : CleanupTarget.values()) {
			if (jobs.stream().noneMatch(job -> cleans(job, target))) {
				create(target, days.get(target));
			}
		}
	}

	static boolean cleans(ScheduleJobInfo job, CleanupTarget target) {
		return job.getJobClassName() != null && job.getJobClassName().equals(DataTableFlashJob.class.getName())
			&& job.getJobDetails() != null && job.getJobDetails().stream().anyMatch(d ->
				DataTableFlashJob.CLEANUP_TARGET.equals(d.getEntryKey()) && target.name().equals(d.getEntryValue()));
	}

	private void create(CleanupTarget target, int savingDays) {
		ScheduleJobInfo job = new ScheduleJobInfo();
		job.setName("cleanup-" + target.name().toLowerCase().replace('_', '-'));
		job.setJobClassName(DataTableFlashJob.class.getName());
		job.setUse(true);
		job.setTriggerType(ScheduleConstants.TRIGGER_TYPE_CRON);
		job.setCronExpression(cron);
		job.setDescription("기본 정리 Job — " + target.table() + " 를 " + savingDays + "일 보관");
		job.addJobDetail(detail(DataTableFlashJob.CLEANUP_TARGET, target.name(), ScheduleConstants.ENTRY_VAULE_TYPE_STRING, "정리할 표"));
		job.addJobDetail(detail(DataTableFlashJob.SAVING_DAYS, String.valueOf(savingDays), ScheduleConstants.ENTRY_VAULE_TYPE_INT, "보관 일수"));
		try {
			scheduleJobService.registerScheduleJob(job);
			scheduleJobService.schedule(job.getName());
			log.info("기본 정리 Job 을 만들었습니다: {} ({}일 보관, {})", job.getName(), savingDays, cron);
		} catch (Exception e) {
			log.warn("기본 정리 Job 을 만들지 못했습니다: {} — {}", job.getName(), e.getMessage());
		}
	}

	private static ScheduleJobDetailInfo detail(String key, String value, String type, String description) {
		ScheduleJobDetailInfo d = new ScheduleJobDetailInfo();
		d.setEntryKey(key);
		d.setEntryValue(value);
		d.setEntryValueType(type);
		d.setDescription(description);
		return d;
	}
}
