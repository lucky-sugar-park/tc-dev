package com.wow.be.tc.vplc.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.junit.jupiter.api.Test;

import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.Types.Protocol;

import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer.TagSpec;

/** 가상 OPC UA 서버(vplc 안) — 시작·정지, 일시정지 동안 값 읽기 무응답, 태그 더하기·지우기·값, 설정 기본값 */
class OpcUaVirtualPlcTest {

	@Test
	void 시작_일시정지_태그_정지() throws Exception {
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		VirtualPlcInfo info = new VirtualPlcInfo();
		info.setId("ua-1");
		info.setName("ua-1");
		info.setProtocol(Protocol.OPC_UA);
		info.setIpAddress("127.0.0.1");
		info.setStartPort(port);
		new OpcUaProvider().normalize(info);
		assertThat(info.getSettings()).containsEntry("path", "/vplc").containsEntry("requireLogin", false);
		assertThat(info.getMemoryTypeList()).containsExactly("TAG");

		OpcUaVirtualPlc d = new OpcUaVirtualPlc(info, List.of(new TagSpec("Line1.Temp", TagType.FLOAT64, 25.5, null, null)), Map.of());
		assertThat(d.tags()).extracting(VirtualOpcUaServer.TagView::value).containsExactly(25.5);
		assertThatThrownBy(() -> d.setValue("Line1.Temp", 1.0)).hasMessageContaining("실행 중이 아니라");
		d.start();
		try {
			assertThat(d.endpointUrl()).isEqualTo("opc.tcp://127.0.0.1:" + port + "/vplc");
			OpcUaClient client = OpcUaClient.create(d.endpointUrl());
			client.connect();
			try {
				NodeId temp = NodeId.parse("ns=" + d.namespaceIndex() + ";s=Line1.Temp");
				assertThat(client.readValue(0, TimestampsToReturn.Neither, temp).getValue().getValue()).isEqualTo(25.5);
				assertThat(d.sessionCount()).isEqualTo(1);

				// 일시정지 — 읽기가 돌아오지 않는다, 재개하면 돌아온다
				d.pause();
				assertThat(d.getStatus()).isEqualTo(PlcStatus.PAUSED);
				var pending = client.readValuesAsync(0, TimestampsToReturn.Neither, List.of(temp));
				Thread.sleep(700);
				assertThat(pending.isDone()).isFalse();
				d.resume();
				assertThat(pending.get(5, TimeUnit.SECONDS).get(0).getValue().getValue()).isEqualTo(25.5);

				// 태그 — 실행 중에 더하고 값 바꾸고 지운다
				d.addTag(new TagSpec("Line2.Count", TagType.INT32, 3, null, null));
				d.setValue("Line2.Count", 7);
				assertThat(client.readValue(0, TimestampsToReturn.Neither, NodeId.parse("ns=" + d.namespaceIndex() + ";s=Line2.Count"))
					.getValue().getValue()).isEqualTo(7);
				d.removeTag("Line2.Count");
				assertThat(d.tags()).extracting(VirtualOpcUaServer.TagView::path).containsExactly("Line1.Temp");
				assertThatThrownBy(() -> d.addTag(new TagSpec("Line1.Temp", TagType.INT16, 0, null, null))).hasMessageContaining("이미");
			} finally {
				client.disconnect();
			}
		} finally {
			d.stop();
		}
		assertThat(d.getStatus()).isEqualTo(PlcStatus.STOPPED);
		// 정지하면 포트를 돌려준다 — 같은 포트로 다시 시작할 수 있다
		d.start();
		d.stop();
	}
}
