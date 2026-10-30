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
	
	public int getRetryCount() {
		return this.retryCount;
	}
	
	public long getRetryIntervalRate () {
		return this.retryIntervalRate;
	}
	
	public boolean getRetryIntervalIncrement() {
		return this.retryIntervalIncrement;
	}
}
