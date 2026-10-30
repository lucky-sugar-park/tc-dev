package com.wow.be.tc;

import java.util.Map;

import com.wow.be.tc.dispatch.PlcInterfaceCommand;
import com.wow.be.tc.plc.connector.melsec.MelsecProgrammableControllerAdapter;
import com.wow.be.tc.schedule.jobs.DataTableFlashJob;
import com.wow.be.tc.schedule.jobs.PlcConnectorSpyScheduleJob;
import com.wow.be.tc.schedule.jobs.PlcGeneralScheduleJob;

import lombok.extern.slf4j.Slf4j;

/**
 * DB 에 저장된 클래스 이름(PLC 어댑터·커맨드·스케줄 Job)을 로드한다.
 *
 * <p>패키지 이전(레거시 패키지 → {@code com.wow.be.tc}) 전에 저장된 행은 옛 패키지 이름을 그대로 들고 있다.
 * 그 이름으로 클래스를 찾지 못하면, 단순 클래스 이름이 같은 tc 클래스로 대신 찾는다.
 * DB 값을 새 이름으로 고치면(UPDATE) 이 대체 경로는 쓰이지 않는다.
 */
@Slf4j
public final class LegacyClassNames {

	private static final Map<String, Class<?>> KNOWN = Map.of(
		MelsecProgrammableControllerAdapter.class.getSimpleName(), MelsecProgrammableControllerAdapter.class,
		"GenericMessageCommand", PlcInterfaceCommand.class,
		PlcInterfaceCommand.class.getSimpleName(), PlcInterfaceCommand.class,
		DataTableFlashJob.class.getSimpleName(), DataTableFlashJob.class,
		PlcGeneralScheduleJob.class.getSimpleName(), PlcGeneralScheduleJob.class,
		PlcConnectorSpyScheduleJob.class.getSimpleName(), PlcConnectorSpyScheduleJob.class
	);

	private LegacyClassNames() {
	}

	/**
	 * 클래스 이름으로 클래스를 찾는다. 없으면 같은 단순 이름의 tc 클래스를 돌려준다.
	 *
	 * @throws ClassNotFoundException 둘 다 없을 때
	 */
	public static Class<?> forName(String className) throws ClassNotFoundException {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			Class<?> known = className == null ? null : KNOWN.get(className.substring(className.lastIndexOf('.') + 1));
			if (known == null) {
				throw e;
			}
			log.warn("저장된 클래스 이름 {} 를 찾을 수 없어 {} 로 대신 로드합니다 — DB 값을 새 이름으로 바꾸세요.", className, known.getName());
			return known;
		}
	}

	/** {@link #forName} 결과의 이름 — 스프링 빈 이름 조회 등 문자열이 필요한 곳에서 쓴다. 못 찾으면 원래 이름. */
	public static String resolve(String className) {
		try {
			return forName(className).getName();
		} catch (ClassNotFoundException e) {
			return className;
		}
	}
}
