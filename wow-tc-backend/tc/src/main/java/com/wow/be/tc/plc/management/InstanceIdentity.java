package com.wow.be.tc.plc.management;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.stereotype.Component;

/**
 * 이 tc 인스턴스의 ID — {@code 호스트명-기동시각(ms)}. 같은 호스트에서 재기동해도 다른 ID 라 옛 기록과 섞이지 않는다
 * (docs/plc-ha-design.md §3.2). 연결 상태(Redis)에 "어느 인스턴스가 연결했는가"로 남고, 다중화 3단계의 임대 값으로도 쓴다.
 */
@Component
public class InstanceIdentity {

	private final String id;

	public InstanceIdentity() {
		this.id = hostName() + "-" + System.currentTimeMillis();
	}

	public String id() {
		return id;
	}

	private static String hostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			return "unknown-host";
		}
	}
}
