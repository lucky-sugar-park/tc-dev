package com.wow.be.tc;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 매개변수 이름을 클래스 파일에서 읽지 않아도 뜨는지 확인한다.
 * <p>
 * Spring 6.1 부터 이름 없는 {@code @PathVariable}·{@code @RequestParam}·{@code @Param} 은 {@code -parameters} 로
 * 컴파일해야만 동작한다. Gradle 은 그 옵션을 주지만 Eclipse 는 기본으로 주지 않아, Eclipse 에서 직접 띄우면
 * 기동이 실패한다. 이름을 모두 적어 두면 어느 쪽으로 컴파일해도 같다.
 */
class ParameterNamesTest {

	private static final Pattern NAMED_QUERY_PARAM = Pattern.compile("(?<![:\\w]):[A-Za-z_]\\w*");

	@Test
	void everyNameDependentParameterIsNamed() throws Exception {
		List<String> problems = new ArrayList<>();
		for (Class<?> type : tcClasses()) {
			for (Method method : type.getDeclaredMethods()) {
				checkWebParameters(method, problems);
				checkQueryParameters(method, problems);
			}
		}
		assertThat(problems).as("이름 없는 매개변수 — 애노테이션에 이름을 적는다").isEmpty();
	}

	private static void checkWebParameters(Method method, List<String> problems) {
		for (Parameter p : method.getParameters()) {
			PathVariable path = p.getAnnotation(PathVariable.class);
			if (path != null && path.value().isEmpty() && path.name().isEmpty() && !Map.class.isAssignableFrom(p.getType())) {
				problems.add(where(method, p) + " @PathVariable");
			}
			RequestParam param = p.getAnnotation(RequestParam.class);
			if (param != null && param.value().isEmpty() && param.name().isEmpty() && !Map.class.isAssignableFrom(p.getType())) {
				problems.add(where(method, p) + " @RequestParam");
			}
			RequestHeader header = p.getAnnotation(RequestHeader.class);
			if (header != null && header.value().isEmpty() && header.name().isEmpty() && !Map.class.isAssignableFrom(p.getType())) {
				problems.add(where(method, p) + " @RequestHeader");
			}
		}
	}

	/** {@code @Param} 은 전부 붙이거나 하나도 안 붙인다. {@code :이름} 을 쓰는 {@code @Query} 면 전부 붙인다 */
	private static void checkQueryParameters(Method method, List<String> problems) {
		List<Parameter> bindable = new ArrayList<>();
		for (Parameter p : method.getParameters()) {
			if (!Pageable.class.isAssignableFrom(p.getType()) && !Sort.class.isAssignableFrom(p.getType())) {
				bindable.add(p);
			}
		}
		long named = bindable.stream().filter(p -> p.isAnnotationPresent(Param.class)).count();
		Query query = method.getAnnotation(Query.class);
		boolean needsNames = named > 0 || (query != null && NAMED_QUERY_PARAM.matcher(query.value()).find());
		if (!needsNames) {
			return;
		}
		for (Parameter p : bindable) {
			if (!p.isAnnotationPresent(Param.class)) {
				problems.add(where(method, p) + " @Param");
			}
		}
	}

	private static String where(Method method, Parameter p) {
		return method.getDeclaringClass().getSimpleName() + "." + method.getName() + "(" + p.getType().getSimpleName() + ")";
	}

	private static List<Class<?>> tcClasses() throws ClassNotFoundException {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
			@Override
			protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
				return true; // 인터페이스(저장소)도 본다
			}
		};
		TypeFilter all = (reader, factory) -> true;
		scanner.addIncludeFilter(all);
		List<Class<?>> classes = new ArrayList<>();
		ClassLoader loader = ParameterNamesTest.class.getClassLoader();
		for (BeanDefinition definition : scanner.findCandidateComponents("com.wow.be.tc")) {
			classes.add(Class.forName(definition.getBeanClassName(), false, loader));
		}
		return classes;
	}
}
