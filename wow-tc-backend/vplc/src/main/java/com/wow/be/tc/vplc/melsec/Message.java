package com.wow.be.tc.vplc.melsec;

import com.wow.be.tc.vplc.melsec.MelsecTypes.FrameFormat;
import com.wow.be.framework.pbox.PostboxMessage;

import lombok.Data;

/**
 * MC 3E 프레임 메시지(요청·응답·오류)의 공통 헤더.
 * PLC 마다 하나씩 있는 Postbox(framework pbox)에 넣어 PLC 단위로 순서대로 처리하므로 {@link PostboxMessage} 를 구현한다.
 */
@Data
public abstract class Message implements PostboxMessage {
	
	private String transactionId;
	
	private String messageId;
	
	private String clientChannelId;
	
	private FrameFormat format;
	
	private byte[] originalData;
	
	// subHeader1 + subHeader2 = binary 타입일 경우 2 바이트, ascii 타입일 경우 4 바이트
	// 요청: 5000/0x50,0x00, 응답: D000/0xD0,0x00
	private byte subHeader1;
	
	private byte subHeader2;
	
	private byte subHeader3;
	
	private byte subHeader4;
	
	private byte netNumber1;
	
	private byte netNumber2;
	
	private byte plcNumber1;
	
	private byte plcNumber2;
	
	private byte opponentIONumber1;
	
	private byte opponentIONumber2;
	
	private byte opponentIONumber3;
	
	private byte opponentIONumber4;
	
	private byte opponentNumber1;
	
	private byte opponentNumber2;
	
	private byte dataLen1;
	
	private byte dataLen2;
	
	private byte dataLen3;
	
	private byte dataLen4;
	
	private ResponseMessageCallback responseMessageCallback;

	public abstract String forString();
	
	public abstract void extract(byte[] data);
	
	public abstract byte[] toByteArray();

	/**
	 * framework {@code Message} 계약. 레거시 PostboxMessage 에는 없던 메서드라 {@link #forString()} 을 그대로 돌려준다.
	 */
	@Override
	public String toPlainText() {
		return forString();
	}
}
