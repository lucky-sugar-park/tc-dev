package com.wow.be.tc.webhook;

import java.util.List;
import java.util.Map;

import com.wow.be.framework.Condition;

public interface WebHookManagementService {
	
	public abstract void registerWebHook(WebHookInfo webHookInfo);
	
	public abstract void checkUrl(String url, String sampleData, Map<String, String> resultMap);
	
	public abstract void updateWebHook(WebHookInfo webHookInfo);
	
	public abstract void deleteWebHook(long id);
	
	public abstract void deleteWebHook(String name);

	public abstract WebHookInfo findWebHookById(long id);
	
	public abstract WebHookInfo findWebHookByName(String name);
	
	public abstract List<WebHookInfo> searchAllWebHook();
	
	public abstract List<WebHookInfo> searchWebHook(Condition cond);
}
