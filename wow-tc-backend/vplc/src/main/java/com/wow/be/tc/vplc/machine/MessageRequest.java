package com.wow.be.tc.vplc.machine;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
public class MessageRequest extends Message {
	
	public enum RequestType {
		READ, WRITE
	}
	
	private RequestType reqType;
	
	private byte timer1;
	
	private byte timer2;
	
	private byte timer3;
	
	private byte timer4;
	
	private byte command1;
	
	private byte command2;
	
	private byte command3;
	
	private byte command4;
	
	private byte subCommand1;
	
	private byte subCommand2;
	
	private byte subCommand3;
	
	private byte subCommand4;
	
	// 3 byte 또는 6 byte로 표현해야 함 
	private int startAddress;
	
	private byte deviceCode1;
	
	private byte deviceCode2;
	
	// 2 byte 또는 4 byte로 표현되어야 함
	private int wordCount;
	
	private byte[] data;

	@Override
	public String forString() {
		return null;
	}

	@Override
	public void extract(byte[] data) {
		// extract and assign to properties
		
	}

	@Override
	public byte[] toByteArray() {
		// nothing to do...
		return null;
	}

	@Override
	public String toJson() {
		// TODO Auto-generated method stub
		return null;
	}
}
