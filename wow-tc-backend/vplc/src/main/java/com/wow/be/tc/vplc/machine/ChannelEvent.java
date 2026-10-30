package com.wow.be.tc.vplc.machine;

import java.beans.PropertyChangeEvent;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
public class ChannelEvent extends PropertyChangeEvent {

	private static final long serialVersionUID = 1L;
	
	public enum ChannelEventName { ACTIVE, INACTIVE };
	
	private ClientChannel clientChannel;
	
	public ChannelEvent(Object source, String propertyName, Object newValue) {
		this(source, propertyName, null, newValue);
	}

	public ChannelEvent(Object source, String propertyName, Object oldValue, Object newValue) {
		super(source, propertyName, oldValue, newValue);
		if(newValue instanceof ClientChannel) {
			this.clientChannel=(ClientChannel)newValue;
		}
	}
}
