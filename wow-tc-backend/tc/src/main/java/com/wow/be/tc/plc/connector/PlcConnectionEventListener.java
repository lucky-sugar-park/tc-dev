package com.wow.be.tc.plc.connector;

public interface PlcConnectionEventListener {

	public abstract void onEvent(PlcConnectorEvent pevent);
}
