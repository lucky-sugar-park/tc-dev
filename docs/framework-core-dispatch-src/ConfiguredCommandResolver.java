package com.wow.be.framework.dispatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 설정({@code app.dispatch.commands})에서 커맨드를 찾는다. {@code @Command} 를 붙일 수 없는 커맨드
 * (다른 jar 의 클래스 등)나, 설치처마다 커맨드를 바꿔 끼울 때 쓴다.
 *
 * <pre>
 * app:
 *   dispatch:
 *     commands:
 *       - opcode: some.opcode
 *         command: someCommandBean          # 빈 이름
 *       - opcode: other.opcode
 *         command: com.acme.OtherCommand    # 클래스 FQN
 * </pre>
 *
 * <p>{@code command} 해석 규칙
 * <ol>
 *   <li>그 이름의 빈이 있으면 그 빈</li>
 *   <li>아니면 클래스 FQN 으로 보고, 그 타입의 빈이 하나 있으면 그 빈 (둘 이상이면 기동 실패)</li>
 *   <li>그 타입의 빈이 없으면 {@link AutowireCapableBeanFactory#createBean(Class)} 로 DI·초기화된 인스턴스를 만든다.
 *       이 인스턴스는 컨텍스트가 닫힐 때 이 리졸버가 정리한다.</li>
 * </ol>
 *
 * <p>모든 매핑은 기동할 때 한 번 해석한다. 클래스를 찾을 수 없거나, MessageCommand 가 아니거나, 같은 opcode 가 두 번
 * 나오면 기동을 멈춘다.
 *
 * <p>순서는 {@link #ORDER}(100) — {@link AnnotatedCommandResolver}(200)보다 먼저다. 같은 opcode 가 {@code @Command} 에도
 * 있으면 설정이 이긴다.
 */
@Slf4j
@Component
public class ConfiguredCommandResolver implements CommandResolver, SmartInitializingSingleton, DisposableBean {

	public static final int ORDER = 100;

	private final ApplicationContext applicationContext;

	private final DispatchProperties properties;

	private volatile Map<String, MessageCommand<?, ?>> commands = Map.of();

	/** createBean 으로 직접 만든 인스턴스 — 컨텍스트가 관리하지 않으므로 destroy 에서 정리한다 */
	private final List<Object> createdInstances = new ArrayList<>();

	public ConfiguredCommandResolver(ApplicationContext applicationContext, DispatchProperties properties) {
		this.applicationContext = applicationContext;
		this.properties = properties;
	}

	@Override
	public void afterSingletonsInstantiated() {
		// 1) 설정 자체를 먼저 검증한다 — 커맨드 인스턴스를 하나도 만들기 전에 설정 오류를 알린다
		Map<String, String> summary = new LinkedHashMap<>();
		for (DispatchProperties.CommandMapping mapping : properties.getCommands()) {
			String opcode = mapping.getOpcode();
			String commandName = mapping.getCommand();
			if (!StringUtils.hasText(opcode) || !StringUtils.hasText(commandName)) {
				throw new BeanInitializationException(String.format(
					"[%s] app.dispatch.commands 에 opcode 나 command 가 비어 있는 항목이 있습니다: opcode=%s, command=%s",
					DispatchException.EXECUTION_FAILED, opcode, commandName));
			}
			if (summary.putIfAbsent(opcode, commandName.trim()) != null) {
				throw new BeanInitializationException(String.format(
					"[%s] app.dispatch.commands 에 같은 opcode 가 두 번 있습니다: %s",
					DispatchException.DUPLICATE_OPCODE, opcode));
			}
		}

		// 2) 커맨드를 찾거나 만든다
		Map<String, MessageCommand<?, ?>> found = new HashMap<>();
		summary.forEach((opcode, commandName) -> found.put(opcode, toCommand(opcode, commandName)));

		this.commands = Map.copyOf(found);
		if (!found.isEmpty()) {
			log.info("설정 커맨드 {}개를 등록했습니다: {}", found.size(), summary);
		}
	}

	private MessageCommand<?, ?> toCommand(String opcode, String commandName) {
		Object instance;
		if (applicationContext.containsBean(commandName)) {
			instance = applicationContext.getBean(commandName);
		} else {
			Class<?> commandClass = loadClass(opcode, commandName);
			String[] beanNames = applicationContext.getBeanNamesForType(commandClass);
			if (beanNames.length > 1) {
				throw new BeanInitializationException(String.format(
					"[%s] 커맨드 타입의 빈이 둘 이상이라 하나를 고를 수 없습니다. 빈 이름으로 지정해 주세요: opcode=%s, %s → %s",
					DispatchException.EXECUTION_FAILED, opcode, commandName, String.join(", ", beanNames)));
			}
			if (beanNames.length == 1) {
				instance = applicationContext.getBean(beanNames[0]);
			} else {
				instance = applicationContext.getAutowireCapableBeanFactory().createBean(commandClass);
				synchronized (createdInstances) {
					createdInstances.add(instance);
				}
			}
		}

		if (!(instance instanceof MessageCommand<?, ?> command)) {
			throw new BeanInitializationException(String.format(
				"[%s] 설정한 커맨드가 MessageCommand 를 구현하지 않습니다: opcode=%s, %s (%s)",
				DispatchException.EXECUTION_FAILED, opcode, commandName, instance.getClass().getName()));
		}
		return command;
	}

	private Class<?> loadClass(String opcode, String className) {
		try {
			return ClassUtils.forName(className, applicationContext.getClassLoader());
		} catch (ClassNotFoundException | LinkageError e) {
			throw new BeanInitializationException(String.format(
				"[%s] 설정한 커맨드가 빈 이름도 아니고 클래스도 찾을 수 없습니다: opcode=%s, %s",
				DispatchException.EXECUTION_FAILED, opcode, className), e);
		}
	}

	@Override
	public Optional<MessageCommand<?, ?>> resolve(RequestMessage request) {
		return Optional.ofNullable(commands.get(request.getOpcode()));
	}

	@Override
	public int getOrder() {
		return ORDER;
	}

	@Override
	public void destroy() {
		AutowireCapableBeanFactory beanFactory = applicationContext.getAutowireCapableBeanFactory();
		synchronized (createdInstances) {
			for (Object instance : createdInstances) {
				try {
					beanFactory.destroyBean(instance);
				} catch (RuntimeException e) {
					log.warn("설정 커맨드 정리 중 오류가 났습니다: {}", instance.getClass().getName(), e);
				}
			}
			createdInstances.clear();
		}
	}
}
