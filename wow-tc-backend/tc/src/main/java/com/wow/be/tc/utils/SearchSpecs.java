package com.wow.be.tc.utils;

import java.util.Objects;

import org.springframework.data.jpa.domain.Specification;

import com.wow.be.framework.Condition;
import com.wow.be.framework.Condition.ConjType;
import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.framework.persist.SearchSpec;

/**
 * framework {@link SearchSpec#createSpecification} 을 안전하게 부른다.
 *
 * <ul>
 *   <li>조건 항목의 conjType(AND/OR)이 비어 있으면 AND 로 채운다. framework 0.8.45(2026-10-30 재배포본)는 비어 있는
 *       conjType 에서 NPE 가 난다 — 화면이 첫 조건의 conjType 을 보내지 않는 경우가 많다.</li>
 *   <li>결과가 null 이면 "조건 없음"으로 바꾼다. Spring Data JPA 4 는 null Specification 을 받지 않는다.</li>
 * </ul>
 */
public final class SearchSpecs {

	private SearchSpecs() {
	}

	@SuppressWarnings("rawtypes")
	public static <T extends PersistentEntity> Specification<T> create(SearchSpec<T> specs, Condition cond) {
		if (cond == null) {
			return Specification.unrestricted();
		}
		if (cond.getConditions() != null) {
			cond.getConditions().stream()
				.filter(item -> item.getConjType() == null)
				.forEach(item -> item.setConjType(ConjType.AND));
		}
		return Objects.requireNonNullElse(specs.createSpecification(cond), Specification.unrestricted());
	}
}
