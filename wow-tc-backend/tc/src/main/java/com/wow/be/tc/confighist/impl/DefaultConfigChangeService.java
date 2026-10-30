package com.wow.be.tc.confighist.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.wow.be.framework.security.ClientAddressResolver;
import com.wow.be.tc.confighist.ConfigChangeInfo;
import com.wow.be.tc.confighist.ConfigChangeInfo.Action;
import com.wow.be.tc.confighist.ConfigChangeInfo.ActorType;
import com.wow.be.tc.confighist.ConfigChangeInfo.TargetType;
import com.wow.be.tc.confighist.ConfigChangeRecorder;
import com.wow.be.tc.confighist.ConfigChangeService;
import com.wow.be.tc.security.RequestPrincipals;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 설정 변경 이력 기록·조회 (docs/auth-and-audit-design.md §5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultConfigChangeService implements ConfigChangeRecorder, ConfigChangeService {

	/** 이력 JSON 에서 가리는 필드 이름(대소문자 무시) */
	private static final Set<String> MASKED = Set.of("password", "passwordhash", "keyhash", "apikey", "secret", "secrethash");

	/** 같은 HTTP 요청에서 생긴 이력을 묶는 ID 를 담아 두는 요청 속성 */
	private static final String TX_ATTR = ConfigChangeRecorder.class.getName() + ".TX";

	private static final Gson GSON = new GsonBuilder()
		.serializeNulls()
		.setExclusionStrategies(new ExclusionStrategy() {
			@Override
			public boolean shouldSkipField(FieldAttributes f) {
				return false;
			}

			@Override
			public boolean shouldSkipClass(Class<?> clazz) {
				// ScheduleJobInfo.jobClass 처럼 Class 형 필드는 Gson 이 직렬화하지 못한다
				return clazz == Class.class;
			}
		})
		.create();

	private final ConfigChangeRepository repository;

	private final ClientAddressResolver addressResolver;

	// ── 기록 ──

	@Override
	@Transactional
	public void record(TargetType targetType, String targetId, String targetName, Action action, String detail,
			Object before, Object after) {
		String beforeJson = toMaskedJson(before);
		String afterJson = toMaskedJson(after);
		if (action == Action.UPDATE && Objects.equals(stripVolatile(beforeJson), stripVolatile(afterJson))) {
			return;
		}

		ConfigChangeEntity e = new ConfigChangeEntity();
		e.setId(UUID.randomUUID().toString());
		e.setChangedAt(new Date());
		String principal = RequestPrincipals.current();
		if (principal.startsWith("client:")) {
			e.setActorType(ActorType.API_CLIENT);
			e.setActorId(principal.substring("client:".length()));
		} else if (principal.startsWith("user:")) {
			e.setActorType(ActorType.USER);
			e.setActorId(principal.substring("user:".length()));
		} else {
			e.setActorType(ActorType.SYSTEM);
			e.setActorId(RequestPrincipals.SYSTEM);
		}
		HttpServletRequest request = currentRequest();
		if (request != null) {
			e.setActorIp(addressResolver.resolve(request));
			Object tx = request.getAttribute(TX_ATTR);
			if (tx == null) {
				tx = UUID.randomUUID().toString();
				request.setAttribute(TX_ATTR, tx);
			}
			e.setTransactionId(tx.toString());
		}
		e.setTargetType(targetType);
		e.setTargetId(targetId != null ? targetId : "");
		e.setTargetName(truncate(targetName, 200));
		e.setAction(action);
		e.setDetail(truncate(detail, 100));
		e.setBeforeJson(beforeJson);
		e.setAfterJson(afterJson);
		repository.save(e);
	}

	private static HttpServletRequest currentRequest() {
		RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
		return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
	}

	/** 비밀값을 가린 JSON. null 이면 null */
	static String toMaskedJson(Object value) {
		if (value == null) {
			return null;
		}
		try {
			JsonElement tree = GSON.toJsonTree(value);
			mask(tree);
			return GSON.toJson(tree);
		} catch (RuntimeException ex) {
			log.warn("설정 변경 이력의 값을 JSON 으로 바꾸지 못했습니다: {}", value.getClass().getName(), ex);
			return "\"" + value.getClass().getSimpleName() + " (직렬화 실패)\"";
		}
	}

	private static void mask(JsonElement e) {
		if (e instanceof JsonObject o) {
			for (Map.Entry<String, JsonElement> entry : new ArrayList<>(o.entrySet())) {
				if (MASKED.contains(entry.getKey().toLowerCase()) && !entry.getValue().isJsonNull()) {
					o.add(entry.getKey(), new JsonPrimitive("***"));
				} else {
					mask(entry.getValue());
				}
			}
		} else if (e instanceof JsonArray a) {
			a.forEach(DefaultConfigChangeService::mask);
		}
	}

	/**
	 * 수정 시각이나 운영 상태(게시·트리거 상태)처럼 운영 중에 바뀌는 값은 "변경 없음" 판단에서 뺀다 — 상태 변화는 CONTROL 로
	 * 따로 남는다.
	 */
	private static String stripVolatile(String json) {
		return json == null ? null
			: json.replaceAll("\"(updatedDate|updatedDateAsString|updatedBy|stateChangedTime|status1|status2|published|triggerState)\":"
				+ "(\"[^\"]*\"|null|true|false|-?\\d+),?", "");
	}

	private static String truncate(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max);
	}

	// ── 조회 ──

	@Override
	public Page<ConfigChangeInfo> search(Date from, Date to, TargetType targetType, String targetId, String actorId,
			int page, int size) {
		Specification<ConfigChangeEntity> spec = Specification.unrestricted();
		if (from != null) {
			spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("changedAt"), from));
		}
		if (to != null) {
			spec = spec.and((r, q, cb) -> cb.lessThan(r.get("changedAt"), to));
		}
		if (targetType != null) {
			spec = spec.and((r, q, cb) -> cb.equal(r.get("targetType"), targetType));
		}
		if (targetId != null && !targetId.isBlank()) {
			spec = spec.and((r, q, cb) -> cb.equal(r.get("targetId"), targetId));
		}
		if (actorId != null && !actorId.isBlank()) {
			spec = spec.and((r, q, cb) -> cb.equal(r.get("actorId"), actorId));
		}
		PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 500), Sort.by(Sort.Direction.DESC, "changedAt"));
		return repository.findAll(spec, pr).map(ConfigChangeEntity::info);
	}

	@Override
	public List<ConfigChangeInfo> historyOf(TargetType targetType, String targetId) {
		return repository.findByTargetTypeAndTargetIdOrderByChangedAtDesc(targetType, targetId).stream()
			.map(ConfigChangeEntity::info)
			.toList();
	}

	@Override
	@Transactional
	public long purgeOlderThan(Date before) {
		return repository.deleteOlderThan(before);
	}
}
