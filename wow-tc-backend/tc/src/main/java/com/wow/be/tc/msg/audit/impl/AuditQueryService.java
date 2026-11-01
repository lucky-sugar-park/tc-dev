package com.wow.be.tc.msg.audit.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wow.be.tc.msg.audit.AuditQuery;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditInfo;
import com.wow.be.tc.utils.RangeQueries;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/**
 * 송수신 기록 검색(화면, docs/frontend-design.md §14). 실패는 AuditDispatchInterceptor·DefaultResultDeliveryService 가 남기는 모양으로 가린다 —
 * 응답 설명이 {@code ERR_} 로 시작, 오류 행, 설명이 {@code OK} 로 시작하지 않는 전달 행.
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

	static final int MAX_SIZE = 500;

	private final ExchangedMessageAuditRepository repository;

	private final ExchangedMessageAuditRangeableRepository rangeRepository;

	private final RangeQueries rangeQueries;

	@Transactional(readOnly = true)
	public AuditQuery.Page search(AuditQuery q) {
		int page = q.page() != null && q.page() >= 0 ? q.page() : 0;
		int size = q.size() != null ? Math.min(Math.max(q.size(), 1), MAX_SIZE) : 100;
		Page<ExchangedMessageAuditEntity> result = repository.findAll(specOf(q), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
		return new AuditQuery.Page(result.getContent().stream().map(ExchangedMessageAuditEntity::info).toList(),
			result.getTotalElements(), result.getNumber(), result.getSize());
	}

	/**
	 * 구간 조회 — 화면의 구간 스크롤 표(docs/range-grid-design.md). 최신순, 스냅샷은 ID. 목록에는 본문(MSG_JSON)을 빼고 보낸다
	 * (상세 창은 거래 ID 로 따로 읽는다). 엑셀에서 본문을 고르면 includeBody.
	 */
	@Transactional(readOnly = true)
	public RangeQueries.Result<ExchangedMessageAuditInfo> range(AuditQuery q, RangeQueries.Params range, boolean includeBody) {
		return rangeQueries.byKey(ExchangedMessageAuditEntity.class, specOf(q), "id", range, Sort.by(Sort.Direction.DESC, "id"),
			repository, rangeRepository, e -> {
				ExchangedMessageAuditInfo info = e.info();
				if (!includeBody) info.setDataMap(new HashMap<>());
				return info;
			});
	}

	private static Specification<ExchangedMessageAuditEntity> specOf(AuditQuery q) {
		if (q.from() != null && q.to() != null && q.from().after(q.to())) {
			throw new IllegalArgumentException("기간의 시작이 끝보다 늦습니다");
		}
		return (root, query, cb) -> {
			List<Predicate> ps = new ArrayList<>();
			if (q.from() != null) {
				ps.add(cb.greaterThanOrEqualTo(root.get("timestamp"), q.from()));
			}
			if (q.to() != null) {
				ps.add(cb.lessThanOrEqualTo(root.get("timestamp"), q.to()));
			}
			if (q.opcode() != null && !q.opcode().isBlank()) {
				ps.add(cb.like(cb.lower(root.get("name")), "%" + q.opcode().trim().toLowerCase(Locale.ROOT) + "%"));
			}
			if (q.transactionId() != null && !q.transactionId().isBlank()) {
				ps.add(cb.equal(root.get("transactionId"), q.transactionId().trim()));
			}
			if (q.principal() != null && !q.principal().isBlank()) {
				ps.add(cb.equal(root.get("principal"), q.principal().trim()));
			}
			if (q.messageTypes() != null && !q.messageTypes().isEmpty()) {
				ps.add(root.get("messageType").in(q.messageTypes()));
			}
			if (Boolean.TRUE.equals(q.failedOnly())) {
				ps.add(cb.or(
					cb.and(cb.equal(root.get("messageType"), "RESPONSE"), cb.like(root.get("description"), "ERR%")),
					cb.equal(root.get("messageType"), "ERROR"),
					cb.and(cb.equal(root.get("messageType"), "DELIVERY"), cb.notLike(root.get("description"), "OK%"))));
			}
			return cb.and(ps.toArray(Predicate[]::new));
		};
	}
}
