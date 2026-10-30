package com.wow.be.tc.webhook;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;


import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class WebHook {
	
	private static RestTemplate restTemplate=new RestTemplate();
	
	private long id;
	
	private String name;

	private String url;
	
	public WebHook() {
	}
	
	public WebHook(WebHookInfo webHookInfo) {
		this.id=webHookInfo.getId();
		this.name=webHookInfo.getName();
		this.url=webHookInfo.getUrl();
	}
	
	/** 메시지를 이 WebHook 주소로 POST 한다. 타임아웃이 없으므로 화면의 주소 확인(check-url) 용도로만 쓴다 — 결과 전달은 WebHookDeliveryChannel 이 한다. */
	public void hook(Object message) {
		log.debug("");

		HttpHeaders headers=new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<Object> request=new HttpEntity<>(message, headers);
		
		restTemplate.postForEntity(url, request, String.class);
	}
}
