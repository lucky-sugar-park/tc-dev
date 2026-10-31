package com.wow.be.tc.vplc.modbus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.tc.vplc.modbus.ModbusDevice.Table;
import com.wow.be.tc.vplc.modbus.ModbusDevices.DeviceSpec;

import lombok.RequiredArgsConstructor;

/**
 * 가상 Modbus 장치 REST (docs/virtual-devices.md) — 장치 목록·더하기·지우기, 영역 값 보기·바꾸기, 늦게·안 답하기, 연결 끊기.
 * 번호는 프로토콜 주소(0부터) — tc 의 {@code HR1}(addressBase 1)은 여기 {@code hr} 0 번이다.
 */
@RestController
@RequestMapping("/api/v1/modbus/devices")
@RequiredArgsConstructor
public class ModbusDeviceController {

	private final ModbusDevices devices;

	public record DeviceView(String name, int port, int unitId, int size, long delayMs, boolean silent, int connections) {
	}

	public record Values(int start, List<Object> values) {
	}

	public record Behavior(Long delayMs, Boolean silent) {
	}

	private DeviceView view(ModbusDevice d) {
		ModbusTcpServer s = devices.server(d.port());
		return new DeviceView(d.name(), d.port(), d.unitId(), d.size(), d.delayMs(), d.silent(), s != null ? s.connections() : 0);
	}

	@GetMapping
	public List<DeviceView> list() {
		return devices.all().stream().map(this::view).sorted(java.util.Comparator.comparing(DeviceView::name)).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DeviceView add(@RequestBody DeviceSpec spec) throws IOException {
		return view(devices.add(spec));
	}

	@DeleteMapping("/{name}")
	public void remove(@PathVariable("name") String name) {
		devices.remove(name);
	}

	/** 값 보기 — 비트 영역은 true/false, 레지스터는 부호 없는 16비트(0~65535) */
	@GetMapping("/{name}/{table}")
	public Values read(@PathVariable("name") String name, @PathVariable("table") String table,
			@RequestParam(name = "start", defaultValue = "0") int start, @RequestParam(name = "count", defaultValue = "10") int count) {
		ModbusDevice d = devices.require(name);
		Table t = Table.of(table);
		if (!d.inRange(start, count) || count > 2000) {
			throw new IllegalArgumentException("범위 밖입니다(칸 " + d.size() + ", 한 번에 2000까지): " + start + "+" + count);
		}
		List<Object> out = new ArrayList<>(count);
		if (t.isBit()) {
			for (boolean b : d.bits(t, start, count)) {
				out.add(b);
			}
		} else {
			for (short s : d.registers(t, start, count)) {
				out.add(s & 0xFFFF);
			}
		}
		return new Values(start, out);
	}

	/** 값 바꾸기 — 비트는 true/false·1/0, 레지스터는 -32768~65535(음수는 2의 보수) */
	@PutMapping("/{name}/{table}")
	public Values write(@PathVariable("name") String name, @PathVariable("table") String table, @RequestBody Values body) {
		ModbusDevice d = devices.require(name);
		Table t = Table.of(table);
		List<Object> values = body.values() != null ? body.values() : List.of();
		if (values.isEmpty() || !d.inRange(body.start(), values.size())) {
			throw new IllegalArgumentException("범위 밖이거나 값이 없습니다: " + body.start() + "+" + values.size());
		}
		if (t.isBit()) {
			boolean[] bits = new boolean[values.size()];
			for (int i = 0; i < bits.length; i++) {
				Object v = values.get(i);
				bits[i] = Boolean.TRUE.equals(v) || (v instanceof Number n && n.intValue() != 0) || "1".equals(String.valueOf(v))
					|| "true".equalsIgnoreCase(String.valueOf(v));
			}
			d.setBits(t, body.start(), bits);
		} else {
			short[] regs = new short[values.size()];
			for (int i = 0; i < regs.length; i++) {
				int n = values.get(i) instanceof Number num ? num.intValue() : Integer.parseInt(String.valueOf(values.get(i)).trim());
				if (n < -32768 || n > 65535) {
					throw new IllegalArgumentException("레지스터 값은 -32768~65535 입니다: " + n);
				}
				regs[i] = (short) n;
			}
			d.setRegisters(t, body.start(), regs);
		}
		return read(name, table, body.start(), values.size());
	}

	/** 늦게 답하기(ms)·답하지 않기 — tc 시간 초과 시험 */
	@PutMapping("/{name}/behavior")
	public DeviceView behavior(@PathVariable("name") String name, @RequestBody Behavior b) {
		ModbusDevice d = devices.require(name);
		if (b.delayMs() != null) {
			d.delayMs(b.delayMs());
		}
		if (b.silent() != null) {
			d.silent(b.silent());
		}
		return view(d);
	}

	/** 그 장치 포트의 연결을 모두 끊는다 — tc 재연결 시험 */
	@PostMapping("/{name}/drop-connections")
	public Map<String, Object> drop(@PathVariable("name") String name) {
		ModbusDevice d = devices.require(name);
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("port", d.port());
		out.put("dropped", devices.server(d.port()).dropConnections());
		return out;
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Map<String, String> notFound(NoSuchElementException e) {
		return Map.of("message", e.getMessage());
	}

	@ExceptionHandler({ IllegalArgumentException.class, NumberFormatException.class })
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, String> bad(RuntimeException e) {
		return Map.of("message", String.valueOf(e.getMessage()));
	}

	@ExceptionHandler(IllegalStateException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public Map<String, String> conflict(IllegalStateException e) {
		return Map.of("message", e.getMessage());
	}

	@ExceptionHandler(IOException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public Map<String, String> io(IOException e) {
		return Map.of("message", "포트를 열 수 없습니다: " + e.getMessage());
	}
}
