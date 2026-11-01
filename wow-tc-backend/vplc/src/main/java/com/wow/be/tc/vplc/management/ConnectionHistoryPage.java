package com.wow.be.tc.vplc.management;

import java.util.List;

/** 가상 PLC 연결 이력 한 쪽 — 최신순 */
public record ConnectionHistoryPage(List<ConnectionClientHistoryInfo> items, long total, int page, int size) {
}
