package com.wow.be.tc.plc.management;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlcConfig {

	@Value("${plc.connection.retry.count}")
	private int retryCount;
	
	@Value("${plc.connection.retry.interval.rate}")
	private long retryIntervalRate;
	
	@Value("${plc.connection.retry.interval.increment}")
	private boolean retryIntervalIncrement;

	/** PLC 요청 하나의 시간 초과(ms). 쉬는 채널을 기다린 시간도 들어간다. 시간 초과가 나면 그 연결을 닫고 다시 연결한다. */
	@Value("${plc.request.timeout-ms:20000}")
	private long requestTimeoutMs;
	
	public int getRetryCount() {
		return this.retryCount;
	}
	
	public long getRetryIntervalRate () {
		return this.retryIntervalRate;
	}
	
	public boolean getRetryIntervalIncrement() {
		return this.retryIntervalIncrement;
	}

	public long getRequestTimeoutMs() {
		return this.requestTimeoutMs;
	}
}
