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
import com.wow.be.tc.vplc.core.VirtualPlc;
import com.wow.be.tc.vplc.core.VirtualPlcRegistry;
import com.wow.be.tc.vplc.core.memory.MemoryService;
import com.wow.be.tc.vplc.core.memory.TagMemory.Tag;
import com.wow.be.tc.vplc.core.memory.TagMemory.TagDef;

/**
 * 메모리·연결 REST — 종류와 관계없이 가상 PLC 의 능력으로(docs/vplc-architecture.md §2.4).
 * <ul>
 *   <li>{@code GET /memory/{id}} — 능력: {kind: AREA, areas} 또는 {kind: TAG}</li>
 *   <li>영역(MELSEC·Modbus): {@code GET|PUT /memory/{id}/area/{area}}, {@code POST /memory/{id}/area/{area}/clear}</li>
 *   <li>태그(OPC UA): {@code GET|POST /memory/{id}/tags}, {@code PUT|DELETE /memory/{id}/tags/{path}}</li>
 *   <li>{@code POST /control/drop-connections/{id}} — 붙은 연결 끊기</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/vplc")
public class MemoryController {

	@Autowired
	private MemoryService memory;

	public record Values(int start, List<Object> values) {
	}

	public record Value(Object value) {
	}

	@GetMapping("/memory/{vplcId}")
	public Map<String, Object> describe(@PathVariable("vplcId") String vplcId) throws VirtualPlcException {
		return memory.describe(vplcId);
	}

	@GetMapping("/memory/{vplcId}/area/{area}")
	public Map<String, Object> read(@PathVariable("vplcId") String vplcId, @PathVariable("area") String area,
			@RequestParam(name = "start", defaultValue = "0") int start, @RequestParam(name = "count", defaultValue = "10") int count)
			throws VirtualPlcException {
		return memory.read(vplcId, area, start, count);
	}

	@PutMapping("/memory/{vplcId}/area/{area}")
	public Map<String, Object> write(@PathVariable("vplcId") String vplcId, @PathVariable("area") String area, @RequestBody Values body)
			throws VirtualPlcException {
		return memory.write(vplcId, area, body.start(), body.values());
	}

	@PostMapping("/memory/{vplcId}/area/{area}/clear")
	public void clear(@PathVariable("vplcId") String vplcId, @PathVariable("area") String area) throws VirtualPlcException {
		memory.clear(vplcId, area);
	}

	@GetMapping("/memory/{vplcId}/tags")
	public List<Tag> tags(@PathVariable("vplcId") String vplcId) throws VirtualPlcException {
		return memory.tags(vplcId);
	}

	@PostMapping("/memory/{vplcId}/tags")
	public void addTag(@PathVariable("vplcId") String vplcId, @RequestBody TagDef def) throws VirtualPlcException {
		memory.addTag(vplcId, def);
	}

	@PutMapping("/memory/{vplcId}/tags/{path}")
	public Tag setValue(@PathVariable("vplcId") String vplcId, @PathVariable("path") String path, @RequestBody Value body) throws VirtualPlcException {
		return memory.setValue(vplcId, path, body.value());
	}

	@DeleteMapping("/memory/{vplcId}/tags/{path}")
	public void removeTag(@PathVariable("vplcId") String vplcId, @PathVariable("path") String path) throws VirtualPlcException {
		memory.removeTag(vplcId, path);
	}

	/** 붙은 연결을 모두 끊는다(포트는 계속 받는다) — tc 재연결 시험 */
	@PostMapping("/control/drop-connections/{vplcId}")
	public Map<String, Object> dropConnections(@PathVariable("vplcId") String vplcId) throws VirtualPlcException {
		VirtualPlc plc = VirtualPlcRegistry.instance().get(vplcId);
		if (plc == null) {
			throw new VirtualPlcException(VirtualPlcException.ERR_409, "게시되지 않은 가상 PLC 입니다: " + vplcId);
		}
		return Map.of("dropped", plc.dropConnections());
	}
}
