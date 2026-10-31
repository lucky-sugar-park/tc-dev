package com.wow.be.tc.plc.management;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.wow.be.framework.Condition;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.PlcConnectorInfoStatus;
import com.wow.be.tc.plc.connector.PlcAdapterManager;
import com.wow.be.tc.plc.connector.ProgrammableControllerAdapter;
import com.wow.be.tc.plc.remote.InternalSignature;
import com.wow.be.tc.plc.remote.RemotePlcAdapter;

import lombok.extern.slf4j.Slf4j;

/**
 * PLC 소유권 — PLC 마다 연결하는 인스턴스를 Redis 임대로 정확히 하나 정한다(docs/plc-ha-design.md §3.2·§3.3, 다중화 3단계).
 * {@code tc.plc.ownership.enabled=false}(기본)면 아무것도 하지 않는다.
 *
 * <ul>
 *   <li>임대: {@code tc:plc-owner:{이름}} = 인스턴스 ID, {@code SET NX PX}(10초). 3초마다 "내 ID 일 때만" 연장한다.
 *       키가 없어졌으면(만료·누가 지움) 다시 잡고, 남의 ID 면 연결을 닫는다(강등, 결정 H1b).</li>
 *   <li>검사(2초마다): 게시(DB STATUS_1)된 커넥터 가운데 주인 없는 것을 균형 규칙 {@code ceil(게시 수 / 인스턴스 수)} 까지 잡고
 *       연결한다. 게시가 풀렸거나 지워진 커넥터는 연결을 닫고 임대를 반납한다.</li>
 *   <li>Redis 에 닿지 않으면 가진 연결을 그대로 둔다(fail-static) — 이 인스턴스만 끊긴 것인지 Redis 전체 장애인지 안에서는 가릴 수
 *       없다(§5.3 보완). 그 사이 다른 인스턴스가 임대를 가져가도 PLC 가 포트당 연결 하나만 받으면 두 번째 연결은 거부되고, 이
 *       인스턴스가 Redis 에 다시 닿으면 남의 임대를 보고 강등한다.</li>
 *   <li>다른 인스턴스가 주인인 PLC 는 원격 어댑터({@link RemotePlcAdapter})로 그 인스턴스에 PLC 교환을 넘긴다(4단계). 주인이 없는
 *       사이(임대 만료 전후)에는 503 이다.</li>
 * </ul>
 * 갱신과 검사는 각자의 스레드에서 돈다 — 검사가 PLC 연결을 기다리는 동안에도 임대 갱신이 밀리지 않게.
 */
@Slf4j
@Component
@EnableConfigurationProperties(PlcOwnershipProperties.class)
public class PlcOwnershipManager {

	static final String KEY = "tc:plc-owner:";

	/** 1 연장, 2 키가 없어 다시 잡음, 0 남의 임대 */
	private static final RedisScript<Long> RENEW = new DefaultRedisScript<>(
		"local v = redis.call('GET', KEYS[1]) "
		+ "if v == ARGV[1] then redis.call('PEXPIRE', KEYS[1], ARGV[2]) return 1 end "
		+ "if not v then redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2]) return 2 end "
		+ "return 0", Long.class);

	private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>(
		"if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0", Long.class);

	private final PlcOwnershipProperties properties;

	private final StringRedisTemplate redis;

	private final InstanceIdentity identity;

	private final InstanceRegistry registry;

	private final ObjectProvider<PlcConnectorManagementService> connectorService;

	private final PlcConfig plcConfig;

	private final PlcConnectionStateStore stateStore;

	private final InternalSignature signature;

	/** 이 인스턴스가 임대를 가진 커넥터 */
	private final Set<String> owned = ConcurrentHashMap.newKeySet();

	/**
	 * 게시·해제 API 가 다루는 중인 커넥터 — 검사가 건드리지 않는다. API 는 임대·연결을 먼저 바꾸고 의도(DB)는 트랜잭션이 끝날 때
	 * 저장되므로, 그 사이 검사가 옛 의도를 보고 방금 연 연결을 닫거나 방금 닫은 연결을 다시 열지 않게 한다.
	 */
	private final Set<String> controlling = ConcurrentHashMap.newKeySet();

	private final AtomicBoolean redisFailing = new AtomicBoolean();

	private volatile boolean stopping;

	private ScheduledExecutorService renewer;

	private ScheduledExecutorService scanner;

	public PlcOwnershipManager(PlcOwnershipProperties properties, RedisConnectionFactory connectionFactory, InstanceIdentity identity,
			InstanceRegistry registry, ObjectProvider<PlcConnectorManagementService> connectorService, PlcConfig plcConfig,
			PlcConnectionStateStore stateStore, InternalSignature signature) {
		this.properties = properties;
		this.redis = new StringRedisTemplate(connectionFactory);
		this.identity = identity;
		this.registry = registry;
		this.connectorService = connectorService;
		this.plcConfig = plcConfig;
		this.stateStore = stateStore;
		this.signature = signature;
	}

	public boolean isEnabled() {
		return properties.isEnabled();
	}

	/** 기동이 끝나면 갱신·검사를 시작한다. 첫 검사는 바로 — 게시된 커넥터를 곧 연결하게 */
	@EventListener(ApplicationReadyEvent.class)
	public void start() {
		if (!isEnabled()) {
			return;
		}
		log.info("PLC 소유권 관리를 시작합니다: instance={}, 임대 {}·갱신 {}·검사 {}", identity.id(),
			properties.getLeaseTtl(), properties.getRenewInterval(), properties.getScanInterval());
		if (!signature.isConfigured()) {
			log.warn("TC_INTERNAL_SECRET 이 없어 다른 인스턴스와 PLC 교환을 주고받지 못합니다 — 다른 인스턴스가 연결한 PLC 요청은 실패합니다.");
		}
		if (!registry.hasInternalUrl()) {
			log.warn("tc.instance.internal-url 이 없어 다른 인스턴스가 이 인스턴스로 PLC 교환을 넘기지 못합니다.");
		}
		renewer = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "plc-ownership-renew"));
		scanner = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "plc-ownership-scan"));
		long renew = properties.getRenewInterval().toMillis();
		renewer.scheduleWithFixedDelay(this::renewQuietly, 0, renew, TimeUnit.MILLISECONDS);
		scanner.scheduleWithFixedDelay(this::scanQuietly, 0, properties.getScanInterval().toMillis(), TimeUnit.MILLISECONDS);
	}

	/**
	 * 앱 종료 — 새 임대를 잡지 않게 멈춘다. 연결을 닫은 뒤 {@link #releaseAll()} 로 임대를 반납한다(§5.2 순서 ③④).
	 */
	public void stopAcquiring() {
		stopping = true;
		if (scanner != null) {
			scanner.shutdownNow();
		}
		if (renewer != null) {
			renewer.shutdownNow();
		}
	}

	/** 앱 종료 — 연결을 닫은 뒤 부른다. 다른 인스턴스가 다음 검사(2초 안)에서 바로 이어받는다 */
	public void releaseAll() {
		if (!isEnabled()) {
			return;
		}
		for (String name : new HashSet<>(owned)) {
			releaseLease(name);
		}
		owned.clear();
		try {
			registry.remove();
		} catch (RuntimeException e) {
			log.warn("인스턴스 목록에서 빠지지 못했습니다(만료로 사라짐): {}", e.toString());
		}
	}

	// ── 게시·해제 API ──

	/**
	 * 게시 API — 임대를 잡을 수 있으면 잡고 바로 연결한다(연결 실패는 그대로 알린다). 다른 인스턴스가 주인이면 연결하지 않는다
	 * (그 인스턴스가 연결을 유지한다).
	 *
	 * @return 이 인스턴스가 연결했으면 true
	 */
	public boolean publish(PlcConnectorInfo info, Properties props) throws ToolControlException {
		String name = info.getName();
		controlling.add(name);
		afterTransaction(committed -> controlling.remove(name));
		String owner;
		try {
			Boolean acquired = redis.opsForValue().setIfAbsent(KEY + name, identity.id(), properties.getLeaseTtl());
			owner = Boolean.TRUE.equals(acquired) ? identity.id() : redis.opsForValue().get(KEY + name);
		} catch (RuntimeException e) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE,
				"PLC 소유권(Redis)을 확인하지 못해 게시하지 못했습니다: " + name + " — " + e.getMessage());
		}
		if (!identity.id().equals(owner)) {
			log.info("PLC {} 는 다른 인스턴스({})가 연결하고 있습니다 — 게시 상태만 남기고 요청은 그쪽으로 넘깁니다.", name, owner);
			pointToOwner(info);
			return false;
		}
		try {
			PlcAdapterManager.getPlcManager().removeRemote(name);
			PlcAdapterManager.getPlcManager().loadProgrammableControllerAdapter(props);
		} catch (ToolControlException e) {
			releaseLease(name);
			throw e;
		}
		owned.add(name);
		return true;
	}

	/**
	 * 해제 API — 연결을 닫기 전에 부른다. 이 인스턴스가 주인이면 해제(의도)가 저장된 뒤 임대를 반납한다 — 먼저 반납하면 다른
	 * 인스턴스가 아직 게시로 보이는 의도를 읽고 잠깐 연결할 수 있다. 다른 인스턴스가 주인이면 그쪽이 다음 검사에서 닫는다.
	 * 의도를 저장하는 트랜잭션 안에서 불러야 한다 — 트랜잭션이 없으면 바로 반납해, 그 사이 검사가 다시 연결할 수 있다.
	 */
	public void release(String name) {
		if (!isEnabled()) {
			return;
		}
		controlling.add(name);
		afterTransaction(committed -> {
			if (committed) {
				owned.remove(name);
				releaseLease(name);
			}
			controlling.remove(name);
		});
	}

	/** 트랜잭션이 있으면 끝난 뒤(커밋 여부를 넘김), 없으면 바로 */
	private static void afterTransaction(Consumer<Boolean> action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					action.accept(status == STATUS_COMMITTED);
				}
			});
		} else {
			action.accept(true);
		}
	}

	// ── 갱신 ──

	void renewQuietly() {
		try {
			renew();
		} catch (RuntimeException e) {
			redisFailed(e);
		}
	}

	void renew() {
		if (stopping) {
			return;
		}
		registry.heartbeat(properties.getLeaseTtl());
		String ttl = String.valueOf(properties.getLeaseTtl().toMillis());
		for (String name : new HashSet<>(owned)) {
			Long result = redis.execute(RENEW, List.of(KEY + name), identity.id(), ttl);
			if (result != null && result == 0L) {
				log.warn("PLC {} 의 임대를 다른 인스턴스가 가졌습니다 — 이 인스턴스의 연결을 닫습니다(강등).", name);
				owned.remove(name);
				closeLocal(name);
			} else if (result != null && result == 2L) {
				log.warn("PLC {} 의 임대가 없어져 다시 잡았습니다(만료·삭제). 연결은 그대로입니다.", name);
			}
		}
		redisRecovered();
	}

	// ── 검사 ──

	void scanQuietly() {
		try {
			scan();
		} catch (RuntimeException e) {
			redisFailed(e);
		}
	}

	void scan() {
		if (stopping) {
			return;
		}
		Map<String, PlcConnectorInfo> published = new LinkedHashMap<>();
		for (PlcConnectorInfo info : connectorService.getObject().searchPlcConnectores(new Condition())) {
			if (info.getStatus1() == PlcConnectorInfoStatus.PUBLISHED) {
				published.put(info.getName(), info);
			}
		}

		// 게시가 풀렸거나 지워진 커넥터 — 연결을 닫고 임대를 반납한다. 원격 어댑터는 뺀다
		Set<String> present = new HashSet<>(owned);
		PlcAdapterManager.getPlcManager().adapters().forEach(a -> present.add(a.getName()));
		for (String name : present) {
			if (!published.containsKey(name) && !controlling.contains(name)) {
				if (owned.remove(name) || isLocal(name)) {
					log.info("PLC {} 는 게시 상태가 아니라 연결을 닫고 임대를 반납합니다.", name);
				}
				closeLocal(name);
				PlcAdapterManager.getPlcManager().removeRemote(name);
				releaseLease(name);
			}
		}

		int limit = limit(published.size(), registry.liveCount());
		for (PlcConnectorInfo info : published.values()) {
			String name = info.getName();
			if (controlling.contains(name)) {
				continue;
			}
			if (owned.contains(name)) {
				if (!isLocal(name)) {
					connect(info);  // 임대는 있는데 로컬 어댑터가 없음(로드 실패 뒤, 강등 뒤 다시 잡음 등)
				}
				continue;
			}
			if (owned.size() < limit && !stopping
					&& Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(KEY + name, identity.id(), properties.getLeaseTtl()))) {
				owned.add(name);
				log.info("PLC {} 의 임대를 얻었습니다 — 연결합니다(이 인스턴스 {}개 / 한도 {}).", name, owned.size(), limit);
				connect(info);
				continue;
			}
			pointToOwner(info);
		}
		redisRecovered();
	}

	/**
	 * 이 인스턴스가 주인이 아닌 PLC — 주인 인스턴스로 넘기는 원격 어댑터를 둔다(4단계). 주인이 없거나 주인의 내부 주소를 모르면
	 * 원격 어댑터를 빼 요청이 503 이 되게 한다. 남의 임대인데 로컬 어댑터가 있으면(임대 없이 올라간 경우) 닫는다.
	 */
	private void pointToOwner(PlcConnectorInfo info) {
		String name = info.getName();
		String owner = redis.opsForValue().get(KEY + name);
		if (isLocal(name)) {
			closeLocal(name);
		}
		String url = owner == null || owner.equals(identity.id()) ? null : registry.urlOf(owner);
		if (url == null) {
			PlcAdapterManager.getPlcManager().removeRemote(name);
			return;
		}
		if (PlcAdapterManager.getPlcManager().get(name) instanceof RemotePlcAdapter current
				&& owner.equals(current.ownerInstance()) && url.equals(current.ownerUrl())) {
			return;
		}
		PlcAdapterManager.getPlcManager().putRemote(name, new RemotePlcAdapter(info.getId(), name, owner, url,
			signature, identity.id(), plcConfig.getRequestTimeoutMs(), this::ownerUrlOf));
		log.info("PLC {} 는 인스턴스 {}({}) 가 연결합니다 — 요청을 그쪽으로 넘깁니다.", name, owner, url);
	}

	/** 지금 주인의 내부 주소 — 원격 어댑터가 409(주인 아님) 뒤에 다시 찾을 때. 주인이 없거나 이 인스턴스면 null */
	private String ownerUrlOf(String name) {
		try {
			String owner = redis.opsForValue().get(KEY + name);
			return owner == null || owner.equals(identity.id()) ? null : registry.urlOf(owner);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static boolean isLocal(String name) {
		ProgrammableControllerAdapter adapter = PlcAdapterManager.getPlcManager().get(name);
		return adapter != null && adapter.isLocal();
	}

	/** 균형 규칙 — 한 인스턴스가 가질 PLC 수의 상한 {@code ceil(게시 수 / 살아 있는 인스턴스 수)} */
	static int limit(int published, int liveInstances) {
		int n = Math.max(liveInstances, 1);
		return (published + n - 1) / n;
	}

	private void connect(PlcConnectorInfo info) {
		PlcAdapterManager.getPlcManager().removeRemote(info.getName());
		Properties props = PlcAdapterProps.of(info, plcConfig,
			new PlcConnectionEventDbHandler(connectorService.getObject(), stateStore), true);
		props.put("fastRetryUntil", System.currentTimeMillis() + properties.getTakeoverFastRetry().toMillis());
		props.put("fastRetryIntervalMs", properties.getTakeoverRetryInterval().toMillis());
		try {
			PlcAdapterManager.getPlcManager().loadProgrammableControllerAdapter(props);
		} catch (ToolControlException e) {
			// 설정 오류(포트·VIP 없음 등) — 임대를 놓아 다른 인스턴스가 시도하게 한다
			log.warn("PLC {} 어댑터를 올리지 못해 임대를 반납합니다: {}", info.getName(), e.getMessage());
			owned.remove(info.getName());
			releaseLease(info.getName());
		}
	}

	private void closeLocal(String name) {
		if (!isLocal(name)) {
			return;
		}
		try {
			PlcAdapterManager.getPlcManager().releaseProgrammableControllerAdapter(name);
		} catch (ToolControlException | RuntimeException e) {
			log.warn("PLC {} 연결을 닫지 못했습니다: {}", name, e.toString());
		}
	}

	private void releaseLease(String name) {
		try {
			redis.execute(RELEASE, List.of(KEY + name), identity.id());
		} catch (RuntimeException e) {
			log.warn("PLC {} 임대를 반납하지 못했습니다(만료로 풀림): {}", name, e.toString());
		}
	}

	private void redisFailed(RuntimeException e) {
		if (redisFailing.compareAndSet(false, true)) {
			log.warn("PLC 소유권(Redis)에 닿지 않습니다 — 가진 연결 {}개를 유지하고 새 임대는 잡지 않습니다: {}", owned.size(), e.toString());
		}
	}

	private void redisRecovered() {
		if (redisFailing.compareAndSet(true, false)) {
			log.info("PLC 소유권(Redis)에 다시 닿습니다.");
		}
	}

	/** 지금 이 인스턴스가 임대를 가진 커넥터(시험·화면용) */
	public Set<String> owned() {
		return Set.copyOf(owned);
	}

	private static Thread daemon(Runnable r, String name) {
		Thread t = new Thread(r, name);
		t.setDaemon(true);
		return t;
	}
}
