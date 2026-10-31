package com.wow.be.tc.ui;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.AuthenticatedOnly;
import com.wow.be.tc.plc.management.PlcConnectionState;
import com.wow.be.tc.plc.management.PlcConnectionStateStore;
import com.wow.be.tc.plc.management.PlcConnectorInfo;
import com.wow.be.tc.plc.management.PlcConnectorManagementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

/**
 * 홈 화면 요약 (docs/frontend-design.md §4.2). 커넥터 연결 상태 건수, 오늘 요청·실패 건수, 최근 오류.
 * 실패는 감사의 응답 설명이 오류 코드({@code ERR_…})로 시작하는 것과 오류 행이다(AuditDispatchInterceptor).
 */
@Tag(name = "UI — 홈", description = "대시보드 요약")
@RestController
@RequiredArgsConstructor
@AuthenticatedOnly
public class DashboardController {

	private static final int RECENT_ERRORS = 10;

	private final PlcConnectorManagementService connectorService;

	private final PlcConnectionStateStore connectionStateStore;

	@PersistenceContext
	private EntityManager em;

	@Operation(summary = "홈 요약")
	@GetMapping("/api/v1/dashboard/summary")
	@Transactional(readOnly = true)
	public Map<String, Object> summary() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("connectors", connectors());
		out.put("today", today());
		out.put("recentErrors", recentErrors());
		out.put("generatedAt", System.currentTimeMillis());
		return out;
	}

	private Map<String, Object> connectors() {
		List<PlcConnectorInfo> all = connectorService.searchAllPlcConnectores();
		Map<String, PlcConnectionState> states = connectionStateStore.find(all.stream().map(PlcConnectorInfo::getName).toList());
		int published = 0;
		int connected = 0;
		List<Map<String, Object>> rows = new ArrayList<>();
		for (PlcConnectorInfo c : all) {
			PlcConnectionState s = states.get(c.getName());
			boolean pub = c.isPublished();
			boolean up = s != null && s.getActiveChannels() > 0;
			published += pub ? 1 : 0;
			connected += pub && up ? 1 : 0;
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("name", c.getName());
			row.put("protocol", c.getProtocol());
			row.put("published", pub);
			row.put("connection", s);
			rows.add(row);
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("total", all.size());
		out.put("published", published);
		out.put("connected", connected);
		out.put("disconnected", published - connected);
		out.put("items", rows);
		return out;
	}

	private Map<String, Object> today() {
		Date from = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
		long requests = em.createQuery("select count(a) from ExchangedMessageAuditEntity a where a.timestamp >= :from and a.messageType = 'REQUEST'", Long.class)
			.setParameter("from", from).getSingleResult();
		long failures = em.createQuery("select count(a) from ExchangedMessageAuditEntity a where a.timestamp >= :from and "
				+ "((a.messageType = 'RESPONSE' and a.description like 'ERR%') or a.messageType = 'ERROR')", Long.class)
			.setParameter("from", from).getSingleResult();
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("from", from.getTime());
		out.put("requests", requests);
		out.put("failures", failures);
		return out;
	}

	private List<Map<String, Object>> recentErrors() {
		List<Object[]> rows = em.createQuery("select a.transactionId, a.name, a.timestamp, a.description, a.messageType "
				+ "from ExchangedMessageAuditEntity a where (a.messageType = 'RESPONSE' and a.description like 'ERR%') or a.messageType = 'ERROR' "
				+ "order by a.id desc", Object[].class)
			.setMaxResults(RECENT_ERRORS).getResultList();
		List<Map<String, Object>> out = new ArrayList<>();
		for (Object[] r : rows) {
			Map<String, Object> e = new LinkedHashMap<>();
			e.put("transactionId", r[0]);
			e.put("opcode", r[1]);
			e.put("timestamp", r[2] instanceof Date d ? d.getTime() : null);
			String description = (String) r[3];
			int colon = description != null ? description.indexOf(':') : -1;
			e.put("errorCode", colon > 0 ? description.substring(0, colon) : description);
			e.put("errorMessage", colon > 0 ? description.substring(colon + 1).trim() : null);
			e.put("kind", r[4]);
			out.add(e);
		}
		return out;
	}
}
