package com.wow.be.tc.vplc.core.memory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.core.VirtualPlc;
import com.wow.be.tc.vplc.core.VirtualPlcProvider;
import com.wow.be.tc.vplc.core.VirtualPlcProviders;
import com.wow.be.tc.vplc.core.VirtualPlcRegistry;
import com.wow.be.tc.vplc.core.memory.AreaMemory.Area;
import com.wow.be.tc.vplc.core.memory.TagMemory.Tag;
import com.wow.be.tc.vplc.core.memory.TagMemory.TagDef;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.management.VirtualPlcManagementService;

/**
 * 메모리 — 가상 PLC 의 능력으로 다룬다(docs/vplc-architecture.md §2.3). 영역 메모리는 게시된 가상 PLC 가({@link AreaMemory}),
 * 태그 메모리는 그 종류의 Provider 가({@link TagMemory}) 구현한다. 종류는 모른다.
 */
@Service
public class MemoryService {

	/** 한 번에 읽고 쓰는 칸 수 한도 */
	public static final int MAX_COUNT = 2000;

	@Autowired
	private VirtualPlcManagementService management;

	@Autowired
	private VirtualPlcProviders providers;

	private final VirtualPlcRegistry registry = VirtualPlcRegistry.instance();

	/** 메모리 능력 — {kind: AREA, areas} 또는 {kind: TAG} */
	public Map<String, Object> describe(String vplcId) throws VirtualPlcException {
		VirtualPlcInfo info = management.findVirtualPlc(vplcId);
		Map<String, Object> m = new LinkedHashMap<>();
		if (providers.of(info.getProtocol()) instanceof TagMemory) {
			m.put("kind", "TAG");
		} else {
			m.put("kind", "AREA");
			m.put("areas", areaMemory(vplcId).areas());
		}
		return m;
	}

	// ---- 영역 ----

	private AreaMemory areaMemory(String vplcId) throws VirtualPlcException {
		VirtualPlc plc = registry.get(vplcId);
		if (plc == null) {
			management.findVirtualPlc(vplcId);
			throw new VirtualPlcException(VirtualPlcException.ERR_409, "게시되지 않은 가상 PLC 입니다 — 먼저 게시하세요: " + vplcId);
		}
		if (plc instanceof AreaMemory a) {
			return a;
		}
		throw new VirtualPlcException(VirtualPlcException.ERR_400, "영역 메모리가 없는 가상 PLC 입니다(" + plc.getProtocol() + ") — 태그 API 를 쓰세요: " + vplcId);
	}

	private static Area area(AreaMemory m, String code, int start, int count) throws VirtualPlcException {
		Area a = m.areas().stream().filter(x -> x.code().equalsIgnoreCase(code) || x.label().equalsIgnoreCase(code)).findFirst()
			.orElseThrow(() -> new VirtualPlcException(VirtualPlcException.ERR_400, "없는 영역입니다: " + code));
		if (start < 0 || count < 1 || count > MAX_COUNT || start + count > a.size()) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400,
				"범위 밖입니다(" + a.label() + " 칸 " + a.size() + ", 한 번에 1~" + MAX_COUNT + "): " + start + "+" + count);
		}
		return a;
	}

	public Map<String, Object> read(String vplcId, String code, int start, int count) throws VirtualPlcException {
		AreaMemory m = areaMemory(vplcId);
		Area a = area(m, code, start, count);
		return values(start, m.read(a.code(), start, count));
	}

	/** 값 쓰기 — WORD 는 -32768~65535, BIT 는 0·1(true/false 도) */
	public Map<String, Object> write(String vplcId, String code, int start, List<Object> values) throws VirtualPlcException {
		AreaMemory m = areaMemory(vplcId);
		Area a = area(m, code, start, values == null || values.isEmpty() ? 0 : values.size());
		int[] v = new int[values.size()];
		for (int i = 0; i < v.length; i++) {
			v[i] = toInt(values.get(i), a);
		}
		m.write(a.code(), start, v);
		return values(start, m.read(a.code(), start, v.length));
	}

	public void clear(String vplcId, String code) throws VirtualPlcException {
		AreaMemory m = areaMemory(vplcId);
		m.clear(area(m, code, 0, 1).code());
	}

	private static int toInt(Object o, Area a) throws VirtualPlcException {
		if (a.unit() == AreaMemory.Unit.BIT) {
			String s = String.valueOf(o).trim();
			if (Boolean.TRUE.equals(o) || "1".equals(s) || "true".equalsIgnoreCase(s) || (o instanceof Number n && n.intValue() == 1)) {
				return 1;
			}
			if (Boolean.FALSE.equals(o) || "0".equals(s) || "false".equalsIgnoreCase(s) || (o instanceof Number n && n.intValue() == 0)) {
				return 0;
			}
			throw new VirtualPlcException(VirtualPlcException.ERR_400, "비트 값은 0·1 입니다: " + o);
		}
		int n;
		try {
			n = o instanceof Number num ? num.intValue() : Integer.parseInt(String.valueOf(o).trim());
		} catch (NumberFormatException e) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, "워드 값이 숫자가 아닙니다: " + o);
		}
		if (n < Short.MIN_VALUE || n > 0xFFFF) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, "워드 값은 -32768~65535 입니다: " + n);
		}
		return n & 0xFFFF;
	}

	private static Map<String, Object> values(int start, int[] v) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("start", start);
		m.put("values", v);
		return m;
	}

	// ---- 태그 ----

	private TagMemory tagMemory(String vplcId) throws VirtualPlcException {
		VirtualPlcInfo info = management.findVirtualPlc(vplcId);
		VirtualPlcProvider p = providers.of(info.getProtocol());
		if (p instanceof TagMemory t) {
			return t;
		}
		throw new VirtualPlcException(VirtualPlcException.ERR_400, "태그 메모리가 없는 가상 PLC 입니다(" + info.getProtocol() + ") — 영역 API 를 쓰세요: " + vplcId);
	}

	public List<Tag> tags(String vplcId) throws VirtualPlcException {
		return tagMemory(vplcId).tags(vplcId);
	}

	public void addTag(String vplcId, TagDef def) throws VirtualPlcException {
		tagMemory(vplcId).addTag(vplcId, def);
	}

	public void removeTag(String vplcId, String path) throws VirtualPlcException {
		tagMemory(vplcId).removeTag(vplcId, path);
	}

	public Tag setValue(String vplcId, String path, Object value) throws VirtualPlcException {
		return tagMemory(vplcId).setValue(vplcId, path, value);
	}
}
