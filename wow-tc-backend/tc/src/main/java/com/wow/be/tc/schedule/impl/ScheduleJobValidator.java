package com.wow.be.tc.schedule.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.quartz.CronExpression;
import org.springframework.stereotype.Component;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.InterfaceService;
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.schedule.ScheduleConstants;
import com.wow.be.tc.schedule.ScheduleJobDetailInfo;
import com.wow.be.tc.schedule.ScheduleJobInfo;
import com.wow.be.tc.schedule.ScheduleJobKind;
import com.wow.be.tc.schedule.jobs.DataTableFlashJob;
import com.wow.be.tc.schedule.jobs.PlcGeneralScheduleJob;

import lombok.RequiredArgsConstructor;

/**
 * 스케줄 Job 을 저장하기 전에 다듬고 검사한다 — 틀리면 {@code ERR_400}. 저장해 놓고 실행할 때마다 FAIL 로 남는 것을 막는다.
 *
 * <ul>
 *   <li>Job 클래스는 {@link ScheduleJobKind} 의 것만(임의 클래스를 만들지 않는다).</li>
 *   <li>주기: cron 은 Quartz 식으로 읽히는지, 간격은 1000ms 이상.</li>
 *   <li>속성: 키가 비거나 겹치면 안 된다. 값 형은 string·int·long — JDBC JobStore 가 {@code useProperties=true} 라 JobDataMap 에는
 *       문자열만 들어간다(예전 ref·list·map 은 예약할 때 실패했다). int·long 은 값이 그 형으로 읽혀야 한다.</li>
 *   <li>종류별: 필수 속성, PLC 는 인터페이스가 있고 나머지 속성이 그 점 이름과 맞는지(READ 는 더 둘 수 없고, WRITE 는 점마다 값이 있어야
 *       한다), 정리는 정리할 표와 보관 일수.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ScheduleJobValidator {

	private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_.-]{1,100}$");

	private static final Set<String> VALUE_TYPES = Set.of(ScheduleConstants.ENTRY_VAULE_TYPE_STRING,
		ScheduleConstants.ENTRY_VAULE_TYPE_INT, ScheduleConstants.ENTRY_VAULE_TYPE_LONG);

	private final InterfaceService interfaceService;

	/** 다듬고(값 형 소문자·빈 형은 string·지연 null 은 0) 검사한다. */
	public ScheduleJobKind validate(ScheduleJobInfo job) throws ToolControlException {
		if (job.getName() == null || !NAME.matcher(job.getName()).matches()) {
			throw invalid("이름은 영숫자·_ . - 로 100자까지입니다: " + job.getName());
		}
		ScheduleJobKind kind = ScheduleJobKind.ofClassName(job.getJobClassName())
			.orElseThrow(() -> invalid("등록할 수 없는 Job 종류입니다: " + job.getJobClassName()));
		job.setJobClassName(kind.jobClass().getName());
		validateTrigger(job);

		Map<String, String> properties = new LinkedHashMap<>();
		for (ScheduleJobDetailInfo d : job.getJobDetails() == null ? List.<ScheduleJobDetailInfo>of() : job.getJobDetails()) {
			String key = d.getEntryKey() == null ? "" : d.getEntryKey().trim();
			if (key.isEmpty()) throw invalid("속성 키가 비었습니다");
			if (properties.containsKey(key)) throw invalid("속성 키가 겹칩니다: " + key);
			d.setEntryKey(key);
			String type = d.getEntryValueType() == null || d.getEntryValueType().isBlank()
				? ScheduleConstants.ENTRY_VAULE_TYPE_STRING : d.getEntryValueType().trim().toLowerCase();
			if (!VALUE_TYPES.contains(type)) throw invalid("속성 " + key + " 의 값 형은 string·int·long 중 하나입니다: " + type);
			d.setEntryValueType(type);
			String value = d.getEntryValue() == null ? "" : d.getEntryValue();
			d.setEntryValue(value);
			checkType(key, type, value);
			properties.put(key, value);
		}
		for (ScheduleJobKind.PropertySpec spec : kind.properties()) {
			if (spec.required() && properties.getOrDefault(spec.key(), "").isBlank()) throw invalid("필수 속성이 없습니다: " + spec.key());
		}

		switch (kind) {
		case PLC -> validatePlc(properties);
		case CLEANUP -> validateCleanup(properties);
		}
		return kind;
	}

	private static void validateTrigger(ScheduleJobInfo job) throws ToolControlException {
		if (job.getStartDelay() == null) job.setStartDelay(0L);
		if (job.getStartDelay() < 0) throw invalid("시작 지연은 0 이상입니다");
		if (job.getRepeatInterval() == null) job.setRepeatInterval(0L);
		String type = job.getTriggerType() == null ? "" : job.getTriggerType().trim().toLowerCase();
		if (ScheduleConstants.TRIGGER_TYPE_CRON.equals(type)) {
			if (job.getCronExpression() == null || !CronExpression.isValidExpression(job.getCronExpression().trim())) {
				throw invalid("cron 식이 틀렸습니다: " + job.getCronExpression());
			}
			job.setCronExpression(job.getCronExpression().trim());
		} else if (ScheduleConstants.TRIGGER_TYPE_SIMPLE.equals(type)) {
			if (job.getRepeatInterval() < 1000) throw invalid("반복 간격은 1000ms 이상입니다");
		} else {
			throw invalid("주기 종류는 cron·simple 중 하나입니다: " + job.getTriggerType());
		}
		job.setTriggerType(type);
	}

	private static void checkType(String key, String type, String value) throws ToolControlException {
		try {
			if (ScheduleConstants.ENTRY_VAULE_TYPE_INT.equals(type)) Integer.parseInt(value.trim());
			else if (ScheduleConstants.ENTRY_VAULE_TYPE_LONG.equals(type)) Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			throw invalid("속성 " + key + " 의 값이 " + type + " 이 아닙니다: " + value);
		}
	}

	private void validatePlc(Map<String, String> properties) throws ToolControlException {
		String interfaceName = properties.get(PlcGeneralScheduleJob.INTERFACE_NAME);
		InterfaceInfo iface = interfaceService.findInterfaceByName(interfaceName);
		if (iface == null) throw invalid("인터페이스가 없습니다: " + interfaceName);
		Set<String> points = new LinkedHashSet<>();
		for (PointInfo p : iface.getPoints() == null ? List.<PointInfo>of() : iface.getPoints()) points.add(p.getName());
		Set<String> extra = new LinkedHashSet<>(properties.keySet());
		extra.remove(PlcGeneralScheduleJob.INTERFACE_NAME);
		if (iface.getInterfaceType() == InterfaceType.READ) {
			if (!extra.isEmpty()) throw invalid("READ 인터페이스(" + interfaceName + ")에는 interfaceName 말고 속성을 둘 수 없습니다: " + extra);
			return;
		}
		List<String> unknown = new ArrayList<>(extra);
		unknown.removeAll(points);
		if (!unknown.isEmpty()) throw invalid(interfaceName + " 에 없는 점입니다: " + unknown + " (점: " + points + ")");
		List<String> missing = new ArrayList<>(points);
		missing.removeAll(extra);
		if (!missing.isEmpty()) throw invalid("WRITE 인터페이스(" + interfaceName + ")의 쓸 값이 없는 점: " + missing);
	}

	private static void validateCleanup(Map<String, String> properties) throws ToolControlException {
		DataTableFlashJob.targetOf(properties.get(DataTableFlashJob.CLEANUP_TARGET));
		DataTableFlashJob.savingDaysOf(properties.get(DataTableFlashJob.SAVING_DAYS));
		Set<String> extra = new LinkedHashSet<>(properties.keySet());
		extra.remove(DataTableFlashJob.CLEANUP_TARGET);
		extra.remove(DataTableFlashJob.SAVING_DAYS);
		if (!extra.isEmpty()) throw invalid("정리 Job 에는 cleanupTarget·savingDays 말고 속성을 둘 수 없습니다: " + extra);
	}

	private static ToolControlException invalid(String message) {
		return ToolControlException.of(ToolControlException.INVALID_REQUEST, message);
	}
}
