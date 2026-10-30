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
		applicationContext=SessionContext.getGlobalSessionContext().getApplicationContext();
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
			WebHookManagementService whmService=(WebHookManagementService)applicationContext.getBean(WebHookManagementService.class);
			WebHookInfo info=whmService.findWebHookByName(name);
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
		WebHookManagementService whmService=(WebHookManagementService)applicationContext.getBean(WebHookManagementService.class);
		sync(whmService.searchAllWebHook());
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
