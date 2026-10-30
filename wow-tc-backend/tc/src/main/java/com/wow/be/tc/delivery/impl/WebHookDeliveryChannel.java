package com.wow.be.tc.delivery.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.wow.be.tc.delivery.DeliveryConfig;
import com.wow.be.tc.delivery.DeliveryTarget;
import com.wow.be.tc.delivery.ResultDeliveryChannel;
import com.wow.be.tc.dispatch.TcMessageRequest;
import com.wow.be.tc.dispatch.TcMessageResponse;

/**
 * 응답 인터페이스에 등록된 WebHook 으로 결과를 POST 한다. 레거시와 같은 메서드(POST)다.
 */
@Component
public class WebHookDeliveryChannel implements ResultDeliveryChannel {

	private final RestClient restClient;

	public WebHookDeliveryChannel(@Qualifier(DeliveryConfig.DELIVERY_REST_CLIENT) RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public DeliveryTarget.DeliveryType type() {
		return DeliveryTarget.DeliveryType.WEBHOOK;
	}

	@Override
	public void deliver(DeliveryTarget target, TcMessageRequest request, TcMessageResponse response) {
		restClient.post()
			.uri(target.url())
			.contentType(MediaType.APPLICATION_JSON)
			.body(response)
			.retrieve()
			.toBodilessEntity();
	}
}
