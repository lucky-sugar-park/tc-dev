package com.wow.be.tc.vplc.opcua;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.EndpointConfig;
import org.eclipse.milo.opcua.sdk.server.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.CompositeValidator;
import org.eclipse.milo.opcua.sdk.server.identity.IdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultApplicationGroup;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.DefaultClientCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateStore;
import org.eclipse.milo.opcua.stack.core.security.MemoryTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.RsaSha256CertificateFactory;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransport;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransportConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * 가상 OPC UA 서버(Eclipse Milo). 네임스페이스 {@value #NAMESPACE_URI} 에 태그 경로대로 폴더와 변수를 만든다.
 * 태그 {@code Line1.Temp} 의 NodeId 는 {@code ns=<번호>;s=Line1.Temp} 또는 {@code nsu=<네임스페이스 URI>;s=Line1.Temp}, 폴더 {@code Line1} 은 Objects 아래.
 *
 * <p>엔드포인트(docs/opcua-design.md §7.2) — None 과 Basic256Sha256 Sign·SignAndEncrypt, 각각 익명·사용자 이름. 서버 인증서는 기동 때 자체 서명
 * (메모리 — 다시 뜨면 바뀐다). 먼저 {@value #EXTRA_NAMESPACE_URI} 를 등록해 기본 네임스페이스 번호가 3 이 된다(ns=2 가정을 잡으려고).
 *
 * <p>흉내(simulation)가 있는 태그는 주기마다 값을 바꾼다 — ramp(최소→최대 반복)·sine·toggle(BOOL)·random. tc 구독 시험용.
 */
@Slf4j
public final class VirtualOpcUaServer implements AutoCloseable {

	public static final String NAMESPACE_URI = "urn:wow:tc:vplc-opcua";

	public static final String EXTRA_NAMESPACE_URI = "urn:wow:tc:vplc-opcua:extra";

	public static final String APPLICATION_URI = "urn:wow:tc:vplc-opcua:server";

	/** 보안 — 사용자(이름 → 비밀번호), 익명 거절, 클라이언트 인증서 모두 믿기 */
	public record Security(Map<String, String> users, boolean requireLogin, boolean trustAllClients) {

		public static final Security OPEN = new Security(Map.of(), false, true);

		public Security {
			users = users != null ? Map.copyOf(users) : Map.of();
		}
	}

	/** 태그 하나 */
	public record TagSpec(String path, TagType type, Object value, Simulation simulation, String description) {
	}

	/** 값 흉내 — kind: RAMP·SINE·TOGGLE·RANDOM, periodMs 마다, min~max(숫자) */
	public record Simulation(String kind, Long periodMs, Double min, Double max) {
	}

	/** 태그 상태(REST 응답) */
	public record TagView(String path, String nodeId, String expandedNodeId, TagType type, Object value, Simulation simulation, String description) {
	}

	private final OpcUaServer server;

	private final Namespace namespace;

	private final Map<String, Tag> tags = new ConcurrentHashMap<>();

	private final ScheduledExecutorService ticker;

	private final String endpointUrl;

	private static final class Tag {

		final TagSpec spec;

		final UaVariableNode node;

		volatile long lastTick;

		long step;

		Tag(TagSpec spec, UaVariableNode node) {
			this.spec = spec;
			this.node = node;
		}
	}

	public VirtualOpcUaServer(String bindAddress, String hostname, int port, String path, List<TagSpec> initial) throws Exception {
		this(bindAddress, hostname, port, path, initial, Security.OPEN);
	}

	public VirtualOpcUaServer(String bindAddress, String hostname, int port, String path, List<TagSpec> initial, Security security) throws Exception {
		String p = path == null || path.isBlank() ? "" : (path.startsWith("/") ? path : "/" + path);
		this.endpointUrl = "opc.tcp://" + hostname + ":" + port + p;

		// 서버 인증서(자체 서명, 메모리)와 클라이언트 인증서 검사
		MemoryTrustListManager trustList = new MemoryTrustListManager();
		CertificateValidator clientValidator = security.trustAllClients()
			? new CertificateValidator.InsecureCertificateValidator()
			: new DefaultClientCertificateValidator(trustList, new MemoryCertificateQuarantine());
		DefaultApplicationGroup group = DefaultApplicationGroup.createAndInitialize(trustList, new MemoryCertificateStore(),
			new RsaSha256CertificateFactory() {
				@Override
				protected X509Certificate[] createRsaSha256CertificateChain(KeyPair keyPair) throws Exception {
					return new X509Certificate[] { new SelfSignedCertificateBuilder(keyPair)
						.setCommonName("WOW-TC virtual OPC UA").setOrganization("wow").setApplicationUri(APPLICATION_URI)
						.addDnsName("localhost").addDnsName(hostname).addIpAddress("127.0.0.1").build() };
				}
			}, clientValidator);
		X509Certificate certificate = group.getCertificateChain(group.getSupportedCertificateTypeIds().get(0)).orElseThrow()[0];

		Set<EndpointConfig> endpoints = new java.util.LinkedHashSet<>();
		EndpointConfig.Builder base = EndpointConfig.newBuilder()
			.setBindAddress(bindAddress).setBindPort(port).setHostname(hostname).setPath(p)
			.setTransportProfile(TransportProfile.TCP_UASC_UABINARY).setCertificate(certificate);
		if (!security.requireLogin()) {
			base.addTokenPolicy(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS);
		}
		base.addTokenPolicy(OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME);
		endpoints.add(base.copy().setSecurityPolicy(SecurityPolicy.None).setSecurityMode(MessageSecurityMode.None).build());
		endpoints.add(base.copy().setSecurityPolicy(SecurityPolicy.Basic256Sha256).setSecurityMode(MessageSecurityMode.Sign).build());
		endpoints.add(base.copy().setSecurityPolicy(SecurityPolicy.Basic256Sha256).setSecurityMode(MessageSecurityMode.SignAndEncrypt).build());

		List<IdentityValidator> identities = new ArrayList<>();
		if (!security.requireLogin()) {
			identities.add(AnonymousIdentityValidator.INSTANCE);
		}
		identities.add(new UsernameIdentityValidator(c -> c.getPassword() != null && c.getPassword().equals(security.users().get(c.getUsername()))));

		OpcUaServerConfig config = OpcUaServerConfig.builder()
			.setApplicationName(LocalizedText.english("WOW-TC virtual OPC UA"))
			.setApplicationUri(APPLICATION_URI)
			.setProductUri("urn:wow:tc:vplc-opcua")
			.setEndpoints(endpoints)
			.setIdentityValidator(new CompositeValidator(identities))
			.setCertificateManager(new DefaultCertificateManager(new MemoryCertificateQuarantine(), group))
			.build();
		server = new OpcUaServer(config, profile -> new OpcTcpServerTransport(OpcTcpServerTransportConfig.newBuilder().build()));
		// 먼저 등록한 네임스페이스가 번호 2 — 기본 네임스페이스는 3
		new ExtraNamespace(server).startup();
		namespace = new Namespace(server);
		namespace.startup();
		server.startup().get(15, TimeUnit.SECONDS);
		for (TagSpec spec : initial) {
			add(spec);
		}
		ticker = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "vplc-opcua-sim");
			t.setDaemon(true);
			return t;
		});
		ticker.scheduleAtFixedRate(this::tick, 100, 100, TimeUnit.MILLISECONDS);
		log.info("가상 OPC UA 서버: {} (태그 {}개, 네임스페이스 {})", endpointUrl, tags.size(), namespaceIndex());
	}

	public String endpointUrl() {
		return endpointUrl;
	}

	public int namespaceIndex() {
		return namespace.getNamespaceIndex().intValue();
	}

	public String nodeId(String path) {
		return "ns=" + namespaceIndex() + ";s=" + path;
	}

	/** 네임스페이스 URI 로 쓴 NodeId — 번호가 바뀌어도 같은 노드 */
	public String expandedNodeId(String path) {
		return "nsu=" + NAMESPACE_URI + ";s=" + path;
	}

	/** 태그를 더한다 — 경로의 앞부분은 폴더(없으면 만든다) */
	public synchronized TagView add(TagSpec spec) {
		if (spec.path() == null || !spec.path().matches("[A-Za-z_][A-Za-z0-9_\\-]*(\\.[A-Za-z_][A-Za-z0-9_\\-]*)*") || spec.path().length() > 200) {
			throw new IllegalArgumentException("태그 경로는 영문자·_ 로 시작하는 이름을 . 으로 이은 것입니다(예 Line1.Temp): " + spec.path());
		}
		if (spec.type() == null) {
			throw new IllegalArgumentException("형(type)이 없습니다: " + spec.path());
		}
		if (tags.containsKey(spec.path())) {
			throw new IllegalStateException("이미 있는 태그입니다: " + spec.path());
		}
		Object initial = spec.type().of(spec.value() != null ? spec.value() : spec.type() == TagType.BOOL ? false
			: spec.type() == TagType.STRING ? "" : 0);
		UaVariableNode node = namespace.variable(spec, initial);
		tags.put(spec.path(), new Tag(spec, node));
		return view(tags.get(spec.path()));
	}

	public List<TagView> list() {
		List<TagView> out = new ArrayList<>();
		tags.values().stream().sorted(java.util.Comparator.comparing(t -> t.spec.path())).forEach(t -> out.add(view(t)));
		return out;
	}

	public TagView get(String path) {
		return view(require(path));
	}

	/** 값을 바꾼다(흉내 중인 태그도 바꾸지만 다음 주기에 다시 흉내 값) */
	public TagView set(String path, Object value) {
		Tag t = require(path);
		t.node.setValue(new DataValue(new Variant(t.spec.type().of(value))));
		return view(t);
	}

	private Tag require(String path) {
		Tag t = tags.get(path);
		if (t == null) {
			throw new java.util.NoSuchElementException("없는 태그입니다: " + path);
		}
		return t;
	}

	private TagView view(Tag t) {
		DataValue dv = t.node.getValue();
		Object v = dv != null && dv.getValue() != null ? TagType.plain(dv.getValue().getValue()) : null;
		return new TagView(t.spec.path(), nodeId(t.spec.path()), expandedNodeId(t.spec.path()), t.spec.type(), v, t.spec.simulation(),
			t.spec.description());
	}

	/** 흉내 — 100ms 마다 불려 주기가 된 태그만 바꾼다 */
	private void tick() {
		long now = System.currentTimeMillis();
		for (Tag t : tags.values()) {
			Simulation s = t.spec.simulation();
			if (s == null || s.kind() == null) {
				continue;
			}
			long period = s.periodMs() != null && s.periodMs() > 0 ? s.periodMs() : 1000;
			if (now - t.lastTick < period) {
				continue;
			}
			t.lastTick = now;
			t.step++;
			try {
				t.node.setValue(new DataValue(new Variant(t.spec.type().of(simulate(t, s)))));
			} catch (RuntimeException e) {
				log.debug("흉내 값 실패: {} — {}", t.spec.path(), e.getMessage());
			}
		}
	}

	private static Object simulate(Tag t, Simulation s) {
		double min = s.min() != null ? s.min() : 0;
		double max = s.max() != null ? s.max() : 100;
		return switch (s.kind().toUpperCase()) {
			case "TOGGLE" -> t.step % 2 == 1;
			case "RANDOM" -> t.spec.type() == TagType.BOOL ? ThreadLocalRandom.current().nextBoolean()
				: min + ThreadLocalRandom.current().nextDouble() * (max - min);
			case "SINE" -> min + (max - min) * (Math.sin(t.step / 10.0 * 2 * Math.PI) + 1) / 2;
			default -> min + (t.step % (long) Math.max(1, max - min + 1));   // RAMP — 정수 걸음
		};
	}

	@Override
	public void close() throws Exception {
		ticker.shutdownNow();
		server.shutdown().get(10, TimeUnit.SECONDS);
	}

	/** 비어 있는 네임스페이스 — 기본 네임스페이스 번호를 2 가 아니게 하려고 먼저 등록한다 */
	static final class ExtraNamespace extends ManagedNamespaceWithLifecycle {

		ExtraNamespace(OpcUaServer server) {
			super(server, EXTRA_NAMESPACE_URI);
		}

		@Override
		public void onDataItemsCreated(List<DataItem> items) {
		}

		@Override
		public void onDataItemsModified(List<DataItem> items) {
		}

		@Override
		public void onDataItemsDeleted(List<DataItem> items) {
		}

		@Override
		public void onMonitoringModeChanged(List<MonitoredItem> items) {
		}
	}

	/** 네임스페이스 — 폴더·변수 노드, 구독은 SubscriptionModel(샘플링) */
	static final class Namespace extends ManagedNamespaceWithLifecycle {

		private final SubscriptionModel model;

		private final Map<String, UaFolderNode> folders = new ConcurrentHashMap<>();

		Namespace(OpcUaServer server) {
			super(server, NAMESPACE_URI);
			model = new SubscriptionModel(server, this);
			getLifecycleManager().addLifecycle(model);
		}

		/** 경로 앞부분 폴더를 만들고(없으면) 변수를 더한다 */
		UaVariableNode variable(TagSpec spec, Object initial) {
			String[] parts = spec.path().split("\\.");
			UaFolderNode parent = null;
			StringBuilder p = new StringBuilder();
			for (int i = 0; i < parts.length - 1; i++) {
				if (i > 0) {
					p.append('.');
				}
				p.append(parts[i]);
				parent = folder(p.toString(), parts[i], parent);
			}
			String name = parts[parts.length - 1];
			UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
				.setNodeId(new NodeId(getNamespaceIndex(), spec.path()))
				.setBrowseName(new QualifiedName(getNamespaceIndex(), name))
				.setDisplayName(LocalizedText.english(name))
				.setDescription(LocalizedText.english(spec.description() != null ? spec.description() : ""))
				.setDataType(spec.type().dataType())
				.setTypeDefinition(new NodeId(0, 63))
				.setAccessLevel(AccessLevel.READ_WRITE)
				.setUserAccessLevel(AccessLevel.READ_WRITE)
				.build();
			node.setValue(new DataValue(new Variant(initial)));
			getNodeManager().addNode(node);
			if (parent != null) {
				parent.addOrganizes(node);
			} else {
				node.addReference(new Reference(node.getNodeId(), new NodeId(0, 35), new NodeId(0, 85).expanded(), false));
			}
			return node;
		}

		private UaFolderNode folder(String path, String name, UaFolderNode parent) {
			return folders.computeIfAbsent(path, k -> {
				UaFolderNode f = new UaFolderNode(getNodeContext(), new NodeId(getNamespaceIndex(), path),
					new QualifiedName(getNamespaceIndex(), name), LocalizedText.english(name));
				getNodeManager().addNode(f);
				if (parent != null) {
					parent.addOrganizes(f);
				} else {
					// Objects --Organizes--> 폴더
					f.addReference(new Reference(f.getNodeId(), new NodeId(0, 35), new NodeId(0, 85).expanded(), false));
				}
				return f;
			});
		}

		@Override
		public void onDataItemsCreated(List<DataItem> items) {
			model.onDataItemsCreated(items);
		}

		@Override
		public void onDataItemsModified(List<DataItem> items) {
			model.onDataItemsModified(items);
		}

		@Override
		public void onDataItemsDeleted(List<DataItem> items) {
			model.onDataItemsDeleted(items);
		}

		@Override
		public void onMonitoringModeChanged(List<MonitoredItem> items) {
			model.onMonitoringModeChanged(items);
		}
	}
}
