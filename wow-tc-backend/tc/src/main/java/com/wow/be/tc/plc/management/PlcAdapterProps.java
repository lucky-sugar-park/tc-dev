package com.wow.be.tc.plc.management;

import java.util.Properties;

import com.wow.be.tc.Types.PlcConnectorOperStatus;
import com.wow.be.tc.plc.connector.PlcConnectionEventListener;

/**
 * 어댑터를 올릴 때 넘기는 속성. 기동(PlcConnectorLoader)·게시 API·소유권 관리자가 같은 값을 쓰게 한 곳에서 만든다.
 */
public final class PlcAdapterProps {

	private PlcAdapterProps() {
	}

	/**
	 * @param background PLC 에 아직 연결하지 못해도 어댑터를 올리고 뒤에서 연결한다(기동·소유권). false 면 연결 실패를 바로 알린다(게시 API)
	 */
	public static Properties of(PlcConnectorInfo info, PlcConfig plcConfig, PlcConnectionEventListener listener, boolean background) {
		Properties props=new Properties();
		props.put("id", info.getId());
		props.put("name", info.getName());
		props.put("className", info.getAdapterClassName());
		props.put("hostIp", info.getHostIp());
		props.put("targetPorts", info.getPorts());
		props.put("localVips", info.getConnectionVips());
		props.put("messageFrameFormat", info.getMessageFrameFormat().getName());
		props.put("headerReqTemplate", info.getCommProtocolHeadersReqTemplate());
		props.put("headerResTemplate", info.getCommProtocolHeadersResTemplate());
		// 운영자 의도(STATUS1=PUBLISHED)로 올린다 — STATUS2 는 마지막 제어 결과일 뿐 실제 연결 상태가 아니다(docs/plc-ha-design.md §3.4)
		props.put("operStatus", PlcConnectorOperStatus.RUNNING.name());
		props.put("plcRetryCount", plcConfig.getRetryCount());
		props.put("plcRetryIntervalRate", plcConfig.getRetryIntervalRate());
		props.put("plcRetryIntervalIncrement", plcConfig.getRetryIntervalIncrement());
		props.put("plcRequestTimeoutMs", plcConfig.getRequestTimeoutMs());
		props.put("startEvenIfDisconnected", background);
		props.put("connectionListener", listener);
		return props;
	}
}
