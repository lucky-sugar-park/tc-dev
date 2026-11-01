package com.wow.be.tc.vplc.core.connection;

public interface ClientConnectionEventListener {
	
	public String getName();

	public void onClientConnectionChanged(ClientConnectionEvent cce);
}
