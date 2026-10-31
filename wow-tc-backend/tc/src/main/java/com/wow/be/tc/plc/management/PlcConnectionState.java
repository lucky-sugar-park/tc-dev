package com.wow.be.tc.plc.management;

import java.util.Date;

import lombok.Data;

/**
 * PLC 커넥터의 지금 연결 상태 — 연결을 가진 인스턴스가 Redis 에 남긴 값(docs/plc-ha-design.md §3.4).
 * 운영자 의도(게시·해제)는 DB {@code STATUS1} 이고, 이것은 실제로 연결이 어떤지다.
 */
@Data
public class PlcConnectionState {

	private String plcConnectorName;

	/** 연결을 가진 인스턴스({@link InstanceIdentity}) */
	private String instance;

	/** 어댑터의 운영 상태(RUNNING, PAUSED, STOPPED …) */
	private String operStatus;

	/** 살아 있는 채널 수. 0 이면 어댑터는 올라와 있지만 PLC 와 연결되지 않은 상태(뒤에서 재연결 중) */
	private int activeChannels;

	/** 마지막 연결 이벤트(START, CONNECT, DISCONNECT …)와 시각 */
	private String lastEvent;

	private Date lastEventAt;

	/** 마지막으로 채널이 연결된 시각 */
	private Date lastConnectedAt;

	/** 마지막 끊김 사유와 시각 */
	private String lastError;

	private Date lastErrorAt;

	/** 이 값을 마지막으로 갱신한 시각. 인스턴스가 죽으면 갱신이 멈추고 만료(기본 15초)로 사라진다 */
	private Date updatedAt;
}
