package com.wow.be.tc.webhook.impl;

import org.springframework.data.jpa.domain.Specification;

import com.wow.be.framework.Condition.CondType;
import com.wow.be.framework.persist.SearchSpec;

public class WebHookSpec extends SearchSpec<WebHookEntity> {

	public Specification<WebHookEntity> withName(CondType condType, String name) {
		return super.with("name", condType, name);
	}
	
	public Specification<WebHookEntity> withUrl(CondType condType, String url) {
		return super.with("url", condType, url);
	}

	public Specification<WebHookEntity> withDescription(CondType condType, String description) {
		return super.with("description", condType, description);
	}
}
