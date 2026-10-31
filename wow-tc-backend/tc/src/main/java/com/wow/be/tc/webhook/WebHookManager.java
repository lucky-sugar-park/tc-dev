package com.wow.be.tc.webhook;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationContext;

import com.wow.be.framework.session.SessionContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebHookManager {

	private static WebHookManager webHookManager;
	
	private ApplicationContext applicationContext;
	
	private Map<String, WebHook> hookMap;

	private WebHookManager() {
		log.info("WebHookManager singleton instance is created.");
		hookMap=new HashMap<>();
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
		if(webHookManager==null) {
			webHookManager=new WebHookManager();
		}
		return webHookManager;
	}
	
	public void addWebHook(WebHookInfo webHookInfo) {
		log.debug("WebHookManager.addWebHook(WebHookInfo webHookInfo) is called.");
		this.addWebHook(new WebHook(webHookInfo));
	}

	public void addWebHook(WebHook webHook) {
		log.debug("WebHookManager.addWebHook(WebHook webHook) is called.");
		hookMap.put(webHook.getName(), webHook);
	}

	public WebHook getWebHook(String name) {
		log.debug("WebHookManager.getWebHook(String name)...");
		WebHook webHook=hookMap.get(name);
		if(webHook==null) {
			WebHookInfo info=service().findWebHookByName(name);
			if(info!=null) {
				webHook=new WebHook();
				webHook.setId(info.getId());
				webHook.setName(info.getName());
				webHook.setUrl(info.getUrl());

				hookMap.put(name, webHook);
			}
		}
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

		webHookList.forEach(info->{
			WebHook hook=new WebHook();
			hook.setId(info.getId());
			hook.setName(info.getName());
			hook.setUrl(info.getUrl());
			addWebHook(hook);
			log.info("");
		});
	}
}
