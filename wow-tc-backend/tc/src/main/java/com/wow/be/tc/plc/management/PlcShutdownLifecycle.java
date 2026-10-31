package com.wow.be.tc.plc.management;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.wow.be.tc.plc.connector.PlcAdapterManager;

import lombok.extern.slf4j.Slf4j;

/**
 * 앱 종료 순서에서 PLC 연결을 닫는 자리 (docs/plc-ha-design.md §5.2, §10 1단계).
 *
 * <p>종료는 SmartLifecycle phase 가 큰 것부터 멈춘다.
 * <ol>
 *   <li>Quartz 스케줄러(phase 최대) — 새 스케줄 실행을 멈춘다.</li>
 *   <li>웹 서버 graceful shutdown(DEFAULT-1024) — 새 요청을 받지 않고 진행 중 요청을 마친다({@code server.shutdown=graceful}).</li>
 *   <li>웹 서버 종료(DEFAULT-2048)</li>
 *   <li><b>여기(DEFAULT-4096)</b> — 진행 중 PLC 요청이 다 끝난 뒤 연결을 닫는다.</li>
 * </ol>
 * 연결은 이벤트 없이 닫으므로 커넥터는 PUBLISHED 로 남아 다음 기동 때 다시 올라온다. 연결을 먼저 닫아야 PLC 쪽 슬롯이 빨리 비어
 * 다음 기동(이나 다른 인스턴스)이 바로 연결할 수 있다.
 */
@Slf4j
@Component
public class PlcShutdownLifecycle implements SmartLifecycle {

	private final PlcOwnershipManager ownership;

	private volatile boolean running;

	public PlcShutdownLifecycle(PlcOwnershipManager ownership) {
		this.ownership = ownership;
	}

	@Override
	public void start() {
		running = true;
	}

	@Override
	public void stop() {
		log.info("PLC 연결을 닫습니다(앱 종료) — 진행 중 요청은 웹 서버 단계에서 마쳤습니다");
		ownership.stopAcquiring();
		PlcAdapterManager.getPlcManager().shutdownAllQuietly();
		// 연결을 닫은 뒤 임대를 반납한다 — PLC 슬롯이 비어 있어 다른 인스턴스가 다음 검사(2초 안)에서 바로 연결한다(§5.2 ③④)
		ownership.releaseAll();
		running = false;
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public int getPhase() {
		return SmartLifecycle.DEFAULT_PHASE - 4096;
	}
}
