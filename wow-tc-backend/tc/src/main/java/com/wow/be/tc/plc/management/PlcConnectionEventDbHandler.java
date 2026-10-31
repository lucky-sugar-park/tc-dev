package com.wow.be.tc.plc.management;

import com.wow.be.tc.plc.connector.PlcConnectionEventListener;
import com.wow.be.tc.plc.connector.PlcConnectorEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * 어댑터의 연결 이벤트를 받는다 — DB 이력(PLC_CONNECTION_HIST)과 Redis 연결 상태({@link PlcConnectionStateStore})에 남긴다.
 *
 * <p>커넥터의 DB 상태(STATUS1·STATUS2)는 바꾸지 않는다(docs/plc-ha-design.md §3.4). 예전에는 START 에서 PUBLISHED·RUNNING,
 * STOP·SHUTDOWN 에서 RELEASED·STOPPED 로 고쳤는데, 게시·해제는 관리 API 가 이미 같은 값을 쓰고, 그 밖의 경우(한 인스턴스의
 * 연결 정리)에는 운영자 의도를 덮어쓴다 — 여러 인스턴스에서는 다른 인스턴스의 게시 상태까지 바뀐다.
 */
@Slf4j
public class PlcConnectionEventDbHandler implements PlcConnectionEventListener {
	
	private final PlcConnectorManagementService plcConnectorManagementService;

	private final PlcConnectionStateStore stateStore;
	
	public PlcConnectionEventDbHandler(PlcConnectorManagementService plcConnectorManagementService, PlcConnectionStateStore stateStore) {
		this.plcConnectorManagementService=plcConnectorManagementService;
		this.stateStore=stateStore;
	}
	
	@Override
	public void onEvent(PlcConnectorEvent pevent) {
		log.debug("");
		PlcConnectionEventHistInfo hInfo=new PlcConnectionEventHistInfo();
		hInfo.setPlcConnectorId(pevent.getPlcConnectorId());
		hInfo.setPlcConnectorName(pevent.getPlcConnectorName());
		hInfo.setEventType(pevent.getType().name());
		hInfo.setCause(pevent.getCause());
		hInfo.setTimestamp(pevent.getTimestamp());
		hInfo.setEventJson(pevent.json(true));
		hInfo.setDescription(pevent.getDescription());
		plcConnectorManagementService.savePlcConnectionEventHistory(hInfo);

		// 끊긴 채널은 어댑터가 스스로 다시 잇는다 — 커넥터는 게시 상태 그대로다(docs/plc-connection-design.md §5.2)
		stateStore.onEvent(pevent);
	}
}
