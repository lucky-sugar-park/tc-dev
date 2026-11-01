package com.wow.be.tc.plc.protocol.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.delivery.ResultDeliveryService;
import com.wow.be.tc.dispatch.TcMessageRequest;
import com.wow.be.tc.dispatch.TcMessageResponse;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.InterfaceService;
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditService;
import com.wow.be.tc.plc.connector.PlcAdapterManager;
import com.wow.be.tc.plc.connector.opcua.OpcUaAdapter;
import com.wow.be.tc.plc.connector.opcua.OpcUaSubscriptionService;
import com.wow.be.tc.plc.connector.opcua.OpcUaTools;
import com.wow.be.tc.plc.management.PlcConnectorInfo;
import com.wow.be.tc.plc.protocol.PlannedRequest;
import com.wow.be.tc.plc.protocol.PlcOperation;
import com.wow.be.tc.plc.protocol.PlcResult;
import com.wow.be.tc.plc.protocol.PointType;

/** OPC UA 드라이버·어댑터·찾아보기·구독 — 내장 Milo 서버로 (docs/opcua-design.md) */
class OpcUaDriverTest {

	static TestOpcUaServer server;

	static Map<String, Object> settings;

	final OpcUaDriver driver = new OpcUaDriver();

	@BeforeAll
	static void start() throws Exception {
		server = new TestOpcUaServer();
		settings = new OpcUaDriver().normalizeSettings(Map.of("endpointPath", "/test", "publishingIntervalMs", 100, "samplingIntervalMs", 50), null);
	}

	@AfterAll
	static void stop() throws Exception {
		server.close();
	}

	private static PointInfo point(String name, String address, PointType type) {
		PointInfo p = new PointInfo();
		p.setName(name);
		p.setAddress(address);
		p.setType(type);
		return p;
	}

	private static OpcUaClient client() throws Exception {
		OpcUaClient c = OpcUaSessions.create(OpcUaSessions.endpointUrl("127.0.0.1", server.port, settings), settings);
		c.connect();
		return c;
	}

	private PlcConnectorInfo connector() {
		PlcConnectorInfo info = new PlcConnectorInfo();
		info.setName("ua-test");
		info.setHostIp("127.0.0.1");
		info.setPorts(List.of(server.port));
		info.setProtocol(OpcUaDriver.PROTOCOL);
		info.setSettings(settings);
		return info;
	}

	@Test
	void 설정과_점_검증() {
		assertThat(driver.normalizeSettings(Map.of(), null)).containsEntry("requestTimeoutMs", 5000).containsEntry("maxNodesPerRequest", 500);
		assertThatThrownBy(() -> driver.normalizeSettings(Map.of("frame", "3E"), null)).hasMessageContaining("없는 항목");
		driver.validatePoint(point("t", "ns=2;s=Line1.Temp", PointType.FLOAT64), InterfaceType.READ);
		driver.validatePoint(point("s", "i=2258", PointType.INT64), InterfaceType.READ);
		assertThatThrownBy(() -> driver.validatePoint(point("t", "D100", PointType.INT16), InterfaceType.READ)).hasMessageContaining("NodeId");
		PointInfo ordered = point("t", "ns=2;i=1", PointType.INT32);
		ordered.setByteOrder(com.wow.be.tc.plc.protocol.ByteOrder.LITTLE);
		assertThatThrownBy(() -> driver.validatePoint(ordered, InterfaceType.READ)).hasMessageContaining("순서");
		assertThat(driver.normalizeInterfaceSettings(Map.of("accessMode", "subscribe"))).containsEntry("accessMode", "SUBSCRIBE");
	}

	@Test
	void 읽기_쓰기_상태() throws Exception {
		OpcUaClient c = client();
		try {
			List<PointInfo> points = List.of(point("temp", server.node("Temp"), PointType.FLOAT64), point("count", server.node("Count"), PointType.INT32),
				point("speed", server.node("Speed"), PointType.UINT16), point("run", server.node("Running"), PointType.BOOL),
				point("recipe", server.node("Recipe"), PointType.STRING));
			PlcResult r = OpcUaDriver.run(c, new PlcOperation(OpcUaDriver.PROTOCOL, settings, PlcOperation.Kind.READ, points, Map.of(), "r"));
			assertThat(r.status().ok()).isTrue();
			assertThat(r.values()).containsEntry("temp", 25.5).containsEntry("count", 7).containsEntry("speed", 1500)
				.containsEntry("run", true).containsEntry("recipe", "R-01");

			PlcResult w = OpcUaDriver.run(c, new PlcOperation(OpcUaDriver.PROTOCOL, settings, PlcOperation.Kind.WRITE,
				List.of(point("count", server.node("Count"), PointType.INT32), point("recipe", server.node("Recipe"), PointType.STRING)),
				Map.of("count", 42, "recipe", "R-02"), "w"));
			assertThat(w.status().ok()).isTrue();
			r = OpcUaDriver.run(c, new PlcOperation(OpcUaDriver.PROTOCOL, settings, PlcOperation.Kind.READ, points, Map.of(), "r2"));
			assertThat(r.values()).containsEntry("count", 42).containsEntry("recipe", "R-02");

			// 없는 노드 — 좋은 노드 값은 싣고 상태는 Bad_NodeIdUnknown
			PlcResult bad = OpcUaDriver.run(c, new PlcOperation(OpcUaDriver.PROTOCOL, settings, PlcOperation.Kind.READ,
				List.of(point("temp", server.node("Temp"), PointType.FLOAT64), point("x", server.node("Nope"), PointType.INT32)), Map.of(), "b"));
			assertThat(bad.status().ok()).isFalse();
			assertThat(bad.status().text()).contains("Bad_NodeIdUnknown").contains("x");
			assertThat(bad.values()).containsKey("temp").doesNotContainKey("x");

			// 형 범위 밖 쓰기 → 400
			assertThatThrownBy(() -> OpcUaDriver.run(c, new PlcOperation(OpcUaDriver.PROTOCOL, settings, PlcOperation.Kind.WRITE,
				List.of(point("speed", server.node("Speed"), PointType.UINT16)), Map.of("speed", 70000), "w2")))
				.isInstanceOf(ToolControlException.class).hasMessageContaining("범위");
		} finally {
			c.disconnect();
		}
	}

	@Test
	void 미리보기는_서비스_단위() {
		List<PlannedRequest> plan = driver.plan(new PlcOperation(OpcUaDriver.PROTOCOL, Map.of("maxNodesPerRequest", 2), PlcOperation.Kind.READ,
			List.of(point("a", "ns=2;s=A", PointType.INT32), point("b", "ns=2;s=B", PointType.INT32), point("c", "ns=2;s=C", PointType.INT32)),
			Map.of(), "p"));
		assertThat(plan).hasSize(2);
		assertThat(plan.get(0).function()).isEqualTo("Read(Value)");
		assertThat(plan.get(0).target()).isEqualTo("ns=2;s=A 외 1");
		assertThat(plan.get(0).count()).isEqualTo(2);
		assertThat(plan.get(1).points()).containsExactly("c");
	}

	@Test
	void 찾아보기와_연결_시험() throws Exception {
		Map<String, String> test = OpcUaTools.testConnection(connector(), settings);
		assertThat(test).containsEntry("result", "CONNECTION_TEST_OK");
		List<OpcUaTools.BrowseNode> top = OpcUaTools.browse(connector(), settings, null);
		OpcUaTools.BrowseNode line = top.stream().filter(n -> "Line1".equals(n.browseName())).findFirst().orElseThrow();
		assertThat(line.hasChildren()).isTrue();
		List<OpcUaTools.BrowseNode> vars = OpcUaTools.browse(connector(), settings, line.nodeId());
		assertThat(vars).extracting(OpcUaTools.BrowseNode::browseName).contains("Temp", "Count", "Running", "Recipe");
		OpcUaTools.BrowseNode temp = vars.stream().filter(n -> "Temp".equals(n.browseName())).findFirst().orElseThrow();
		assertThat(temp.type()).isEqualTo(PointType.FLOAT64);
		assertThat(temp.nodeId()).isEqualTo(server.expanded("Temp"));   // 찾아보기는 nsu= 로 돌려준다(O17)
		assertThat(vars.stream().filter(n -> "Running".equals(n.browseName())).findFirst().orElseThrow().type()).isEqualTo(PointType.BOOL);
	}

	/** 전달된 응답 중 key=value 인 것이 올 때까지(5초) */
	private static TcMessageResponse waitFor(ResultDeliveryService delivery, String key, Object value) throws InterruptedException {
		long until = System.currentTimeMillis() + 5000;
		while (System.currentTimeMillis() < until) {
			ArgumentCaptor<TcMessageResponse> captor = ArgumentCaptor.forClass(TcMessageResponse.class);
			try {
				verify(delivery, org.mockito.Mockito.atLeastOnce()).deliver(any(), any(TcMessageRequest.class), captor.capture());
				for (TcMessageResponse r : captor.getAllValues()) {
					if (value.equals(r.getData().get(key))) {
						return r;
					}
				}
			} catch (AssertionError e) {
				// 아직 없음
			}
			Thread.sleep(100);
		}
		throw new AssertionError("전달 안 됨: " + key + "=" + value);
	}

	@Test
	void 어댑터_실행과_구독_전달() throws Exception {
		OpcUaAdapter adapter = new OpcUaAdapter();
		Properties props = new Properties();
		props.put("hostIp", "127.0.0.1");
		props.put("targetPorts", List.of(server.port));
		props.put("settings", settings);
		adapter.ready(9001, "ua-test", props);
		PlcAdapterManager.getPlcManager().set("ua-test", adapter);
		try {
			assertThat(adapter.status().activeChannels()).isEqualTo(1);
			PlcResult r = adapter.execute(new PlcOperation(OpcUaDriver.PROTOCOL, settings, PlcOperation.Kind.READ,
				List.of(point("raw", server.node("Raw"), PointType.INT16)), Map.of(), "a"));
			assertThat(r.values()).containsEntry("raw", 253);

			InterfaceInfo iface = new InterfaceInfo();
			iface.setName("ua.line1.sub");
			iface.setPlcName("ua-test");
			iface.setInterfaceType(InterfaceType.READ);
			iface.setUse(true);
			PointInfo raw = point("raw", server.node("Raw"), PointType.INT16);
			raw.setScale(0.1);
			iface.setPoints(List.of(raw, point("run", server.node("Running"), PointType.BOOL)));
			iface.setSettings(Map.of("accessMode", "SUBSCRIBE"));
			InterfaceService interfaces = mock(InterfaceService.class);
			when(interfaces.searchInterface()).thenReturn(List.of(iface));
			ResultDeliveryService delivery = mock(ResultDeliveryService.class);
			when(delivery.targetsOf(any(), any(Boolean.class))).thenReturn(List.of());
			ExchangedMessageAuditService audit = mock(ExchangedMessageAuditService.class);
			OpcUaSubscriptionService service = new OpcUaSubscriptionService(interfaces, delivery, audit);
			service.reconcile();
			// 처음 알림은 지금 값(253 × 0.1)
			assertThat(waitFor(delivery, "raw", 25.3).getData()).containsEntry("run", true);
			server.set("Raw", (short) 300);
			TcMessageResponse changed = waitFor(delivery, "raw", 30.0);
			assertThat(changed.getOpcode()).isEqualTo("ua.line1.sub");
			assertThat(changed.getPlcStatus().ok()).isTrue();
			verify(audit, timeout(1000).atLeastOnce()).writeAudit(any());
			// 다시 맞춰도(정의가 같으면) 구독을 새로 만들지 않는다 — 값이 바뀔 때만 알린다
			service.reconcile();
		} finally {
			PlcAdapterManager.getPlcManager().remove("ua-test");
			adapter.stop();
		}
	}
}
