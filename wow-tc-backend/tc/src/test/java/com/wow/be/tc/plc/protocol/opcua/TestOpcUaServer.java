package com.wow.be.tc.plc.protocol.opcua;

import java.net.ServerSocket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.milo.opcua.sdk.core.AccessLevel;
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

/**
 * 시험용 OPC UA 서버(Milo) — None·Basic256Sha256(Sign·SignAndEncrypt), 익명·사용자 이름({@value #USER}/{@value #PASSWORD}).
 * 서버 인증서는 자체 서명(서버마다 다르다 — TOFU 시험). 네임스페이스 {@value #URI} 에 폴더 {@code Line1} 과 변수들.
 * NodeId 는 {@code ns=<index>;s=Line1.<이름>} 또는 {@code nsu=}{@value #URI}{@code ;s=Line1.<이름>}.
 */
final class TestOpcUaServer implements AutoCloseable {

	static final String URI = "urn:wow:tc:test";

	static final String USER = "tester";

	static final String PASSWORD = "pw-123";

	final int port;

	final OpcUaServer server;

	final Namespace namespace;

	TestOpcUaServer() throws Exception {
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		org.eclipse.milo.opcua.stack.core.security.MemoryTrustListManager trust = new org.eclipse.milo.opcua.stack.core.security.MemoryTrustListManager();
		org.eclipse.milo.opcua.stack.core.security.DefaultApplicationGroup group =
			org.eclipse.milo.opcua.stack.core.security.DefaultApplicationGroup.createAndInitialize(trust,
				new org.eclipse.milo.opcua.stack.core.security.MemoryCertificateStore(),
				new org.eclipse.milo.opcua.stack.core.security.RsaSha256CertificateFactory() {
					@Override
					protected java.security.cert.X509Certificate[] createRsaSha256CertificateChain(java.security.KeyPair kp) throws Exception {
						return new java.security.cert.X509Certificate[] { new org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder(kp)
							.setCommonName("tc test server").setApplicationUri("urn:wow:tc:test-server").addDnsName("localhost")
							.addIpAddress("127.0.0.1").build() };
					}
				}, new org.eclipse.milo.opcua.stack.core.security.CertificateValidator.InsecureCertificateValidator());
		java.security.cert.X509Certificate cert = group.getCertificateChain(group.getSupportedCertificateTypeIds().get(0)).orElseThrow()[0];
		EndpointConfig.Builder base = EndpointConfig.newBuilder()
			.setBindAddress("127.0.0.1").setBindPort(port).setHostname("127.0.0.1").setPath("/test")
			.setTransportProfile(TransportProfile.TCP_UASC_UABINARY).setCertificate(cert)
			.addTokenPolicy(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS)
			.addTokenPolicy(OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME);
		Set<EndpointConfig> endpoints = Set.of(
			base.copy().setSecurityPolicy(SecurityPolicy.None).setSecurityMode(MessageSecurityMode.None).build(),
			base.copy().setSecurityPolicy(SecurityPolicy.Basic256Sha256).setSecurityMode(MessageSecurityMode.SignAndEncrypt).build());
		OpcUaServerConfig config = OpcUaServerConfig.builder()
			.setApplicationName(LocalizedText.english("tc test server"))
			.setApplicationUri("urn:wow:tc:test-server")
			.setProductUri("urn:wow:tc:test-server")
			.setEndpoints(endpoints)
			.setIdentityValidator(new org.eclipse.milo.opcua.sdk.server.identity.CompositeValidator(AnonymousIdentityValidator.INSTANCE,
				new org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator(
					c -> USER.equals(c.getUsername()) && PASSWORD.equals(c.getPassword()))))
			.setCertificateManager(new DefaultCertificateManager(new MemoryCertificateQuarantine(), group))
			.build();
		server = new OpcUaServer(config, profile -> new OpcTcpServerTransport(OpcTcpServerTransportConfig.newBuilder().build()));
		namespace = new Namespace(server);
		namespace.startup();
		server.startup().get(10, TimeUnit.SECONDS);
	}

	int ns() {
		return namespace.getNamespaceIndex().intValue();
	}

	String node(String name) {
		return "ns=" + ns() + ";s=Line1." + name;
	}

	String expanded(String name) {
		return "nsu=" + URI + ";s=Line1." + name;
	}

	/** 변수 값을 바꾼다(구독 시험) */
	void set(String name, Object value) {
		UaVariableNode v = (UaVariableNode) namespace.getNodeManager().getNode(new NodeId(ns(), "Line1." + name)).orElseThrow();
		v.setValue(new DataValue(new Variant(value)));
	}

	@Override
	public void close() throws Exception {
		server.shutdown().get(10, TimeUnit.SECONDS);
	}

	static final class Namespace extends ManagedNamespaceWithLifecycle {

		private final SubscriptionModel model;

		Namespace(OpcUaServer server) {
			super(server, URI);
			model = new SubscriptionModel(server, this);
			getLifecycleManager().addLifecycle(model);
			getLifecycleManager().addStartupTask(this::addNodes);
		}

		private void addNodes() {
			UaFolderNode folder = new UaFolderNode(getNodeContext(), new NodeId(getNamespaceIndex(), "Line1"),
				new QualifiedName(getNamespaceIndex(), "Line1"), LocalizedText.english("Line1"));
			getNodeManager().addNode(folder);
			folder.addReference(new org.eclipse.milo.opcua.sdk.core.Reference(folder.getNodeId(),
				new NodeId(0, 35), new NodeId(0, 85).expanded(), false));   // Objects --Organizes--> Line1
			variable(folder, "Temp", new NodeId(0, 11), 25.5);                 // Double
			variable(folder, "Count", new NodeId(0, 6), 7);                    // Int32
			variable(folder, "Speed", new NodeId(0, 5), org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort(1500));
			variable(folder, "Running", new NodeId(0, 1), true);
			variable(folder, "Recipe", new NodeId(0, 12), "R-01");
			variable(folder, "Raw", new NodeId(0, 4), (short) 253);           // Int16 — 배율 시험
		}

		private void variable(UaFolderNode folder, String name, NodeId dataType, Object value) {
			UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
				.setNodeId(new NodeId(getNamespaceIndex(), "Line1." + name))
				.setBrowseName(new QualifiedName(getNamespaceIndex(), name))
				.setDisplayName(LocalizedText.english(name))
				.setDataType(dataType)
				.setTypeDefinition(new NodeId(0, 63))
				.setAccessLevel(AccessLevel.READ_WRITE)
				.setUserAccessLevel(AccessLevel.READ_WRITE)
				.build();
			node.setValue(new DataValue(new Variant(value)));
			getNodeManager().addNode(node);
			folder.addOrganizes(node);
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
