package com.wow.be.framework.dispatch;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link Command} 가 붙은 빈에서 커맨드를 찾는다.
 *
 * <p>모든 싱글턴 빈이 만들어진 뒤({@link SmartInitializingSingleton}) opcode → 커맨드 맵을 한 번 만들고, 그 뒤로는 바꾸지 않는다.
 * 다음 경우에는 기동을 멈춘다 — 잘못된 매핑으로 운영 중에 엉뚱한 커맨드가 실행되는 것보다 낫다.
 * <ul>
 *   <li>같은 opcode 를 두 빈이 선언함</li>
 *   <li>{@code @Command} 를 붙인 빈이 {@link MessageCommand} 를 구현하지 않음</li>
 *   <li>opcode 가 비어 있음</li>
 * </ul>
 *
 * <p>맵은 기동이 끝난 뒤에 만들어진다. 다른 빈의 {@code @PostConstruct} 처럼 기동 도중에 디스패치하면 이 리졸버는
 * 아무것도 찾지 못한다.
 *
 * <p>순서는 {@link #ORDER}(200). 설정 매핑({@link ConfiguredCommandResolver}, 100)이 먼저다 — 설치처마다 코드 수정 없이
 * yml 로 커맨드를 바꿔 끼울 수 있게 하기 위해서다.
 */
@Slf4j
@Component
public class AnnotatedCommandResolver implements CommandResolver, SmartInitializingSingleton {

	public static final int ORDER = 200;

	private final ApplicationContext applicationContext;

	private volatile Map<String, MessageCommand<?, ?>> commands = Map.of();

	public AnnotatedCommandResolver(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Override
	public void afterSingletonsInstantiated() {
		Map<String, MessageCommand<?, ?>> found = new HashMap<>();
		Map<String, String> ownerBeanNames = new HashMap<>();

		for (String beanName : applicationContext.getBeanNamesForAnnotation(Command.class)) {
			// AOP 프록시여도 원래 클래스의 어노테이션을 읽는다
			Command annotation = applicationContext.findAnnotationOnBean(beanName, Command.class);
			Object bean = applicationContext.getBean(beanName);

			if (!(bean instanceof MessageCommand<?, ?> command)) {
				throw new BeanInitializationException(String.format(
					"[%s] @Command 를 붙인 빈이 MessageCommand 를 구현하지 않습니다: %s (%s)",
					DispatchException.EXECUTION_FAILED, beanName, bean.getClass().getName()));
			}
			if (annotation == null || annotation.opcodes().length == 0) {
				throw new BeanInitializationException(String.format(
					"[%s] @Command 에 opcode 가 없습니다: %s",
					DispatchException.EXECUTION_FAILED, beanName));
			}

			for (String opcode : annotation.opcodes()) {
				if (!StringUtils.hasText(opcode)) {
					throw new BeanInitializationException(String.format(
						"[%s] @Command 에 빈 opcode 가 있습니다: %s",
						DispatchException.EXECUTION_FAILED, beanName));
				}
				String previous = ownerBeanNames.putIfAbsent(opcode, beanName);
				if (previous != null) {
					throw new BeanInitializationException(String.format(
						"[%s] 같은 opcode 를 처리하는 커맨드가 둘 이상입니다: %s (%s, %s)",
						DispatchException.DUPLICATE_OPCODE, opcode, previous, beanName));
				}
				found.put(opcode, command);
			}
		}

		this.commands = Map.copyOf(found);
		log.info("@Command 커맨드 {}개를 등록했습니다: {}", found.size(), ownerBeanNames);
	}

	@Override
	public Optional<MessageCommand<?, ?>> resolve(RequestMessage request) {
		return Optional.ofNullable(commands.get(request.getOpcode()));
	}

	@Override
	public int getOrder() {
		return ORDER;
	}
}
