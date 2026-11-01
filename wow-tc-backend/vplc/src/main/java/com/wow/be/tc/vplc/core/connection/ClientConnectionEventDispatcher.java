package com.wow.be.tc.vplc.core.connection;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.extern.slf4j.Slf4j;

/**
 * 연결·끊김 이벤트를 리스너(연결 이력 DB 등)에 알린다 — 한 스레드에서 들어온 차례대로, 부른 쪽과 떼어서.
 * 예전에는 부른 스레드에서 바로 알려, 정지 요청이 세션을 닫으면 이력 저장이 정지 요청의 트랜잭션에 섞였고 이력 저장이 실패하면
 * 정지까지 롤백됐다(docs/vplc-stop-error.md).
 */
@Slf4j
public class ClientConnectionEventDispatcher {

	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "vplc-connection-events");
		t.setDaemon(true);
		return t;
	});
	
	private static final ClientConnectionEventDispatcher dispatcher = new ClientConnectionEventDispatcher();
	
	private List<ClientConnectionEventListener> listeners;

	private ClientConnectionEventDispatcher() {
		listeners=new CopyOnWriteArrayList<>();
	}
	
	public static ClientConnectionEventDispatcher getDispatcher() {
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
		worker.execute(() -> listeners.forEach(listener -> {
			try {
				listener.onClientConnectionChanged(event);
			} catch (RuntimeException e) {
				log.warn("연결 이벤트 처리 실패({}): {} {} {} — {}", listener.getName(), event.getVplcId(), event.getPort(), event.getConnOrDisconn(), e.toString());
			}
		}));
	}
}
