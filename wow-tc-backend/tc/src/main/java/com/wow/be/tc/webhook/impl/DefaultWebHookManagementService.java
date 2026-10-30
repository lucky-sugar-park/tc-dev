package com.wow.be.tc.webhook.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wow.be.tc.utils.SearchSpecs;
import com.wow.be.framework.Condition;
import com.wow.be.framework.persist.PersistentService;
import com.wow.be.framework.dispatch.RequestMessage;
import com.wow.be.tc.webhook.WebHook;
import com.wow.be.tc.webhook.WebHookInfo;
import com.wow.be.tc.webhook.WebHookManagementService;
import com.wow.be.tc.webhook.WebHookManager;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DefaultWebHookManagementService implements WebHookManagementService, PersistentService<WebHookInfo, WebHookEntity> {
	
	@Autowired
	private WebHookRepository webHookManagementRepository;

	@Transactional
	@Override
	public void registerWebHook(WebHookInfo webHookInfo) {
		log.debug("DefaultWebHookManagementService.registerWebHook(WebHookInfo webHookInfo)...");
		WebHookEntity entity=new WebHookEntity();
		entity.from(webHookInfo);
		webHookManagementRepository.save(entity);
		WebHookManager.getManager().addWebHook(webHookInfo);
	}
	
	@Override
	public void checkUrl(String url, String sampleData, Map<String, String> resultMap) {
		WebHook webHook=new WebHook();
		webHook.setUrl(url);
		RequestMessage message=new RequestMessage();
		message.setTransactionId(UUID.randomUUID().toString());
		message.setMessageId(UUID.randomUUID().toString());
		message.setOpcode("opcode.webhook.check.url");
		message.setTimestamp(new Date().getTime());
		message.setSender("Tool Control System.");
		message.setReceiver(url);
		
		Gson gson=new GsonBuilder().setPrettyPrinting().create();
		@SuppressWarnings("unchecked")
		Map<String, Object> data=gson.fromJson(sampleData, Map.class);
		message.setData(data);
		try {
			webHook.hook(message);
			resultMap.put("result", "OK");
		} catch (RestClientException rce) {
			resultMap.put("result", "FAIL");
			resultMap.put("cause", rce.getMessage());
		}
	}
	
	@Transactional
	@Override
	public void updateWebHook(WebHookInfo webHookInfo) {
		log.debug("DefaultWebHookManagementService.updateWebHook(WebHookInfo webHookInfo)...");
		WebHookEntity entity=webHookManagementRepository.findById(webHookInfo.getId()).get();
		entity.from(webHookInfo);
		webHookManagementRepository.save(entity);
		WebHookManager.getManager().addWebHook(webHookInfo);
	}

	@Transactional
	@Override
	public void deleteWebHook(long id) {
		log.debug("DefaultWebHookManagementService.deleteWebHook(long id)...");
		webHookManagementRepository.deleteById(id);
	}

	@Transactional
	@Override
	public void deleteWebHook(String name) {
		log.debug("DefaultWebHookManagementService.deleteWebHook(String name)...");
		webHookManagementRepository.deleteByName(name);	
	}

	@Override
	public WebHookInfo findWebHookById(long id) {
		log.debug("DefaultWebHookManagementService.findWebHookById(long id)...");

		try {
			WebHookEntity entity=webHookManagementRepository.findById(id).get();
			return entity.info();
		} catch (NoSuchElementException e) {
			return null;
		}
	}

	@Override
	public WebHookInfo findWebHookByName(String name) {
		log.debug("DefaultWebHookManagementService.findWebHookByName(String name)...");

		WebHookEntity probe=new WebHookEntity();
		probe.setName(name);

		ExampleMatcher matcher = ExampleMatcher.matchingAny();
		matcher=matcher.withMatcher("name", ExampleMatcher.GenericPropertyMatchers.exact());
		Example<WebHookEntity> example=Example.of(probe, matcher);
		
		try {
			WebHookEntity entity=webHookManagementRepository.findOne(example).get();	
			return entity.info();
		} catch (NoSuchElementException e) {
			return null;
		}
	}

	@Override
	public List<WebHookInfo> searchAllWebHook() {
		log.debug("DefaultWebHookManagementService.searchAllWebHook()...");
		List<WebHookEntity> webHookList=webHookManagementRepository.findAll();
		return infos(webHookList);
	}

	@Override
	public List<WebHookInfo> searchWebHook(Condition cond) {
		log.debug("DefaultWebHookManagementService.searchWebHook(SearchCondition cond)...");
		WebHookSpec specs=new WebHookSpec();
		List<WebHookEntity> webHookList = webHookManagementRepository.findAll(SearchSpecs.create(specs, cond));
		return infos(webHookList);
	}
}
