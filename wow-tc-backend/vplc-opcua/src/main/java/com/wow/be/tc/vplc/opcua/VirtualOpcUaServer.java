package com.wow.be.tc.vplc.opcua;

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
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransport;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransportConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * 가상 OPC UA 서버(Eclipse Milo) — SecurityPolicy None·익명. 네임스페이스 {@value #NAMESPACE_URI} 에 태그 경로대로 폴더와 변수를 만든다.
 * 태그 {@code Line1.Temp} 의 NodeId 는 {@code ns=<네임스페이스>;s=Line1.Temp}, 폴더 {@code Line1} 은 Objects 아래.
 *
 * <p>흉내(simulation)가 있는 태그는 주기마다 값을 바꾼다 — ramp(최소→최대 반복)·sine·toggle(BOOL)·random. tc 구독 시험용.
 */
@Slf4j
public final class VirtualOpcUaServer implements AutoCloseable {

	public static final String NAMESPACE_URI = "urn:wow:tc:vplc-opcua";

	/** 태그 하나 */
	public record TagSpec(String path, TagType type, Object value, Simulation simulation, String description) {
	}

	/** 값 흉내 — kind: RAMP·SINE·TOGGLE·RANDOM, periodMs 마다, min~max(숫자) */
	public record Simulation(String kind, Long periodMs, Double min, Double max) {
	}

	/** 태그 상태(REST 응답) */
	public record TagView(String path, String nodeId, TagType type, Object value, Simulation simulation, String description) {
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
		String p = path == null || path.isBlank() ? "" : (path.startsWith("/") ? path : "/" + path);
		this.endpointUrl = "opc.tcp://" + hostname + ":" + port + p;
		EndpointConfig endpoint = EndpointConfig.newBuilder()
			.setBindAddress(bindAddress).setBindPort(port).setHostname(hostname).setPath(p)
			.setSecurityPolicy(SecurityPolicy.None).setSecurityMode(MessageSecurityMode.None)
			.setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
			.addTokenPolicy(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS)
			.build();
		OpcUaServerConfig config = OpcUaServerConfig.builder()
			.setApplicationName(LocalizedText.english("WOW-TC virtual OPC UA"))
			.setApplicationUri("urn:wow:tc:vplc-opcua:server")
			.setProductUri("urn:wow:tc:vplc-opcua")
			.setEndpoints(Set.of(endpoint))
			.setIdentityValidator(AnonymousIdentityValidator.INSTANCE)
			.setCertificateManager(new DefaultCertificateManager(new MemoryCertificateQuarantine(), List.of()))
			.build();
		server = new OpcUaServer(config, profile -> new OpcTcpServerTransport(OpcTcpServerTransportConfig.newBuilder().build()));
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
		return new TagView(t.spec.path(), nodeId(t.spec.path()), t.spec.type(), v, t.spec.simulation(), t.spec.description());
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
