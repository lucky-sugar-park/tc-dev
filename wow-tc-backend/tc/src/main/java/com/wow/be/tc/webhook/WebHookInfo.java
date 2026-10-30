package com.wow.be.tc.webhook;

import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * WebHook은 모두 POST 방식만 지원함
 * 
 * @author insoo67.park
 */
@ToString
@Data
@EqualsAndHashCode(callSuper=false)
public class WebHookInfo extends AbstractInfo {
	
	private long id;
	
	/**
	 * name은 유일해야 함.
	 * 예) system.name.objective.webhook
	 */
	private String name;

	private String url;
	
	private String status;
	
	private String sampleData;
	
	private String description;
	
	public WebHookInfo() {
		super();
	}
	
	@Override
	public String toPlainText() {
		return null;
	}
}
