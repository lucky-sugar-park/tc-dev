package com.wow.be.tc.schedule.jobs;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.schedule.CleanupTarget;
import com.wow.be.tc.schedule.ScheduleJob;
import com.wow.be.tc.schedule.impl.DataCleaner;

/**
 * 오래된 행을 지우는 정리 Job.
 *
 * <ul>
 *   <li>속성 {@code cleanupTarget}(정리할 표, {@link CleanupTarget})과 {@code savingDays}(보관 일수, 1 이상)만 쓴다.</li>
 *   <li>비고는 지운 건수와 기준 시각이다. 실패하면 예외가 그대로 올라가 실행 이력이 FAIL 로 남는다.</li>
 *   <li>같은 Job 은 클러스터 전체에서 한 번에 하나만 돈다({@code @DisallowConcurrentExecution} 은 Job 단위 — 같은 표를 지우는
 *       Job 을 둘 만들면 함께 돌 수 있지만 지우는 조건이 같아 결과는 같다).</li>
 * </ul>
 * 클래스 이름은 DB(SCHEDULE_JOB.JOB_CLASS_NAME)에 저장되어 있어 바꾸지 않는다.
 */
@DisallowConcurrentExecution
public class DataTableFlashJob extends ScheduleJob {

	public static final String CLEANUP_TARGET = "cleanupTarget";

	public static final String SAVING_DAYS = "savingDays";

	@Autowired
	private DataCleaner dataCleaner;

	@Override
	protected String executeJob(JobExecutionContext context) throws ToolControlException {
		JobDataMap properties = context.getMergedJobDataMap();
		CleanupTarget target = targetOf(properties.getString(CLEANUP_TARGET));
		int savingDays = savingDaysOf(properties.getString(SAVING_DAYS));
		Date before = new Date(System.currentTimeMillis() - savingDays * 24L * 60 * 60 * 1000);
		long deleted = dataCleaner.deleteOlderThan(target, before);
		return target.table() + ": " + savingDays + "일 지난 " + deleted + "건 삭제 (기준 "
			+ new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(before) + ")";
	}

	public static CleanupTarget targetOf(String value) throws ToolControlException {
		if (value == null || value.isBlank()) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "속성 cleanupTarget 이 없습니다");
		}
		try {
			return CleanupTarget.valueOf(value.trim());
		} catch (IllegalArgumentException e) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "정리할 수 없는 표입니다: " + value);
		}
	}

	public static int savingDaysOf(String value) throws ToolControlException {
		try {
			int days = Integer.parseInt(value == null ? "" : value.trim());
			if (days >= 1) return days;
		} catch (NumberFormatException e) {
			// 아래에서 같은 오류로
		}
		throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "속성 savingDays 는 1 이상 정수여야 합니다: " + value);
	}
}
