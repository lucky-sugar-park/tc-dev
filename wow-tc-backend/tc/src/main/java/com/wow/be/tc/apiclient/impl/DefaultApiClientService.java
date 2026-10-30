package com.wow.be.tc.apiclient.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.wow.be.framework.persist.PersistentService;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.apiclient.ApiClientInfo;
import com.wow.be.tc.apiclient.ApiClientKeyInfo;
import com.wow.be.tc.apiclient.ApiClientKeyInfo.KeyStatus;
import com.wow.be.tc.apiclient.ApiClientProperties;
import com.wow.be.tc.apiclient.ApiClientService;
import com.wow.be.tc.apiclient.IpRanges;
import com.wow.be.tc.apiclient.IssuedApiKey;
import com.wow.be.tc.apiclient.VerifiedApiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * API 클라이언트·키 관리와 키 검증 (설계 §6).
 *
 * <p>검증 결과는 keyId 별로 {@code tc.api-client.cache-ttl} 동안 메모리에 둔다. 이 인스턴스에서 클라이언트나 키를 바꾸면
 * 해당 항목을 바로 지운다. 다른 인스턴스는 캐시 시간이 지나야 반영된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultApiClientService implements ApiClientService, PersistentService<ApiClientInfo, ApiClientEntity> {

	/** 영문 소문자·숫자, 하이픈으로 이음(예: mes-prod) */
	private static final Pattern CLIENT_ID_PATTERN = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

	private static final int MAX_ACTIVE_KEYS = 2;

	/** 마지막 사용 시각은 키마다 이 간격에 한 번만 DB 에 쓴다 */
	private static final long TOUCH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1);

	private final ApiClientRepository clientRepository;

	private final ApiClientKeyRepository keyRepository;

	private final ApiClientProperties properties;

	private final PlatformTransactionManager transactionManager;

	private final Map<String, CachedKey> cache = new ConcurrentHashMap<>();

	private final Map<String, Long> lastTouched = new ConcurrentHashMap<>();

	/** 캐시 항목. client 가 null 이면 클라이언트가 없거나 삭제·중지된 것이다. */
	private record CachedKey(String clientId, String keyHash, KeyStatus status, Date expiresAt,
			VerifiedApiClient client, long loadedAt) {
	}

	// ── 조회 ──

	@Override
	public List<ApiClientInfo> searchAll() {
		Map<String, List<ApiClientKeyEntity>> keysByClient = keyRepository.findAll().stream()
			.collect(Collectors.groupingBy(ApiClientKeyEntity::getClientId));
		List<ApiClientInfo> result = new ArrayList<>();
		for (ApiClientEntity entity : clientRepository.findByDeletedAtIsNullOrderByClientIdAsc()) {
			result.add(summarize(entity.info(), keysByClient.getOrDefault(entity.getClientId(), List.of()), false));
		}
		return result;
	}

	@Override
	public ApiClientInfo find(String clientId) throws ToolControlException {
		ApiClientEntity entity = load(clientId);
		return summarize(entity.info(), keyRepository.findByClientIdOrderByCreatedDateDesc(clientId), true);
	}

	@Override
	public List<ApiClientKeyInfo> findKeysExpiringWithin(int withinDays) {
		long now = System.currentTimeMillis();
		return keyRepository.findByStatusAndExpiresAtBetween(KeyStatus.ACTIVE, new Date(now),
				new Date(now + TimeUnit.DAYS.toMillis(withinDays))).stream()
			.map(ApiClientKeyEntity::info)
			.toList();
	}

	// ── 클라이언트 변경 ──

	@Override
	@Transactional
	public ApiClientInfo register(ApiClientInfo info) throws ToolControlException {
		if (!StringUtils.hasText(info.getClientId()) || info.getClientId().length() > 100
				|| !CLIENT_ID_PATTERN.matcher(info.getClientId()).matches()) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST,
				"클라이언트 ID 는 영문 소문자·숫자와 하이픈(-)으로 100자 안에서 씁니다: " + info.getClientId());
		}
		Optional<ApiClientEntity> existing = clientRepository.findById(info.getClientId());
		if (existing.isPresent()) {
			throw ToolControlException.of(ToolControlException.CONFLICT, existing.get().getDeletedAt() != null
				? "삭제한 클라이언트 ID 는 다시 쓸 수 없습니다(감사 기록과 섞이지 않게): " + info.getClientId()
				: "같은 ID 의 클라이언트가 이미 있습니다: " + info.getClientId());
		}
		validate(info);

		ApiClientEntity entity = new ApiClientEntity();
		entity.setClientId(info.getClientId());
		entity.setEnabled(true);
		entity.from(info);
		return summarize(clientRepository.save(entity).info(), List.of(), true);
	}

	@Override
	@Transactional
	public ApiClientInfo update(ApiClientInfo info) throws ToolControlException {
		ApiClientEntity entity = load(info.getClientId());
		validate(info);
		entity.from(info);
		ApiClientEntity saved = clientRepository.save(entity);
		invalidateClient(saved.getClientId());
		return summarize(saved.info(), keyRepository.findByClientIdOrderByCreatedDateDesc(saved.getClientId()), true);
	}

	@Override
	@Transactional
	public ApiClientInfo setEnabled(String clientId, boolean enabled) throws ToolControlException {
		ApiClientEntity entity = load(clientId);
		entity.setEnabled(enabled);
		ApiClientEntity saved = clientRepository.save(entity);
		invalidateClient(clientId);
		log.info("API 클라이언트 {}: {}", enabled ? "사용 재개" : "사용 중지", clientId);
		return summarize(saved.info(), keyRepository.findByClientIdOrderByCreatedDateDesc(clientId), true);
	}

	@Override
	@Transactional
	public void delete(String clientId) throws ToolControlException {
		ApiClientEntity entity = load(clientId);
		long now = System.currentTimeMillis();
		long active = keyRepository.findByClientIdOrderByCreatedDateDesc(clientId).stream().filter(k -> k.isActive(now)).count();
		if (active > 0) {
			throw ToolControlException.of(ToolControlException.CONFLICT,
				"활성 키가 " + active + "개 있습니다. 먼저 모두 폐기해야 삭제할 수 있습니다: " + clientId);
		}
		keyRepository.deleteByClientId(clientId);
		entity.setEnabled(false);
		entity.setDeletedAt(new Date(now));
		clientRepository.save(entity);
		invalidateClient(clientId);
		log.info("API 클라이언트 삭제: {}", clientId);
	}

	// ── 키 ──

	@Override
	@Transactional
	public IssuedApiKey issueKey(String clientId, Integer expiresInDays) throws ToolControlException {
		load(clientId);
		int days = expiresInDays == null ? properties.getDefaultKeyDays() : expiresInDays;
		if (days < 1 || days > properties.getMaxKeyDays()) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST,
				"키 수명은 1~" + properties.getMaxKeyDays() + "일 사이로 정합니다: " + days);
		}
		long now = System.currentTimeMillis();
		long active = keyRepository.findByClientIdOrderByCreatedDateDesc(clientId).stream().filter(k -> k.isActive(now)).count();
		if (active >= MAX_ACTIVE_KEYS) {
			throw ToolControlException.of(ToolControlException.CONFLICT,
				"활성 키가 이미 " + MAX_ACTIVE_KEYS + "개입니다. 옛 키를 폐기한 뒤 발급합니다: " + clientId);
		}

		ApiKeyCodec.Generated generated;
		do {
			generated = ApiKeyCodec.generate();
		} while (keyRepository.existsById(generated.keyId()));

		ApiClientKeyEntity key = new ApiClientKeyEntity();
		key.setKeyId(generated.keyId());
		key.setClientId(clientId);
		key.setKeyHash(generated.secretHash());
		key.setStatus(KeyStatus.ACTIVE);
		key.setExpiresAt(new Date(now + TimeUnit.DAYS.toMillis(days)));
		keyRepository.save(key);
		log.info("API 키 발급: client={}, keyId={}, expiresAt={}", clientId, key.getKeyId(), key.getExpiresAt());
		return new IssuedApiKey(clientId, key.getKeyId(), generated.apiKey(), key.getExpiresAt());
	}

	@Override
	@Transactional
	public ApiClientKeyInfo revokeKey(String clientId, String keyId) throws ToolControlException {
		ApiClientKeyEntity key = keyRepository.findById(keyId)
			.filter(k -> k.getClientId().equals(clientId))
			.orElseThrow(() -> ToolControlException.of("ERR_404", "키를 찾을 수 없습니다: " + clientId + "/" + keyId));
		if (key.getStatus() != KeyStatus.REVOKED) {
			key.setStatus(KeyStatus.REVOKED);
			key.setRevokedAt(new Date());
			key = keyRepository.save(key);
			log.info("API 키 폐기: client={}, keyId={}", clientId, keyId);
		}
		cache.remove(keyId);
		return key.info();
	}

	// ── 검증 ──

	@Override
	public Optional<VerifiedApiClient> verify(String apiKey, String remoteAddress) {
		ApiKeyCodec.Parts parts = ApiKeyCodec.parse(apiKey);
		if (parts == null) {
			log.info("API 키 거부(형식): ip={}", remoteAddress);
			return Optional.empty();
		}
		long now = System.currentTimeMillis();
		CachedKey cached = cache.get(parts.keyId());
		if (cached == null || now - cached.loadedAt() > properties.getCacheTtl().toMillis()) {
			cached = loadForVerify(parts.keyId(), now);
			if (cached == null) {
				log.info("API 키 거부(없는 키): keyId={}, ip={}", parts.keyId(), remoteAddress);
				return Optional.empty();
			}
			cache.put(parts.keyId(), cached);
		}

		String reason = null;
		if (!ApiKeyCodec.matches(parts.secret(), cached.keyHash())) {
			reason = "해시 불일치";
		} else if (cached.status() != KeyStatus.ACTIVE) {
			reason = "폐기됨";
		} else if (cached.expiresAt() != null && cached.expiresAt().getTime() <= now) {
			reason = "만료됨";
		} else if (cached.client() == null) {
			reason = "클라이언트 중지·삭제";
		}
		if (reason != null) {
			log.info("API 키 거부({}): client={}, keyId={}, ip={}", reason, cached.clientId(), parts.keyId(), remoteAddress);
			return Optional.empty();
		}

		touch(parts.keyId(), remoteAddress, now);
		return Optional.of(cached.client());
	}

	private CachedKey loadForVerify(String keyId, long now) {
		Optional<ApiClientKeyEntity> key = keyRepository.findById(keyId);
		if (key.isEmpty()) {
			return null;
		}
		ApiClientKeyEntity k = key.get();
		VerifiedApiClient client = clientRepository.findById(k.getClientId())
			.filter(c -> c.isEnabled() && c.getDeletedAt() == null)
			.map(c -> toVerified(c, keyId))
			.orElse(null);
		return new CachedKey(k.getClientId(), k.getKeyHash(), k.getStatus(), k.getExpiresAt(), client, now);
	}

	private VerifiedApiClient toVerified(ApiClientEntity c, String keyId) {
		return new VerifiedApiClient(
			c.getClientId(),
			keyId,
			List.copyOf(ApiClientEntity.split(c.getAllowedOpcodes())),
			List.copyOf(ApiClientEntity.split(c.getAllowedIps())),
			List.copyOf(ApiClientEntity.split(c.getAllowedPushUrlPrefixes())),
			c.getRateLimitPerSec() != null ? c.getRateLimitPerSec() : properties.getDefaultRateLimitPerSec(),
			c.getMaxConcurrent() != null ? c.getMaxConcurrent() : properties.getDefaultMaxConcurrent());
	}

	/** 마지막 사용 기록. 실패해도 요청은 막지 않는다. */
	private void touch(String keyId, String remoteAddress, long now) {
		Long last = lastTouched.get(keyId);
		if (last != null && now - last < TOUCH_INTERVAL_MS) {
			return;
		}
		lastTouched.put(keyId, now);
		try {
			new TransactionTemplate(transactionManager).executeWithoutResult(status -> keyRepository.touch(keyId, new Date(now), remoteAddress));
		} catch (RuntimeException e) {
			log.warn("API 키 마지막 사용 시각을 남기지 못했습니다: keyId={}", keyId, e);
		}
	}

	private void invalidateClient(String clientId) {
		cache.entrySet().removeIf(e -> Objects.equals(e.getValue().clientId(), clientId));
	}

	// ── 공통 ──

	private ApiClientEntity load(String clientId) throws ToolControlException {
		return clientRepository.findById(clientId == null ? "" : clientId)
			.filter(c -> c.getDeletedAt() == null)
			.orElseThrow(() -> ToolControlException.of("ERR_404", "API 클라이언트를 찾을 수 없습니다: " + clientId));
	}

	private void validate(ApiClientInfo info) throws ToolControlException {
		if (!StringUtils.hasText(info.getName())) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "클라이언트 이름이 없습니다.");
		}
		if (info.getAllowedIps() != null) {
			for (String ip : info.getAllowedIps()) {
				if (StringUtils.hasText(ip) && !IpRanges.isValid(ip)) {
					throw ToolControlException.of(ToolControlException.INVALID_REQUEST,
						"허용 IP 는 숫자 주소나 CIDR(예: 10.0.1.0/24)로 씁니다: " + ip);
				}
			}
		}
		if (info.getAllowedOpcodes() != null) {
			for (String op : info.getAllowedOpcodes()) {
				if (op != null && op.contains(",")) {
					throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "opcode 에 쉼표를 쓸 수 없습니다: " + op);
				}
			}
		}
		if (info.getRateLimitPerSec() != null && info.getRateLimitPerSec() < 1) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "초당 요청 한도는 1 이상입니다.");
		}
		if (info.getMaxConcurrent() != null && info.getMaxConcurrent() < 1) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "동시 처리 한도는 1 이상입니다.");
		}
	}

	/** 키 요약(활성 수·가장 가까운 만료·마지막 사용)을 채운다. withKeys 면 키 목록도 넣는다. */
	private ApiClientInfo summarize(ApiClientInfo info, List<ApiClientKeyEntity> keys, boolean withKeys) {
		long now = System.currentTimeMillis();
		long unusedMs = TimeUnit.DAYS.toMillis(properties.getUnusedDays());
		List<ApiClientKeyInfo> keyInfos = new ArrayList<>();
		int active = 0;
		Date nearest = null;
		Date lastUsed = null;
		for (ApiClientKeyEntity k : keys) {
			ApiClientKeyInfo ki = k.info();
			if (k.isActive(now)) {
				active++;
				if (nearest == null || k.getExpiresAt().before(nearest)) {
					nearest = k.getExpiresAt();
				}
				Date since = k.getLastUsedAt() != null ? k.getLastUsedAt() : k.getCreatedDate();
				ki.setUnused(since != null && now - since.getTime() > unusedMs);
			}
			if (k.getLastUsedAt() != null && (lastUsed == null || k.getLastUsedAt().after(lastUsed))) {
				lastUsed = k.getLastUsedAt();
			}
			keyInfos.add(ki);
		}
		keyInfos.sort(Comparator.comparing(ApiClientKeyInfo::getIssuedAt, Comparator.nullsLast(Comparator.reverseOrder())));
		info.setActiveKeyCount(active);
		info.setNearestExpiresAt(nearest);
		info.setLastUsedAt(lastUsed);
		info.setKeys(withKeys ? keyInfos : null);
		return info;
	}
}
