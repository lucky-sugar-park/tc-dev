package com.wow.be.tc.interfs.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.Types.PropType;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.interfs.PropInfo;
import com.wow.be.tc.plc.protocol.PointType;
import com.wow.be.tc.plc.protocol.melsec.MelsecDriver;

/** RAW(3E BINARY 헤더·데이터 속성) → POINTS 점 (docs/plc-protocol-design.md §8·§15) */
class RawToPointsConverterTest {

	private final Map<String, Object> settings = new MelsecDriver().normalizeSettings(Map.of(), null);

	private static PropInfo prop(String name, int order, PropType type, int length, String value) {
		PropInfo p = new PropInfo();
		p.setName(name);
		p.setOrder(order);
		p.setType(type);
		p.setLength(length);
		if (value != null) {
			try {
				p.setValue(value);
			} catch (ToolControlException e) {
				throw new IllegalStateException(e);
			}
		}
		return p;
	}

	/** e2e fixture 와 같은 머리 — 속성을 나눈 방식은 상관없다(바이트를 이어 본다) */
	private static List<PropInfo> header(String command, String start, String device, String words) {
		List<PropInfo> h = new ArrayList<>();
		h.add(prop("subHeader", 1, PropType.BYTE, 2, "5000"));
		h.add(prop("route", 2, PropType.BYTE, 5, "00FFFF0300"));
		h.add(prop("reqDataLen", 3, PropType.BYTE, 2, "0000"));
		h.add(prop("timer", 4, PropType.BYTE, 2, "1000"));
		h.add(prop("command", 5, PropType.BYTE, 2, command));
		h.add(prop("subCommand", 6, PropType.BYTE, 2, "0000"));
		h.add(prop("startAddress", 7, PropType.BYTE, 3, start));
		h.add(prop("deviceCode", 8, PropType.BYTE, 1, device));
		h.add(prop("wordCount", 9, PropType.BYTE, 2, words));
		return h;
	}

	private static InterfaceInfo iface(InterfaceType type, List<PropInfo> header, List<PropInfo> data) {
		InterfaceInfo i = new InterfaceInfo();
		i.setName("if");
		i.setInterfaceType(type);
		i.setReplyName("if.reply");
		i.setHeaderProps(header);
		i.setDataProps(data);
		return i;
	}

	private static InterfaceInfo reply(PropInfo... data) {
		InterfaceInfo r = new InterfaceInfo();
		r.setName("if.reply");
		r.setInterfaceType(InterfaceType.REPLY);
		r.setDataProps(new ArrayList<>(List.of(data)));
		return r;
	}

	@Test
	void 읽기_D100_두_워드() {
		InterfaceInfo read = iface(InterfaceType.READ, header("0104", "640000", "A8", "0200"), new ArrayList<>());
		RawToPointsConverter.Result r = RawToPointsConverter.convert(read,
			reply(prop("d101", 2, PropType.SHORT, 2, null), prop("d100", 1, PropType.SHORT, 2, null)), settings);
		assertThat(r.points()).extracting(PointInfo::getName, PointInfo::getAddress, PointInfo::getType)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("d100", "D100", PointType.INT16),
				org.assertj.core.groups.Tuple.tuple("d101", "D101", PointType.INT16));
		assertThat(r.warnings()).isEmpty();
	}

	@Test
	void 여러_형과_16진_디바이스() {
		// W1A0 부터 1+2+4+2 = 9워드: SHORT, INT, LONG, DOUBLE(4), STRING 4
		InterfaceInfo read = iface(InterfaceType.READ, header("0104", "A00100", "B4", "0B00"), new ArrayList<>());
		RawToPointsConverter.Result r = RawToPointsConverter.convert(read, reply(
			prop("a", 1, PropType.SHORT, 2, null), prop("b", 2, PropType.INT, 4, null), prop("c", 3, PropType.LONG, 8, null),
			prop("d", 4, PropType.DOUBLE, 4, null), prop("s", 5, PropType.STRING, 4, null)), settings);
		assertThat(r.points()).extracting(PointInfo::getAddress).containsExactly("W1A0", "W1A1", "W1A3", "W1A7", "W1A9");
		assertThat(r.points()).extracting(PointInfo::getType)
			.containsExactly(PointType.INT16, PointType.INT32, PointType.INT64, PointType.FLOAT32, PointType.STRING);
		assertThat(r.points().get(4).getLength()).isEqualTo(4);
		assertThat(r.warnings()).hasSize(1).first().asString().contains("STRING");
	}

	@Test
	void 쓰기는_이_인터페이스의_데이터_속성() {
		InterfaceInfo write = iface(InterfaceType.WRITE, header("0114", "C80000", "A8", "0300"),
			new ArrayList<>(List.of(prop("x", 1, PropType.SHORT, 2, null), prop("y", 2, PropType.INT, 4, null))));
		RawToPointsConverter.Result r = RawToPointsConverter.convert(write, null, settings);
		assertThat(r.points()).extracting(PointInfo::getAddress).containsExactly("D200", "D201");
	}

	@Test
	void 바꾸지_않는_정의() {
		InterfaceInfo read = iface(InterfaceType.READ, header("0114", "640000", "A8", "0100"), new ArrayList<>());
		assertThatThrownBy(() -> RawToPointsConverter.convert(read, reply(prop("a", 1, PropType.SHORT, 2, null)), settings))
			.hasMessageContaining("0401");
		InterfaceInfo len = iface(InterfaceType.READ, header("0104", "640000", "A8", "0300"), new ArrayList<>());
		assertThatThrownBy(() -> RawToPointsConverter.convert(len, reply(prop("a", 1, PropType.SHORT, 2, null)), settings))
			.hasMessageContaining("워드 수");
		InterfaceInfo odd = iface(InterfaceType.READ, header("0104", "640000", "A8", "0200"), new ArrayList<>());
		assertThatThrownBy(() -> RawToPointsConverter.convert(odd,
			reply(prop("s", 1, PropType.STRING, 1, null), prop("t", 2, PropType.STRING, 3, null)), settings))
			.hasMessageContaining("워드 경계");
		InterfaceInfo bool = iface(InterfaceType.READ, header("0104", "640000", "A8", "0100"), new ArrayList<>());
		assertThatThrownBy(() -> RawToPointsConverter.convert(bool,
			reply(prop("f", 1, PropType.BOOLEAN, 1, null), prop("g", 2, PropType.BOOLEAN, 1, null)), settings))
			.hasMessageContaining("BOOLEAN");
		InterfaceInfo bits = iface(InterfaceType.READ, header("0104", "000000", "90", "0100"), new ArrayList<>());
		assertThatThrownBy(() -> RawToPointsConverter.convert(bits, reply(prop("m", 1, PropType.SHORT, 2, null)), settings))
			.hasMessageContaining("비트 디바이스");
		InterfaceInfo noReply = iface(InterfaceType.READ, header("0104", "640000", "A8", "0100"), new ArrayList<>());
		assertThatThrownBy(() -> RawToPointsConverter.convert(noReply, null, settings)).hasMessageContaining("응답 인터페이스");
	}

	@Test
	void 커넥터_설정과_다른_경로는_거절() {
		InterfaceInfo read = iface(InterfaceType.READ, header("0104", "640000", "A8", "0100"), new ArrayList<>());
		Map<String, Object> other = new MelsecDriver().normalizeSettings(Map.of("stationNo", 1), null);
		assertThatThrownBy(() -> RawToPointsConverter.convert(read, reply(prop("a", 1, PropType.SHORT, 2, null)), other))
			.hasMessageContaining("국 번호");
		Map<String, Object> timer = new MelsecDriver().normalizeSettings(Map.of("monitoringTimer", 4), null);
		assertThat(RawToPointsConverter.convert(read, reply(prop("a", 1, PropType.SHORT, 2, null)), timer).warnings())
			.anySatisfy(w -> assertThat(w).contains("감시 타이머"));
	}

	@Test
	void ASCII_헤더는_거절() {
		List<PropInfo> h = List.of(prop("subHeader", 1, PropType.STRING, 4, "5000"));
		InterfaceInfo read = iface(InterfaceType.READ, new ArrayList<>(h), new ArrayList<>());
		assertThatThrownBy(() -> RawToPointsConverter.convert(read, reply(prop("a", 1, PropType.SHORT, 2, null)), settings))
			.hasMessageContaining("ASCII");
	}
}
