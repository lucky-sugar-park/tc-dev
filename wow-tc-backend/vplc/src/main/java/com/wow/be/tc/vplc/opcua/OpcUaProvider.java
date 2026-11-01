package com.wow.be.tc.vplc.opcua;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.wow.be.tc.vplc.Types.Protocol;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.core.VirtualPlc;
import com.wow.be.tc.vplc.core.VirtualPlcProvider;
import com.wow.be.tc.vplc.core.VirtualPlcRegistry;
import com.wow.be.tc.vplc.core.memory.TagMemory;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer.TagSpec;
import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer.TagView;

/**
 * OPC UA 가상 PLC 종류 — 설정 {@code path}(기본 /vplc)·{@code requireLogin}(false)·{@code trustAllClients}(true), 로그인 사용자는
 * {@code vplc.opcua.users}(모든 OPC UA 가상 PLC 공통). 태그 메모리({@link TagMemory}) — 정의는 VPLC_OPCUA_TAG, 지금 값은 게시된 가상 PLC.
 * 지우면 태그 정의도 지운다.
 */
@Component
@EnableConfigurationProperties(OpcUaProvider.OpcUaProperties.class)
public class OpcUaProvider implements VirtualPlcProvider, TagMemory {

	public static final List<String> AREAS = List.of("TAG");

	public static final int DEFAULT_CONN_LIMIT = 8;

	/** 가상 OPC UA 서버의 로그인 사용자(시험용) — vplc.opcua.users */
	@ConfigurationProperties(prefix = "vplc.opcua")
	public record OpcUaProperties(List<User> users) {

		public OpcUaProperties {
			users = users != null ? users : List.of();
		}

		public record User(String username, String password) {
		}

		public Map<String, String> userMap() {
			Map<String, String> m = new LinkedHashMap<>();
			users.forEach(u -> m.put(u.username(), u.password()));
			return m;
		}
	}

	@Autowired
	private OpcUaTagRepository tagRepository;

	@Autowired
	private OpcUaProperties properties;

	@Override
	public Protocol protocol() {
		return Protocol.OPC_UA;
	}

	@Override
	public void normalize(VirtualPlcInfo info) throws VirtualPlcException {
		VirtualPlcProvider.singlePort(info, DEFAULT_CONN_LIMIT);
		Map<String, Object> s = new LinkedHashMap<>(info.getSettings() != null ? info.getSettings() : Map.of());
		String path = s.get("path") != null ? String.valueOf(s.get("path")).trim() : OpcUaVirtualPlc.DEFAULT_PATH;
		if (!path.isEmpty() && !path.matches("/?[A-Za-z0-9_\\-/.]{0,100}")) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, "엔드포인트 경로는 영숫자·_ - / . 입니다: " + path);
		}
		s.put("path", path.isEmpty() || path.startsWith("/") ? path : "/" + path);
		s.put("requireLogin", OpcUaVirtualPlc.bool(s.get("requireLogin"), false));
		s.put("trustAllClients", OpcUaVirtualPlc.bool(s.get("trustAllClients"), true));
		info.setSettings(s);
		info.setFrameFormat(null);
		info.setMemoryTypeList(new ArrayList<>(AREAS));
	}

	@Override
	public VirtualPlc create(VirtualPlcInfo info) {
		return new OpcUaVirtualPlc(info, tagRepository.findByVplcIdOrderByPath(info.getId()).stream().map(OpcUaTagEntity::spec).toList(),
			properties.userMap());
	}

	@Override
	public void onDelete(String vplcId) {
		tagRepository.deleteByVplcId(vplcId);
	}

	// ---- 태그 메모리 ----

	/** 게시된 가상 OPC UA 서버 — 게시 안 됐으면 null(태그 정의는 DB 로) */
	private static OpcUaVirtualPlc published(String vplcId) {
		return VirtualPlcRegistry.instance().get(vplcId) instanceof OpcUaVirtualPlc o ? o : null;
	}

	/** 정지·게시 전의 태그 모양 — 처음 값, NodeId 는 nsu= 만 */
	static TagView stoppedView(TagSpec t) {
		return new TagView(t.path(), null, "nsu=" + VirtualOpcUaServer.NAMESPACE_URI + ";s=" + t.path(), t.type(), t.value(), t.simulation(),
			t.description());
	}

	private static Tag tag(TagView v) {
		VirtualOpcUaServer.Simulation s = v.simulation();
		return new Tag(v.path(), v.nodeId(), v.expandedNodeId(), v.type().name(), v.value(),
			s != null ? new TagMemory.Simulation(s.kind(), s.periodMs(), s.min(), s.max()) : null, v.description());
	}

	private static TagSpec spec(TagDef d) throws VirtualPlcException {
		try {
			TagMemory.Simulation s = d.simulation();
			return new TagSpec(d.path(), d.type() != null ? TagType.valueOf(d.type().trim().toUpperCase()) : null, d.value(),
				s != null && s.kind() != null ? new VirtualOpcUaServer.Simulation(s.kind().toUpperCase(), s.periodMs(), s.min(), s.max()) : null, d.description());
		} catch (IllegalArgumentException e) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, "모르는 태그 형입니다: " + d.type());
		}
	}

	@Override
	public List<Tag> tags(String vplcId) {
		OpcUaVirtualPlc d = published(vplcId);
		List<TagView> views = d != null ? d.tags()
			: tagRepository.findByVplcIdOrderByPath(vplcId).stream().map(e -> stoppedView(e.spec())).toList();
		return views.stream().map(OpcUaProvider::tag).toList();
	}

	@Override
	@Transactional
	public void addTag(String vplcId, TagDef def) throws VirtualPlcException {
		TagSpec spec = spec(def);
		if (tagRepository.existsById(vplcId + ":" + spec.path())) {
			throw new VirtualPlcException(VirtualPlcException.ERR_409, "이미 있는 태그입니다: " + spec.path());
		}
		try {
			OpcUaVirtualPlc d = published(vplcId);
			if (d != null) {
				d.addTag(spec);
			} else {
				VirtualOpcUaServer.validate(spec);
			}
		} catch (IllegalArgumentException e) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, e.getMessage());
		}
		tagRepository.save(OpcUaTagEntity.of(vplcId, spec));
	}

	@Override
	@Transactional
	public void removeTag(String vplcId, String path) throws VirtualPlcException {
		String id = vplcId + ":" + path;
		if (!tagRepository.existsById(id)) {
			throw new VirtualPlcException(VirtualPlcException.ERR_404, "없는 태그입니다: " + path);
		}
		OpcUaVirtualPlc d = published(vplcId);
		if (d != null) {
			d.removeTag(path);
		}
		tagRepository.deleteById(id);
	}

	@Override
	public Tag setValue(String vplcId, String path, Object value) throws VirtualPlcException {
		OpcUaVirtualPlc d = published(vplcId);
		if (d == null) {
			throw new VirtualPlcException(VirtualPlcException.ERR_409, "게시되지 않은 가상 PLC 입니다 — 먼저 게시·시작하세요: " + vplcId);
		}
		try {
			return tag(d.setValue(path, value));
		} catch (IllegalArgumentException e) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, e.getMessage());
		}
	}
}
