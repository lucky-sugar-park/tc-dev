package com.wow.be.tc.vplc.management;

import java.util.Date;

/**
 * 가상 PLC 연결 이력 찾기 — 화면 가상 PLC › 가상 PLC 연결 이력. 비운 조건은 걸지 않는다. 최신순.
 *
 * @param search   가상 PLC 이름 또는 클라이언트 IP 에 든 글(대소문자 무시). 지운 가상 PLC 의 이력은 IP 로만 찾는다
 * @param protocol 가상 PLC 종류(MELSEC_MC·MODBUS_TCP·OPC_UA) — 지금 있는 가상 PLC 의 이력만
 * @param from     연결 기간 시작(포함)
 * @param to       연결 기간 끝(포함)
 * @param page     0 부터
 * @param size     쪽 크기(1~500, 기본 100)
 */
public record ConnectionHistoryQuery(String search, String protocol, Date from, Date to, Integer page, Integer size) {

	public int pageOrDefault() {
		return page == null || page < 0 ? 0 : page;
	}

	public int sizeOrDefault() {
		return size == null || size < 1 ? 100 : Math.min(size, 500);
	}
}
