package com.wow.be.framework.dispatch;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 디스패치 설정 ({@code app.dispatch.*}).
 *
 * <pre>
 * app:
 *   dispatch:
 *     commands:                      # 선택. @Command 를 붙일 수 없는 커맨드를 opcode 에 연결한다
 *       - opcode: some.opcode
 *         command: someCommandBean   # 빈 이름 또는 클래스 FQN
 *     executor:
 *       core-size: 4
 *       max-size: 16
 *       queue-capacity: 200
 *       thread-name-prefix: dispatch-
 *       await-termination: 30s
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "app.dispatch")
public class DispatchProperties {

	/** opcode → 커맨드 매핑. {@link ConfiguredCommandResolver} 가 읽는다. */
	private List<CommandMapping> commands = new ArrayList<>();

	/** 비동기 디스패치 스레드 풀 ({@code dispatchTaskExecutor}). */
	private ExecutorProperties executor = new ExecutorProperties();

	@Data
	public static class CommandMapping {

		/** 처리할 opcode */
		private String opcode;

		/** 커맨드 빈 이름 또는 클래스 FQN. 클래스가 빈이 아니면 DI 된 인스턴스를 만들어 쓴다. */
		private String command;
	}

	@Data
	public static class ExecutorProperties {

		private int coreSize = 4;

		private int maxSize = 16;

		/** 대기 큐 크기. 가득 차면 새 요청은 ERR_503_DISPATCH_REJECTED 로 거절된다. */
		private int queueCapacity = 200;

		private String threadNamePrefix = "dispatch-";

		/** 종료할 때 진행 중인 작업을 기다리는 최대 시간. 0 이면 기다리지 않는다. */
		private Duration awaitTermination = Duration.ofSeconds(30);
	}
}
