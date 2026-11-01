package com.wow.be.tc.vplc.modbus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.tc.vplc.Types.Protocol;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.core.VirtualPlcRegistry;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.management.VirtualPlcManagementService;

/** Modbus 가상 PLC 만의 REST — 응답 지연. 메모리는 공통 {@code /memory}(docs/vplc-architecture.md §2.4) */
@RestController
@RequestMapping("/api/v1/vplc")
public class ModbusController {

	@Autowired
	private VirtualPlcManagementService management;

	public record Delay(long delayMs) {
	}

	/** 응답 지연(ms) — 메모리에 바로, 설정(DB)에도 남긴다 */
	@PostMapping("/control/delay/{vplcId}")
	@Transactional
	public VirtualPlcInfo delay(@PathVariable("vplcId") String vplcId, @RequestBody Delay body) throws VirtualPlcException {
		VirtualPlcInfo info = management.findVirtualPlc(vplcId);
		if (info.getProtocol() != Protocol.MODBUS_TCP) {
			throw ModbusProvider.bad("응답 지연은 Modbus 가상 PLC 만: " + vplcId);
		}
		if (body.delayMs() < 0 || body.delayMs() > 600_000) {
			throw ModbusProvider.bad("응답 지연은 0~600000ms 입니다: " + body.delayMs());
		}
		info.getSettings().put("delayMs", body.delayMs());
		management.updateVirtualPlc(info);
		if (VirtualPlcRegistry.instance().get(vplcId) instanceof ModbusVirtualPlc m) {
			m.memory().delayMs(body.delayMs());
		}
		return management.findVirtualPlc(vplcId);
	}
}
