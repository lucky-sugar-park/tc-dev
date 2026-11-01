package com.wow.be.tc.vplc.core.connection;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientConnectionEventDispatcher {
	
	private static ClientConnectionEventDispatcher dispatcher;
	
	private List<ClientConnectionEventListener> listeners;

	private ClientConnectionEventDispatcher() {
		listeners=new ArrayList<>();
	}
	
	public static ClientConnectionEventDispatcher getDispatcher() {
		if(dispatcher==null) {
			dispatcher=new ClientConnectionEventDispatcher();
		}
		return dispatcher;
	}
	
	public void addEventListener(ClientConnectionEventListener listener) {
		listeners.add(listener);
	}
	
	public void removeEventListener(String name) {
		listeners.removeIf(listener->{
			if(listener.getName().equals(name)) return true;
			return false;
		});
	}
	
	public void dispatch(ClientConnectionEvent event) {
		log.debug("");
		listeners.forEach(listener->{
			listener.onClientConnectionChanged(event);
		});
	}
}
