package com.wow.be.tc.plc.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Redis 해시 → 연결 상태 (docs/plc-ha-design.md §3.4) */
class PlcConnectionStateStoreTest {

	@Test
	void 해시를_연결_상태로() {
		Map<Object, Object> hash = new HashMap<>();
		hash.put("instance", "host-a-1793400000000");
		hash.put("operStatus", "RUNNING");
		hash.put("activeChannels", "2");
		hash.put("lastEvent", "CONNECT");
		hash.put("lastEventAt", "1793400001000");
		hash.put("lastConnectedAt", "1793400001000");
		hash.put("lastError", "연결 끊김 127.0.0.1:15000");
		hash.put("lastErrorAt", "1793400000500");
		hash.put("updatedAt", "1793400002000");

		PlcConnectionState state = PlcConnectionStateStore.toState("plc-1", hash);

		assertThat(state.getPlcConnectorName()).isEqualTo("plc-1");
		assertThat(state.getInstance()).isEqualTo("host-a-1793400000000");
		assertThat(state.getOperStatus()).isEqualTo("RUNNING");
		assertThat(state.getActiveChannels()).isEqualTo(2);
		assertThat(state.getLastEvent()).isEqualTo("CONNECT");
		assertThat(state.getLastConnectedAt().getTime()).isEqualTo(1793400001000L);
		assertThat(state.getLastError()).isEqualTo("연결 끊김 127.0.0.1:15000");
		assertThat(state.getLastErrorAt().getTime()).isEqualTo(1793400000500L);
		assertThat(state.getUpdatedAt().getTime()).isEqualTo(1793400002000L);
	}

	@Test
	void 주기_갱신만_있으면_이벤트_값은_비어_있다() {
		Map<Object, Object> hash = new HashMap<>();
		hash.put("instance", "host-a-1");
		hash.put("operStatus", "RUNNING");
		hash.put("activeChannels", "0");
		hash.put("updatedAt", "1");

		PlcConnectionState state = PlcConnectionStateStore.toState("plc-1", hash);

		assertThat(state.getActiveChannels()).isZero();
		assertThat(state.getLastEvent()).isNull();
		assertThat(state.getLastConnectedAt()).isNull();
		assertThat(state.getLastError()).isNull();
	}
}
