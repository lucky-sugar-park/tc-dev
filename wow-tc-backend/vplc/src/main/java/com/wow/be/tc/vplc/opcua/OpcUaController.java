package com.wow.be.tc.vplc.opcua;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.tc.vplc.Types.Protocol;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.core.VirtualPlcRegistry;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.management.VirtualPlcManagementService;

/** OPC UA 가상 PLC 만의 REST — 엔드포인트 정보. 태그는 공통 {@code /memory/{id}/tags}(docs/vplc-architecture.md §2.4) */
@RestController
@RequestMapping("/api/v1/vplc")
public class OpcUaController {

	@Autowired
	private VirtualPlcManagementService management;

	@Autowired
	private OpcUaTagRepository tagRepository;

	/** 엔드포인트·네임스페이스 URI·번호(실행 중일 때, 아니면 -1)·세션 수·태그 수 */
	@GetMapping("/opcua/{vplcId}")
	public Map<String, Object> info(@PathVariable("vplcId") String vplcId) throws VirtualPlcException {
		VirtualPlcInfo info = management.findVirtualPlc(vplcId);
		if (info.getProtocol() != Protocol.OPC_UA) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, "OPC UA 가상 PLC 가 아닙니다: " + vplcId);
		}
		OpcUaVirtualPlc d = VirtualPlcRegistry.instance().get(vplcId) instanceof OpcUaVirtualPlc o ? o : null;
		Map<String, Object> m = new LinkedHashMap<>();
		Object path = info.getSettings().getOrDefault("path", OpcUaVirtualPlc.DEFAULT_PATH);
		m.put("endpointUrl", "opc.tcp://" + info.getIpAddress() + ":" + info.getStartPort() + path);
		m.put("namespaceUri", VirtualOpcUaServer.NAMESPACE_URI);
		m.put("namespaceIndex", d != null ? d.namespaceIndex() : -1);
		m.put("sessions", d != null ? d.sessionCount() : 0);
		m.put("tags", tagRepository.findByVplcIdOrderByPath(vplcId).size());
		return m;
	}
}
