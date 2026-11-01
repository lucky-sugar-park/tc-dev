package com.wow.be.tc.vplc.endpoints;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.manipulation.impl.VirtualDeviceService;
import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer.TagSpec;
import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer.TagView;

/**
 * 종류별 메모리·제어 REST(docs/virtual-devices.md) — 등록·게시·시작 등 공통은 {@link VirtualPlcController}.
 * <ul>
 *   <li>Modbus: {@code GET|PUT /modbus/{vplcId}/{co|di|ir|hr}}, {@code POST /control/delay/{vplcId}}</li>
 *   <li>OPC UA: {@code GET /opcua/{vplcId}}, {@code GET|POST /opcua/{vplcId}/tags}, {@code PUT|DELETE /opcua/{vplcId}/tags/{path}}</li>
 *   <li>모든 종류: {@code POST /control/drop-connections/{vplcId}}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/vplc")
public class VirtualDeviceController {

	@Autowired
	private VirtualDeviceService service;

	public record Values(int start, List<Object> values) {
	}

	public record Delay(long delayMs) {
	}

	public record Value(Object value) {
	}

	@GetMapping("/modbus/{vplcId}/{table}")
	public Map<String, Object> modbusRead(@PathVariable("vplcId") String vplcId, @PathVariable("table") String table,
			@RequestParam(name = "start", defaultValue = "0") int start, @RequestParam(name = "count", defaultValue = "10") int count)
			throws VirtualPlcException {
		return service.modbusRead(vplcId, table, start, count);
	}

	@PutMapping("/modbus/{vplcId}/{table}")
	public Map<String, Object> modbusWrite(@PathVariable("vplcId") String vplcId, @PathVariable("table") String table, @RequestBody Values body)
			throws VirtualPlcException {
		return service.modbusWrite(vplcId, table, body.start(), body.values());
	}

	/** 응답 지연(ms) — Modbus */
	@PostMapping("/control/delay/{vplcId}")
	public VirtualPlcInfo delay(@PathVariable("vplcId") String vplcId, @RequestBody Delay body) throws VirtualPlcException {
		return service.modbusDelay(vplcId, body.delayMs());
	}

	/** 붙은 연결을 모두 끊는다(포트는 계속 받는다) — tc 재연결 시험 */
	@PostMapping("/control/drop-connections/{vplcId}")
	public Map<String, Object> dropConnections(@PathVariable("vplcId") String vplcId) throws VirtualPlcException {
		return Map.of("dropped", service.dropConnections(vplcId));
	}

	@GetMapping("/opcua/{vplcId}")
	public Map<String, Object> opcUaInfo(@PathVariable("vplcId") String vplcId) throws VirtualPlcException {
		return service.opcUaInfo(vplcId);
	}

	@GetMapping("/opcua/{vplcId}/tags")
	public List<TagView> opcUaTags(@PathVariable("vplcId") String vplcId) throws VirtualPlcException {
		return service.opcUaTags(vplcId);
	}

	@PostMapping("/opcua/{vplcId}/tags")
	public void opcUaAddTag(@PathVariable("vplcId") String vplcId, @RequestBody TagSpec spec) throws VirtualPlcException {
		service.opcUaAddTag(vplcId, spec);
	}

	@PutMapping("/opcua/{vplcId}/tags/{path}")
	public TagView opcUaSetValue(@PathVariable("vplcId") String vplcId, @PathVariable("path") String path, @RequestBody Value body)
			throws VirtualPlcException {
		return service.opcUaSetValue(vplcId, path, body.value());
	}

	@DeleteMapping("/opcua/{vplcId}/tags/{path}")
	public void opcUaRemoveTag(@PathVariable("vplcId") String vplcId, @PathVariable("path") String path) throws VirtualPlcException {
		service.opcUaRemoveTag(vplcId, path);
	}
}
