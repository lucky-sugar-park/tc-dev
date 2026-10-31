package com.wow.be.tc.dispatch;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.wow.be.framework.dispatch.CommandResolver;
import com.wow.be.framework.dispatch.MessageCommand;
import com.wow.be.framework.dispatch.RequestMessage;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.InterfaceService;

/**
 * 인터페이스 DB 에서 opcode 에 맞는 커맨드를 찾는다 (framework 디스패처의 tc 확장 지점, 순서 50).
 *
 * <ul>
 *   <li>opcode 와 같은 이름의 인터페이스가 없으면 {@code Optional.empty()} — 다음 리졸버로 넘어가고, 아무도 못 찾으면
 *       디스패처가 {@code ERR_404_DISPATCH_COMMAND} 를 낸다.</li>
 *   <li>있으면 {@link PlcInterfaceCommand}.</li>
 * </ul>
 */
@Component
public class InterfaceCommandResolver implements CommandResolver {

	public static final int ORDER = 50;

	private final InterfaceService interfaceService;

	private final PlcInterfaceCommand defaultCommand;

	public InterfaceCommandResolver(InterfaceService interfaceService, PlcInterfaceCommand defaultCommand) {
		this.interfaceService = interfaceService;
		this.defaultCommand = defaultCommand;
	}

	@Override
	public Optional<MessageCommand<?, ?>> resolve(RequestMessage request) {
		InterfaceInfo iInfo = interfaceService.findInterfaceByName(request.getOpcode());
		if (iInfo == null) {
			return Optional.empty();
		}
		return Optional.of(defaultCommand);
	}

	@Override
	public int getOrder() {
		return ORDER;
	}
}
