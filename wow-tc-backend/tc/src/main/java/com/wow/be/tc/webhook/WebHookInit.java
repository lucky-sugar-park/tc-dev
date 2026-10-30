package com.wow.be.tc.webhook;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WebHookInit {
	
	@Autowired
	private WebHookManagementService webHookManagementService;

	@PostConstruct
	public void init() {
		log.info("");
		List<WebHookInfo> webHookList=webHookManagementService.searchAllWebHook();
		WebHookManager manager=WebHookManager.getManager();
		webHookList.forEach(info->{
			WebHook hook=new WebHook();
			hook.setId(info.getId());
			hook.setName(info.getName());
			hook.setUrl(info.getUrl());
			manager.addWebHook(hook);
			log.info("");
		});
	}
}
