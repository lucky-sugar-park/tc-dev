package com.wow.be.tc.msg.audit;

import java.util.Date;
import java.util.List;

/**
 * 송수신 기록 검색 조건 (docs/frontend-design.md §14) — 화면의 기간·opcode·거래 ID·종류·실패만. framework 의 일반 검색 조건은
 * 날짜 범위를 받지 못해(BETWEEN 이 글자 비교, GREATER_THAN 은 500) 따로 둔다. 비운 칸은 조건이 아니다.
 *
 * @param opcode        인터페이스 이름에 든 글자
 * @param messageTypes  REQUEST·RESPONSE·ERROR·DELIVERY 중(비우면 전부)
 * @param failedOnly    실패만 — 응답 설명이 오류 코드(ERR_…)로 시작하거나 ERROR, 실패한 DELIVERY
 * @param size          쪽 크기, 1~500(기본 100)
 */
public record AuditQuery(Date from, Date to, String opcode, String transactionId, List<String> messageTypes, Boolean failedOnly,
		String principal, Integer page, Integer size) {

	/** 한 쪽 — 최신순 */
	public record Page(List<ExchangedMessageAuditInfo> items, long total, int page, int size) {
	}
}
