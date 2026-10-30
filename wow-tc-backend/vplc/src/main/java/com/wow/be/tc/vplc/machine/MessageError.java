package com.wow.be.tc.vplc.machine;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
public class MessageError extends Message {
	
	private byte errCode1;
	
	private byte errCode2;
	
	private byte errCode3;
	
	private byte errCode4;
	
	private byte respNetNumber1;
	
	private byte respNetNumber2;
	
	private byte respPlcNumber1;
	
	private byte respPlcNumber2;
	
	private byte respOpponentIONumber1;
	
	private byte respOpponentIONumber2;
	
	private byte respOpponentIONumber3;
	
	private byte respOpponentIONumber4;
	
	private byte respOpponentNumber1;
	
	private byte respOpponentNumber2;
	
	private byte respCommand1;
	
	private byte respCommand2;
	
	private byte respCommand3;
	
	private byte respCommand4;
	
	private byte respSubCommand1;
	
	private byte respSubCommand2;
	
	private byte respSubCommand3;
	
	private byte respSubCommand4;
	
	public MessageError() {
		super();
	}

	@Override
	public String forString() {
		return null;
	}

	@Override
	public void extract(byte[] data) {
		// nothing to do...
	}

	@Override
	public byte[] toByteArray() {
		return null;
	}

	@Override
	public String toJson() {
		// TODO Auto-generated method stub
		return null;
	}
}
