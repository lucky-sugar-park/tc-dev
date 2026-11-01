package com.wow.be.tc.vplc.lifecycle;

import org.springframework.stereotype.Component;

import com.wow.be.tc.vplc.core.VirtualPlc;
import com.wow.be.tc.vplc.core.VirtualPlcRegistry;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 종료 때 게시된 가상 PLC 를 모두 내려 포트를 돌려준다 — **DB 의 상태는 그대로 둔다**. 다음 기동 때 {@link VirtualPlcLoader} 가
 * 마지막 상태(RUNNING·PAUSED·STOPPED)로 되살린다. 예전에는 정지 서비스를 불러 DB 에 STOPPED 를 남겨, 다시 띄우면 아무것도 시작되지
 * 않았다(docs/vplc-stop-error.md §6).
 */
@Component
@Slf4j
public class VirtualPlcResourceReleaseShutdownHook {

	@PreDestroy
	public void releaseAllResource() {
		VirtualPlcRegistry registry = VirtualPlcRegistry.instance();
		for (VirtualPlc plc : registry.all()) {
			log.info("가상 PLC 를 내립니다(DB 상태는 그대로): {} ({})", plc.getName(), plc.getStatus());
			registry.release(plc.getId());
		}
	}
}
