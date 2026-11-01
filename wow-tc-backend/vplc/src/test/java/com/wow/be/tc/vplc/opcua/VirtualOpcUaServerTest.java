package com.wow.be.tc.vplc.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.util.List;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.junit.jupiter.api.Test;

import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer.Simulation;
import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer.TagSpec;

/** 가상 OPC UA 서버 — 클라이언트로 읽기·쓰기, 폴더 찾아보기, 흉내 값, 태그 검증 */
class VirtualOpcUaServerTest {

	@Test
	void 읽기_쓰기_찾아보기_흉내() throws Exception {
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		try (VirtualOpcUaServer server = new VirtualOpcUaServer("127.0.0.1", "127.0.0.1", port, "/vplc", List.of(
				new TagSpec("Line1.Temp", TagType.FLOAT64, 25.5, null, "온도"),
				new TagSpec("Line1.Speed", TagType.UINT16, 1500, null, null),
				new TagSpec("Line1.Counter", TagType.INT32, 0, new Simulation("RAMP", 100L, 0.0, 1000.0), null),
				new TagSpec("Top", TagType.BOOL, true, null, null)))) {
			OpcUaClient client = OpcUaClient.create(server.endpointUrl());
			client.connect();
			try {
				DataValue temp = client.readValue(0, TimestampsToReturn.Neither, NodeId.parse(server.nodeId("Line1.Temp")));
				assertThat(temp.getValue().getValue()).isEqualTo(25.5);
				client.writeValues(List.of(NodeId.parse(server.nodeId("Line1.Temp"))), List.of(DataValue.valueOnly(new Variant(30.0))));
				assertThat(server.get("Line1.Temp").value()).isEqualTo(30.0);
				// REST 로 바꾼 값을 클라이언트가 읽는다(부호 없는 형은 숫자로 보인다)
				server.set("Line1.Speed", 1600);
				assertThat(server.get("Line1.Speed").value()).isEqualTo(1600);
				// Objects → Line1(폴더), Top(변수)
				var top = client.getAddressSpace().browse(new NodeId(0, 85));
				assertThat(top).extracting(r -> r.getBrowseName().getName()).contains("Line1", "Top");
				var line1 = client.getAddressSpace().browse(NodeId.parse("ns=" + server.namespaceIndex() + ";s=Line1"));
				assertThat(line1).extracting(r -> r.getBrowseName().getName()).contains("Temp", "Speed", "Counter");
				// 흉내 — 100ms 마다 1씩
				Object first = server.get("Line1.Counter").value();
				Thread.sleep(500);
				assertThat(server.get("Line1.Counter").value()).isNotEqualTo(first);
				assertThat(server.sessionCount()).isEqualTo(1);
				// 지우면 목록·찾아보기에서 빠지고 같은 경로로 다시 더할 수 있다
				server.remove("Line1.Speed");
				assertThat(server.list()).extracting(VirtualOpcUaServer.TagView::path).doesNotContain("Line1.Speed");
				assertThat(client.getAddressSpace().browse(NodeId.parse("ns=" + server.namespaceIndex() + ";s=Line1")))
					.extracting(r -> r.getBrowseName().getName()).doesNotContain("Speed");
				server.add(new TagSpec("Line1.Speed", TagType.UINT16, 7, null, null));
				assertThat(server.get("Line1.Speed").value()).isEqualTo(7);
			} finally {
				client.disconnect();
			}
			assertThatThrownBy(() -> server.remove("Nope")).hasMessageContaining("없는");
			assertThatThrownBy(() -> server.add(new TagSpec("1bad", TagType.INT16, 0, null, null))).hasMessageContaining("태그 경로");
			assertThatThrownBy(() -> server.add(new TagSpec("Top", TagType.BOOL, false, null, null))).hasMessageContaining("이미");
			assertThatThrownBy(() -> server.set("Line1.Speed", 70000)).hasMessageContaining("범위");
		}
	}
}
