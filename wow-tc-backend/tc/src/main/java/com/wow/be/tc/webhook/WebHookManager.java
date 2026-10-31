package com.wow.be.tc.webhook;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationContext;

import com.wow.be.framework.session.SessionContext;

import lombok.extern.slf4j.Slf4j;

/**
 * WebHook 이름 → 주소 캐시.
 *
 * <p>항목은 {@link #CACHE_TTL_MS} 동안만 쓰고 지나면 DB 에서 다시 읽는다. 다른 tc 인스턴스에서 바꾸거나 지운 WebHook 도
 * 그 시간 안에 따라온다(docs/plc-ha-design.md §9.1 H7). 없는 이름도 같은 시간 동안 기억해, 지운 WebHook 을 가리키는 전달
 * 정의가 전달마다 DB 를 읽지 않게 한다. 이 인스턴스에서 바꾼 것은 관리 서비스가 바로 반영한다.
 */
@Slf4j
public class WebHookManager {

	/** 캐시 항목을 믿는 시간 */
	public static final long CACHE_TTL_MS = 10_000;

	private static final WebHookManager webHookManager = new WebHookManager();

	private volatile ApplicationContext applicationContext;

	/** webHook 이 null 이면 없는 이름 */
	private record Cached(WebHook webHook, long loadedAt) {
	}

	private final Map<String, Cached> hookMap = new ConcurrentHashMap<>();

	private WebHookManager() {
		log.info("WebHookManager singleton instance is created.");
	}

	/**
	 * 처음 만들어지는 때(WebHookInit 의 @PostConstruct)는 전역 컨텍스트가 아직 없어 null 이다. 그때 잡아 두면 캐시에 없는 이름을 찾을 때마다
	 * NPE 가 났다(지운 WebHook 을 쓰는 전달 정의, 재전송) — 쓸 때 가져온다.
	 */
	private WebHookManagementService service() {
		if(applicationContext==null) {
			applicationContext=SessionContext.getGlobalSessionContext().getApplicationContext();
		}
		return applicationContext.getBean(WebHookManagementService.class);
	}

	public static WebHookManager getManager() {
		return webHookManager;
	}

	public void addWebHook(WebHookInfo webHookInfo) {
		log.debug("WebHookManager.addWebHook(WebHookInfo webHookInfo) is called.");
		this.addWebHook(new WebHook(webHookInfo));
	}

	public void addWebHook(WebHook webHook) {
		log.debug("WebHookManager.addWebHook(WebHook webHook) is called.");
		hookMap.put(webHook.getName(), new Cached(webHook, System.currentTimeMillis()));
	}

	/** @return 없으면 null */
	public WebHook getWebHook(String name) {
		log.debug("WebHookManager.getWebHook(String name)...");
		if(name==null) {
			return null;
		}
		long now=System.currentTimeMillis();
		Cached cached=hookMap.get(name);
		if(cached!=null && now-cached.loadedAt()<CACHE_TTL_MS) {
			return cached.webHook();
		}
		WebHookInfo info=service().findWebHookByName(name);
		WebHook webHook=info!=null ? new WebHook(info) : null;
		hookMap.put(name, new Cached(webHook, now));
		return webHook;
	}

	public void removeWebHook(String name) {
		log.debug("");
		hookMap.remove(name);
	}

	public void sync() {
		log.info("WebHookManager.sync() is called.");
		sync(service().searchAllWebHook());
	}

	public void sync(List<WebHookInfo> webHookList) {
		log.info("WebHookManager.sync(List<WebHookInfo> webHookList) is called.");
		hookMap.clear();
		webHookList.forEach(this::addWebHook);
	}
}
