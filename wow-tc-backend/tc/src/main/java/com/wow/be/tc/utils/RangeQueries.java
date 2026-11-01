package com.wow.be.tc.utils;

import java.util.Date;
import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

import com.wow.be.framework.persist.Rangeable;
import com.wow.be.framework.persist.RangeableRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

/**
 * 큰 목록의 구간(Range) 조회 — 화면의 구간 스크롤 표(docs/range-grid-design.md §4).
 *
 * <ul>
 *   <li><b>스냅샷</b>: 첫 요청(asOf 없음)에서 기준을 정해 돌려주고, 이후 요청은 그 기준 안에서만 읽는다 — 스크롤 중에 새 행이 쌓여도
 *       결과가 밀리거나 겹치지 않는다. 증가 키가 있으면 키의 최댓값({@link #byKey}), 없으면 시각({@link #byTime}).</li>
 *   <li><b>건수</b>는 withTotal 일 때만 센다(첫 요청) — 스크롤마다 count(*) 를 다시 세지 않는다. 안 세면 total 은 null.</li>
 *   <li>구간은 framework {@link RangeableRepository}(offset·limit)로 읽는다. limit 1~{@value #MAX_LIMIT}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RangeQueries {

	public static final int MAX_LIMIT = 1000;

	/** 구간 — 검색 결과 안의 절대 위치(0부터)·행 수·스냅샷 기준·건수를 셀지 */
	public record Params(Integer start, Integer limit, Long asOf, Boolean withTotal) {

		public int startOrZero() {
			return start == null || start < 0 ? 0 : start;
		}

		public int limitClamped() {
			return limit == null || limit < 1 ? 100 : Math.min(limit, MAX_LIMIT);
		}
	}

	/** 요청 본문 — 화면 검색 조건과 구간 */
	public record Request<F>(F filter, Params range) {

		public Params rangeOrDefault() {
			return range != null ? range : new Params(0, 100, null, true);
		}
	}

	/** 결과 — total 은 셌을 때만 */
	public record Result<T>(List<T> rows, Long total, long asOf, int start) {
	}

	private final EntityManager entityManager;

	/** 증가 키(숫자) 스냅샷 — 기준은 조건에 맞는 행의 키 최댓값 */
	public <E, R> Result<R> byKey(Class<E> type, Specification<E> spec, String keyAttr, Params p, Sort sort,
			JpaSpecificationExecutor<E> counter, RangeableRepository<E, ?> ranger, Function<E, R> map) {
		long asOf = p.asOf() != null ? p.asOf() : maxOf(type, spec, keyAttr);
		Specification<E> snap = and(spec, (root, q, cb) -> cb.le(root.get(keyAttr), asOf));
		return read(snap, asOf, p, sort, counter, ranger, map);
	}

	/** 시각 스냅샷(밀리초) — 증가 키가 없는 표. 기준은 첫 요청 시각 */
	public <E, R> Result<R> byTime(Specification<E> spec, String timeAttr, Params p, Sort sort,
			JpaSpecificationExecutor<E> counter, RangeableRepository<E, ?> ranger, Function<E, R> map) {
		long asOf = p.asOf() != null ? p.asOf() : System.currentTimeMillis();
		Date until = new Date(asOf);
		Specification<E> snap = and(spec, (root, q, cb) -> cb.lessThanOrEqualTo(root.<Date>get(timeAttr), until));
		return read(snap, asOf, p, sort, counter, ranger, map);
	}

	private <E, R> Result<R> read(Specification<E> snap, long asOf, Params p, Sort sort,
			JpaSpecificationExecutor<E> counter, RangeableRepository<E, ?> ranger, Function<E, R> map) {
		Long total = Boolean.TRUE.equals(p.withTotal()) ? counter.count(snap) : null;
		List<R> rows = ranger.findAllByRange(snap, new Rangeable(p.startOrZero(), p.limitClamped(), sort)).stream().map(map).toList();
		return new Result<>(rows, total, asOf, p.startOrZero());
	}

	private <E> long maxOf(Class<E> type, Specification<E> spec, String keyAttr) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Number> cq = cb.createQuery(Number.class);
		Root<E> root = cq.from(type);
		cq.select(cb.max(root.<Number>get(keyAttr)));
		Predicate where = spec != null ? spec.toPredicate(root, cq, cb) : null;
		if (where != null) cq.where(where);
		Number max = entityManager.createQuery(cq).getSingleResult();
		return max == null ? 0 : max.longValue();
	}

	private static <E> Specification<E> and(Specification<E> spec, Specification<E> more) {
		return spec != null ? spec.and(more) : more;
	}
}
