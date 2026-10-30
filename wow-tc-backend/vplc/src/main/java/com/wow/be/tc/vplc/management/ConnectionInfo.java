package com.wow.be.tc.vplc.management;

import java.util.ArrayList;
import java.util.List;

import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Data
@EqualsAndHashCode(callSuper=false)
@ToString
@Slf4j
public class ConnectionInfo extends AbstractInfo {
	
	/**
	 * VPLC 아이디
	 */
	private String vplcId;

	/**
	 * 포트번호
	 */
	private int port;
	
	/**
	 * 일반적으로는 1 또는 0이 정상임
	 * 대부분의 PLC에서는 하나의 포트당 하나의 클라이언트 접속만 허용하기 때문임
	 */
	private int connectedCount;
	
	/**
	 * connectedClientList가 하나 이상이면 true임
	 * 대부분의 PLC에서는 하나의 포트당 하나의 클라이언트 접속만 허용함
	 */
	private boolean connected;
	
	/**
	 * 대부분의 PLC에서는 하나의 포트당 하나의 클라이언트 접속만 허용함
	 */
	private List<ConnectionClientInfo> connectedClientList;
	
//	/**
//	 * 커넥션 클라이언트 이력목록
//	 */
//	private List<ConnectionClientHistoryInfo> connectionClientHistoryList;
	
	public ConnectionInfo() {
		connectedClientList=new ArrayList<>();
//		connectionClientHistoryList=new ArrayList<>();
	}
	
	public void addConnectedClient(ConnectionClientInfo connClient) {
		this.connectedClientList.add(connClient);
	}
	
	public void removeConnectedClient(ConnectionClientInfo client) {
		for(ConnectionClientInfo aClient:connectedClientList) {
			if(aClient.getClientIp().equals(client.getClientIp()) && aClient.getPort()==client.getPort()) {
				connectedClientList.remove(aClient);
				return;
			}
		}
	}

	@Override
	public String toPlainText() {
		log.debug("");
		return null;
	}
}
