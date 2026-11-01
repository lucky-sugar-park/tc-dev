package com.wow.be.tc.vplc.core;

import java.util.Date;

import com.google.gson.JsonObject;
import com.wow.be.framework.sse.EventPusher;
import com.wow.be.framework.sse.PushEventPayload;
import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.VirtualPlcException;

import lombok.extern.slf4j.Slf4j;

/**
 * 가상 PLC 상태 규칙 한 곳(docs/vplc-architecture.md §2.2) — 종류는 {@code doStart}·{@code doStop}·{@code doPause}·{@code doResume}·{@code doRelease} 만 구현한다.
 * <ul>
 *   <li>시작: PUBLISHED·STOPPED 에서만 → RUNNING</li>
 *   <li>일시정지: RUNNING 에서만 → PAUSED, 재개: PAUSED 에서만 → RUNNING</li>
 *   <li>정지: 실행 중(RUNNING·PAUSED)이면 포트를 닫고, 그 밖은 상태만 → STOPPED</li>
 *   <li>해제: 실행 중이면 정지한 뒤 → RELEASED</li>
 * </ul>
 * 다른 상태에서 시작·일시정지·재개는 409. 상태가 바뀌면 SSE 로 알린다.
 */
@Slf4j
public abstract class AbstractVirtualPlc implements VirtualPlc {

	private final String id;

	private final String name;

	private volatile PlcStatus status = PlcStatus.PUBLISHED;

	protected AbstractVirtualPlc(String id, String name) {
		this.id = id;
		this.name = name;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public PlcStatus getStatus() {
		return status;
	}

	/** 실행 중(포트가 열려 있음) — RUNNING·PAUSED */
	public boolean isRunning() {
		return status == PlcStatus.RUNNING || status == PlcStatus.PAUSED;
	}

	@Override
	public final synchronized void start() throws VirtualPlcException {
		require("시작", PlcStatus.PUBLISHED, PlcStatus.STOPPED);
		doStart();
		changed(PlcStatus.RUNNING, VirtualPlcEventKind.VPLC_STARTED);
	}

	@Override
	public final synchronized void stop() throws VirtualPlcException {
		if (isRunning()) {
			doStop();
		}
		changed(PlcStatus.STOPPED, VirtualPlcEventKind.VPLC_STOPPED);
	}

	@Override
	public final synchronized void pause() throws VirtualPlcException {
		require("일시정지", PlcStatus.RUNNING);
		doPause();
		changed(PlcStatus.PAUSED, VirtualPlcEventKind.VPLC_PAUSED);
	}

	@Override
	public final synchronized void resume() throws VirtualPlcException {
		require("재개", PlcStatus.PAUSED);
		doResume();
		changed(PlcStatus.RUNNING, VirtualPlcEventKind.VPLC_RESUMED);
	}

	@Override
	public final synchronized void release() throws VirtualPlcException {
		if (isRunning()) {
			doStop();
		}
		doRelease();
		status = PlcStatus.RELEASED;
	}

	/** 포트를 열고 답하기 시작한다 */
	protected abstract void doStart() throws VirtualPlcException;

	/** 포트를 닫는다(메모리 값은 남는다) */
	protected abstract void doStop() throws VirtualPlcException;

	/** 연결은 두고 답하지 않는다 */
	protected abstract void doPause() throws VirtualPlcException;

	protected abstract void doResume() throws VirtualPlcException;

	/** 메모리에서 내릴 때 더 정리할 것(정지 뒤) */
	protected void doRelease() throws VirtualPlcException {
	}

	private void require(String what, PlcStatus... allowed) throws VirtualPlcException {
		for (PlcStatus s : allowed) {
			if (status == s) {
				return;
			}
		}
		throw new VirtualPlcException(VirtualPlcException.ERR_409, what + "할 수 없는 상태입니다: " + name + " (" + status + ")");
	}

	private void changed(PlcStatus to, VirtualPlcEventKind kind) {
		status = to;
		try {
			PushEventPayload event = new PushEventPayload() {
				@Override
				public String toJson() {
					JsonObject json = new JsonObject();
					json.addProperty("vplcId", id);
					json.addProperty("vplcName", name);
					json.addProperty("protocol", getProtocol().name());
					json.addProperty("status", to.name());
					json.addProperty("timestamp", new Date().toString());
					return json.toString();
				}
			};
			event.setEventKind(kind.getName());
			EventPusher.newPusher().push(event);
		} catch (RuntimeException e) {
			log.debug("상태 알림 실패: {} — {}", name, e.toString());
		}
	}
}
