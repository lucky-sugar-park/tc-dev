package com.wow.be.tc.vplc.machine;

public interface ClientConnectionEventListener {
	
	public String getName();

	public void onClientConnectionChanged(ClientConnectionEvent cce);
}
