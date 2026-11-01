package com.wow.be.tc.schedule.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.InterfaceService;
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.schedule.ScheduleJobDetailInfo;
import com.wow.be.tc.schedule.ScheduleJobInfo;
import com.wow.be.tc.schedule.ScheduleJobKind;

/** 저장 전 검사 — 저장해 놓고 실행할 때마다 FAIL 로 남을 Job 을 막는다 */
class ScheduleJobValidatorTest {

	private final InterfaceService interfaces = mock(InterfaceService.class);

	private final ScheduleJobValidator validator = new ScheduleJobValidator(interfaces);

	ScheduleJobValidatorTest() {
		when(interfaces.findInterfaceByName("if.read")).thenReturn(iface("if.read", InterfaceType.READ, "d100", "d101"));
		when(interfaces.findInterfaceByName("if.write")).thenReturn(iface("if.write", InterfaceType.WRITE, "d100", "d101"));
	}

	private static InterfaceInfo iface(String name, InterfaceType type, String... points) {
		InterfaceInfo i = new InterfaceInfo();
		i.setName(name);
		i.setInterfaceType(type);
		List<PointInfo> ps = new ArrayList<>();
		for (String p : points) {
			PointInfo pi = new PointInfo();
			pi.setName(p);
			ps.add(pi);
		}
		i.setPoints(ps);
		return i;
	}

	private static ScheduleJobDetailInfo prop(String key, String value, String type) {
		ScheduleJobDetailInfo d = new ScheduleJobDetailInfo();
		d.setEntryKey(key);
		d.setEntryValue(value);
		d.setEntryValueType(type);
		return d;
	}

	private static ScheduleJobInfo job(String className, ScheduleJobDetailInfo... props) {
		ScheduleJobInfo j = new ScheduleJobInfo();
		j.setName("job-1");
		j.setJobClassName(className);
		j.setTriggerType("cron");
		j.setCronExpression("0 0/1 * * * ?");
		j.setJobDetails(new ArrayList<>(List.of(props)));
		return j;
	}

	private static final String PLC = "com.wow.be.tc.schedule.jobs.PlcGeneralScheduleJob";

	private static final String CLEANUP = "com.wow.be.tc.schedule.jobs.DataTableFlashJob";

	private void rejects(ScheduleJobInfo j, String messagePart) {
		assertThatThrownBy(() -> validator.validate(j)).isInstanceOf(ToolControlException.class).hasMessageContaining(messagePart);
	}

	@Test
	void 등록할_수_없는_클래스는_거절() {
		rejects(job("com.example.EvilJob", prop("x", "1", "string")), "등록할 수 없는 Job 종류");
	}

	@Test
	void 주기_검사() {
		ScheduleJobInfo j = job(PLC, prop("interfaceName", "if.read", "string"));
		j.setCronExpression("매분");
		rejects(j, "cron");
		j.setTriggerType("simple");
		j.setRepeatInterval(500L);
		rejects(j, "1000ms");
	}

	@Test
	void PLC_READ_는_interfaceName_만() throws Exception {
		assertThat(validator.validate(job(PLC, prop("interfaceName", "if.read", "STRING")))).isEqualTo(ScheduleJobKind.PLC);
		rejects(job(PLC, prop("interfaceName", "if.read", "string"), prop("d100", "1", "int")), "READ 인터페이스");
		rejects(job(PLC, prop("interfaceName", "no.such", "string")), "인터페이스가 없습니다");
		rejects(job(PLC), "필수 속성이 없습니다: interfaceName");
	}

	@Test
	void PLC_WRITE_는_점마다_값() throws Exception {
		validator.validate(job(PLC, prop("interfaceName", "if.write", "string"), prop("d100", "1", "int"), prop("d101", "-2", "int")));
		rejects(job(PLC, prop("interfaceName", "if.write", "string"), prop("d100", "1", "int")), "쓸 값이 없는 점: [d101]");
		rejects(job(PLC, prop("interfaceName", "if.write", "string"), prop("d100", "1", "int"), prop("d101", "2", "int"),
			prop("d999", "3", "int")), "없는 점입니다: [d999]");
	}

	@Test
	void 정리_Job() throws Exception {
		assertThat(validator.validate(job(CLEANUP, prop("cleanupTarget", "SCHEDULE_JOB_EXEC_HIST", "string"), prop("savingDays", "7", "int"))))
			.isEqualTo(ScheduleJobKind.CLEANUP);
		rejects(job(CLEANUP, prop("cleanupTarget", "USERS", "string"), prop("savingDays", "7", "int")), "정리할 수 없는 표");
		rejects(job(CLEANUP, prop("cleanupTarget", "EXCHANGED_MESSAGE_AUDIT", "string"), prop("savingDays", "0", "int")), "1 이상");
		rejects(job(CLEANUP, prop("cleanupTarget", "EXCHANGED_MESSAGE_AUDIT", "string"), prop("savingDays", "7", "int"),
			prop("methodName", "x", "string")), "[methodName]");
	}

	@Test
	void 속성_키와_값_형() throws Exception {
		rejects(job(PLC, prop("interfaceName", "if.read", "string"), prop("interfaceName", "if.read", "string")), "겹칩니다");
		rejects(job(PLC, prop(" ", "x", "string")), "키가 비었습니다");
		rejects(job(PLC, prop("interfaceName", "if.read", "ref")), "string·int·long");
		rejects(job(PLC, prop("interfaceName", "if.write", "string"), prop("d100", "1.5", "int"), prop("d101", "2", "int")), "int 이 아닙니다");

		ScheduleJobInfo j = job(PLC, prop("interfaceName", "if.read", null));
		validator.validate(j);
		assertThat(j.getJobDetails().get(0).getEntryValueType()).isEqualTo("string");
		assertThat(j.getStartDelay()).isZero();
	}

	@Test
	void 옛_패키지_이름은_새_클래스_이름으로() throws Exception {
		ScheduleJobInfo j = job("com.mymes.equip.tc.schedule.jobs.PlcGeneralScheduleJob", prop("interfaceName", "if.read", "string"));
		validator.validate(j);
		assertThat(j.getJobClassName()).isEqualTo(PLC);
	}
}
