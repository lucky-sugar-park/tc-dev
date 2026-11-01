package com.wow.be.tc.vplc.melsec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.core.memory.AreaMemory.Area;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;

/** 가상 MELSEC PLC 의 영역 메모리와 상태 규칙 — 메모리 종류 = 영역(워드), 16진 주소·비트 디바이스 표시 */
class MelsecAreaMemoryTest {

	@Test
	void 영역_메모리와_상태_규칙() throws Exception {
		VirtualPlcInfo info = new VirtualPlcInfo();
		info.setId("mc-area");
		info.setName("mc-area");
		info.setIpAddress("127.0.0.1");
		info.setStartPort(15999);
		info.setPortCount(1);
		info.setPortConnLimit(1);
		info.setMemoryTypeList(new ArrayList<>(List.of("D*", "W*", "M*")));
		MelsecProvider provider = new MelsecProvider();
		provider.normalize(info);
		MelsecVirtualPlc plc = (MelsecVirtualPlc) provider.create(info);

		List<Area> areas = plc.areas();
		assertThat(areas).extracting(Area::label).containsExactlyInAnyOrder("D", "W", "M");
		Area d = areas.stream().filter(a -> a.label().equals("D")).findFirst().orElseThrow();
		assertThat(d.size()).isEqualTo(12288);
		assertThat(d.hexAddress()).isFalse();
		assertThat(areas.stream().filter(a -> a.label().equals("W")).findFirst().orElseThrow().hexAddress()).isTrue();
		assertThat(areas.stream().filter(a -> a.label().equals("M")).findFirst().orElseThrow().bitDevice()).isTrue();

		// 워드 — 낮은 바이트가 앞(MC 와 같다)
		plc.write("D*", 100, new int[] { 1234, 0xFFFF, 0x4241 });
		assertThat(plc.read("D*", 100, 3)).containsExactly(1234, 0xFFFF, 0x4241);
		assertThat(plc.read("D*", 100, 3)[2] & 0xFF).isEqualTo('A');
		plc.clear("D*");
		assertThat(plc.read("D*", 100, 3)).containsExactly(0, 0, 0);
		assertThatThrownBy(() -> plc.read("R*", 0, 1)).hasMessageContaining("없는 메모리 종류");

		// 상태 규칙 — 게시 직후 일시정지는 409(예전 MELSEC 은 받았다), 정지는 상태만
		assertThat(plc.getStatus()).isEqualTo(PlcStatus.PUBLISHED);
		assertThatThrownBy(plc::pause).hasMessageContaining("일시정지할 수 없는");
		plc.stop();
		assertThat(plc.getStatus()).isEqualTo(PlcStatus.STOPPED);
		plc.release();
		assertThat(plc.getStatus()).isEqualTo(PlcStatus.RELEASED);
	}
}
