package com.wow.be.framework.dispatch;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/**
 * 이 클래스가 처리하는 opcode 를 선언한다. {@code @Component} 를 메타 어노테이션으로 가지므로
 * 컴포넌트 스캔 범위 안에 있으면 따로 {@code @Component} 를 붙이지 않아도 빈이 된다.
 *
 * <p>붙인 클래스는 {@link MessageCommand} 를 구현해야 한다. {@link AnnotatedCommandResolver} 가 기동할 때 모은다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Command {

	/** 처리할 opcode 목록. 비어 있으면 기동 실패. */
	String[] opcodes();
}
