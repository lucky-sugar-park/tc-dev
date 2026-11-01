package com.wow.be.tc.schedule;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.quartz.Job;

import com.wow.be.tc.LegacyClassNames;
import com.wow.be.tc.schedule.jobs.DataTableFlashJob;
import com.wow.be.tc.schedule.jobs.PlcGeneralScheduleJob;

/**
 * 등록할 수 있는 스케줄 Job 종류 — Job 클래스와 그 Job 이 읽는 속성(SCHEDULE_JOB_DETAIL)의 정의. 이 밖의 클래스는 등록하지 않는다
 * (화면에서 클래스 이름을 받지 않는다). 화면은 {@code GET /api/v1/schedule-job/management/job-kinds} 로 받아 속성 표의 틀로 쓴다.
 */
public enum ScheduleJobKind {

	/** 주기마다 인터페이스 하나로 PLC 요청 — 나머지 속성은 요청 data(WRITE 면 점 이름마다 쓸 값) */
	PLC(PlcGeneralScheduleJob.class, true, List.of(
		new PropertySpec(PlcGeneralScheduleJob.INTERFACE_NAME, ScheduleConstants.ENTRY_VAULE_TYPE_STRING, true, null, List.of(),
			"주기마다 실행할 인터페이스(opcode). WRITE 인터페이스면 점 이름마다 쓸 값을 속성으로 더한다"),
		new PropertySpec(PlcGeneralScheduleJob.AUDIT_MODE, ScheduleConstants.ENTRY_VAULE_TYPE_STRING, false,
			PlcGeneralScheduleJob.AUDIT_ALL, List.of(PlcGeneralScheduleJob.AUDIT_ALL, PlcGeneralScheduleJob.AUDIT_FAILURE_ONLY),
			"송수신 기록 — ALL(모두 남김), FAILURE_ONLY(실패만 남김 — 짧은 주기 수집의 기록을 크게 줄인다). 없으면 ALL"))),

	/** 오래된 행 정리 */
	CLEANUP(DataTableFlashJob.class, false, List.of(
		new PropertySpec(DataTableFlashJob.CLEANUP_TARGET, ScheduleConstants.ENTRY_VAULE_TYPE_STRING, true,
			CleanupTarget.EXCHANGED_MESSAGE_AUDIT.name(), Arrays.stream(CleanupTarget.values()).map(Enum::name).toList(), "정리할 표"),
		new PropertySpec(DataTableFlashJob.SAVING_DAYS, ScheduleConstants.ENTRY_VAULE_TYPE_INT, true, "30", List.of(),
			"보관 일수 — 이보다 오래된 행을 지운다")));

	/**
	 * 속성 하나의 정의.
	 *
	 * @param options 고를 수 있는 값(비면 자유 입력)
	 */
	public record PropertySpec(String key, String valueType, boolean required, String defaultValue, List<String> options,
			String description) {
	}

	/** 화면에 보내는 모양 */
	public record Info(String kind, String jobClassName, boolean extraProperties, List<PropertySpec> properties) {
	}

	private final Class<? extends Job> jobClass;

	private final boolean extraProperties;

	private final List<PropertySpec> properties;

	ScheduleJobKind(Class<? extends Job> jobClass, boolean extraProperties, List<PropertySpec> properties) {
		this.jobClass = jobClass;
		this.extraProperties = extraProperties;
		this.properties = properties;
	}

	public Class<? extends Job> jobClass() {
		return jobClass;
	}

	/** 정의된 속성 말고도 속성을 더 둘 수 있는가 */
	public boolean extraProperties() {
		return extraProperties;
	}

	public List<PropertySpec> properties() {
		return properties;
	}

	public Info info() {
		return new Info(name(), jobClass.getName(), extraProperties, properties);
	}

	/** 저장된 클래스 이름(옛 패키지 이름 포함)의 종류 */
	public static Optional<ScheduleJobKind> ofClassName(String className) {
		if (className == null || className.isBlank()) return Optional.empty();
		String resolved = LegacyClassNames.resolve(className.trim());
		return Arrays.stream(values()).filter(k -> k.jobClass.getName().equals(resolved)).findFirst();
	}
}
