package com.wow.be.tc.dispatch;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.wow.be.framework.dispatch.CommandResolver;
import com.wow.be.framework.dispatch.MessageCommand;
import com.wow.be.framework.dispatch.RequestMessage;
import com.wow.be.tc.LegacyClassNames;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.InterfaceService;

import lombok.extern.slf4j.Slf4j;

/**
 * 인터페이스 DB 에서 opcode 에 맞는 커맨드를 찾는다 (framework 디스패처의 tc 확장 지점, 순서 50).
 *
 * <ul>
 *   <li>opcode 와 같은 이름의 인터페이스가 없으면 {@code Optional.empty()} — 다음 리졸버로 넘어가고, 아무도 못 찾으면
 *       디스패처가 {@code ERR_404_DISPATCH_COMMAND} 를 낸다.</li>
 *   <li>인터페이스에 커맨드 클래스(CMD_CLASS_NAME)가 있고 범용 커맨드 사용(USE_GENERIC_CMD_CLASS)이 꺼져 있으면 그 클래스.
 *       그 타입의 빈이 있으면 빈을, 없으면 DI 된 인스턴스를 만들어 클래스별로 한 번만 만든다.</li>
 *   <li>그 밖에는 {@link PlcInterfaceCommand}. 커맨드 클래스가 비어 있으면 범용 커맨드 사용이 꺼져 있어도 기본 커맨드를 쓴다
 *       (레거시는 이 경우 "커맨드 클래스가 지정되지 않은 인터페이스" 오류를 냈다).</li>
 * </ul>
 *
 * <p>레거시 패키지 이름(com.mymes…GenericMessageCommand 등)이 DB 에 남아 있어도 {@link LegacyClassNames} 가 대신 찾는다.
 */
@Slf4j
@Component
public class InterfaceCommandResolver implements CommandResolver {

	public static final int ORDER = 50;

	private final ApplicationContext applicationContext;

	private final InterfaceService interfaceService;

	private final PlcInterfaceCommand defaultCommand;

	private final Map<Class<?>, MessageCommand<?, ?>> customCommands = new ConcurrentHashMap<>();

	public InterfaceCommandResolver(ApplicationContext applicationContext, InterfaceService interfaceService,
			PlcInterfaceCommand defaultCommand) {
		this.applicationContext = applicationContext;
		this.interfaceService = interfaceService;
		this.defaultCommand = defaultCommand;
	}

	@Override
	public Optional<MessageCommand<?, ?>> resolve(RequestMessage request) {
		InterfaceInfo iInfo = interfaceService.findInterfaceByName(request.getOpcode());
		if (iInfo == null) {
			return Optional.empty();
		}
		if (iInfo.isUseGenericCommandClass() || !StringUtils.hasText(iInfo.getCommandClassName())) {
			return Optional.of(defaultCommand);
		}
		return Optional.of(customCommand(iInfo));
	}

	private MessageCommand<?, ?> customCommand(InterfaceInfo iInfo) {
		Class<?> commandClass;
		try {
			commandClass = LegacyClassNames.forName(iInfo.getCommandClassName().trim());
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("인터페이스의 커맨드 클래스를 찾을 수 없습니다: "
				+ iInfo.getCommandClassName() + " (" + iInfo.getName() + ")", e);
		}
		if (commandClass == PlcInterfaceCommand.class) {
			return defaultCommand;
		}
		if (!MessageCommand.class.isAssignableFrom(commandClass)) {
			throw new IllegalStateException("인터페이스의 커맨드 클래스가 MessageCommand 가 아닙니다: "
				+ commandClass.getName() + " (" + iInfo.getName() + ")");
		}
		return customCommands.computeIfAbsent(commandClass, this::instantiate);
	}

	private MessageCommand<?, ?> instantiate(Class<?> commandClass) {
		// 그 타입의 빈이 하나면 그 빈, 없으면(또는 여럿이면) DI 된 인스턴스를 새로 만든다
		Object command = applicationContext.getBeanProvider(commandClass).getIfUnique();
		if (command == null) {
			command = applicationContext.getAutowireCapableBeanFactory().createBean(commandClass);
		}
		log.info("인터페이스 커맨드를 준비했습니다: {}", commandClass.getName());
		return (MessageCommand<?, ?>) command;
	}

	@Override
	public int getOrder() {
		return ORDER;
	}
}
