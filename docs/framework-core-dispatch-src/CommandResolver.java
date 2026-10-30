package com.wow.be.framework.dispatch;

import java.util.Optional;

import org.springframework.core.Ordered;

public interface CommandResolver extends Ordered {
	Optional<MessageCommand<?, ?>> resolve(RequestMessage request);
}
