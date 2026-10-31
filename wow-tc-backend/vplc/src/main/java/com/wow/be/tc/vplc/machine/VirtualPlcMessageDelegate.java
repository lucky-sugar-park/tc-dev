package com.wow.be.tc.vplc.machine;

import java.util.UUID;

import com.wow.be.framework.pbox.Postbox;
import com.wow.be.framework.pbox.PostboxHandler;
import com.wow.be.framework.pbox.PostboxManager;
import com.wow.be.framework.pbox.PostboxMessage;
import com.wow.be.tc.vplc.Types.FrameFormat;
import com.wow.be.tc.vplc.Types.MemoryType;
import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.VirtualPlcException;

import lombok.extern.slf4j.Slf4j;

/**
 * 가상 PLC 하나의 요청 처리 담당.
 * PLC 마다 Postbox(framework pbox) 하나를 두고, 받은 요청을 그 큐에 넣어 PLC 단위로 한 건씩 순서대로 처리한다.
 */
@Slf4j
public class VirtualPlcMessageDelegate implements PostboxHandler<PostboxMessage>{

	private VirtualProgrammableController vplc;

	private Postbox<PostboxMessage> pbox;

	public VirtualPlcMessageDelegate (VirtualProgrammableController vplc) {
		this.vplc=vplc;
		this.vplc.setMessageDelegate(this);
		// sleepTime 50ms, 바로 시작 (레거시 getPostbox(name, handler, true, 50) 과 같다)
		this.pbox=PostboxManager.getManager().getPostbox("PBOX-"+vplc.getId(), this, 50, true);
	}

	/**
	 * vplc로 ClientChannelStatusChangeEventHandler를 전파한다. 
	 * vplc에서 이 핸드러를 통해서 포트당 연결가능한 커넥션 수를 전달해 주기 위함임
	 * 
	 * @param channelEventHandler
	 */
	public void dispatchChannelEventHandler(ClientChannelStatusChangeEventHandler channelEventHandler) {
		log.debug("VirtualPlcMessageDelegate.dispatchChannelEventHandler(ClientChannelStatusChangeEventHandler channelEventHandler)...");

		if(vplc!=null) {
			vplc.setChannelEventHandler(channelEventHandler);
		}
	}

	/**
	 * TC와 같은 다른 시스템으로부터의 읽기 쓰기 요청 메시지를 처리한다
	 * 비동기식으로 처리하기 위해서 Postbox에 넣는다. Postbox에서 onMessage를 호출해 준다
	 * 
	 * @param message
	 * @param callback
	 * @throws VirtualPlcException
	 */
	public void processMessage(Message message, ResponseMessageCallback responseMessageCallback) throws VirtualPlcException {
		log.debug("VirtualPlcMessageDelegate.processMessage(Message message, MessageCallback callback)...");

		if(vplc.getStatus()!=PlcStatus.RUNNING) {
			// 멈춘(PAUSED) 가상 PLC 는 답하지 않는다 — 연결은 살아 있는데 PLC 가 답하지 않는 상태를 흉내 낸다(tc 의 연결 확인·시간 초과 시험).
			// 예전에는 머리 없는 응답을 보내 tc 가 해석하지 못했다
			log.debug("가상 PLC 가 RUNNING 이 아니라 요청에 답하지 않습니다: {}", vplc.getStatus());
			return;
		}

		// Postbox 메시지에 callback 함수를 넣어준다 (비동기 처리를 위함)
		message.setResponseMessageCallback(responseMessageCallback);
		this.pbox.postMessage(message);
	}

	/**
	 * Postbox 작업 스레드가 호출한다. 레거시는 처리 결과를 boolean 으로 돌려줬지만, Postbox 에 callback 을 걸지 않아
	 * 그 값을 쓰는 곳이 없었다. framework {@link PostboxHandler} 계약(void)에 맞춰 반환값만 없앴다.
	 */
	@Override
	public void onMessage(PostboxMessage pboxMessage) {
		log.debug("VirtualPlcMessageDelegate.onMessage(PostboxMessage pboxMessage)..., MSG:\n{}", pboxMessage.toJson());

		if(pboxMessage instanceof McMessage mc) {
			byte[] frame=McResponder.handle(mc.getRequest(), vplc);
			McMessage response=new McMessage(null, frame);
			response.setClientChannelId(mc.getClientChannelId());
			response.setFormat(vplc.getMessageFrameFormat());
			response.setTransactionId(mc.getTransactionId());
			mc.getResponseMessageCallback().onResponse(response);
			return;
		}
		MessageRequest request=(MessageRequest)pboxMessage;
		try {
			MessageResponse response=new MessageResponse();
			MemoryType memType=null;
			if(vplc.getMessageFrameFormat()==FrameFormat.BINARY || vplc.getMessageFrameFormat()==FrameFormat.UNKNOWN) {
				memType=MemoryType.toMemoryType(request.getDeviceCode1());
			} else if(vplc.getMessageFrameFormat()==FrameFormat.ASCII){
				memType=MemoryType.toMemoryType(new String(new byte[] { request.getDeviceCode1(), request.getDeviceCode2() }));
			}
			
			switch(request.getReqType()) {
			case READ:
				byte reads[]=vplc.read(request.getStartAddress(), request.getWordCount(), memType);
				response.setData(reads);
				break;
			case WRITE:
				vplc.write(request.getStartAddress(), request.getWordCount(), request.getData(), memType);
				response.setData(new byte[] {});
				break;
			default:
				break;
			}
			if(FrameFormat.BINARY==vplc.getMessageFrameFormat()) {
				response.setErrCode1((byte)0x00); response.setErrCode2((byte)0x00);				
			} else {
				String errCode="0000";
				byte[] bytes=errCode.getBytes();
				response.setErrCode1(bytes[0]); response.setErrCode1(bytes[1]);
				response.setErrCode1(bytes[2]); response.setErrCode1(bytes[3]);
			}
			response.setTransactionId(request.getTransactionId());
			response.setMessageId(UUID.randomUUID().toString());
			response.setClientChannelId(request.getClientChannelId());
			response.setFormat(vplc.getMessageFrameFormat());
			response.apply(request);

			// 메시지로부터 Callback 함수를 꺼낸 후에 - 요청에 대한 처리결과를 Client로 전송해 준다
			request.getResponseMessageCallback().onResponse(response);
		} catch (VirtualPlcException ve) {
			log.warn("Fail to handle pbox message: \n{}", pboxMessage.toJson());
			MessageError error=new MessageError();
			if(FrameFormat.BINARY==vplc.getMessageFrameFormat()) {
				error.setErrCode1((byte)0x00); error.setErrCode2((byte)0x01);				
			} else {
				String errCode="0001";
				byte[] bytes=errCode.getBytes();
				error.setErrCode1(bytes[0]); error.setErrCode1(bytes[1]);
				error.setErrCode1(bytes[2]); error.setErrCode1(bytes[3]);
			}
			request.getResponseMessageCallback().onResponse(error);
		}
	}
	
	public VirtualProgrammableController getPlc() {
		return this.vplc;
	}
	
	public void startPbox() {
		this.pbox.doStart();
	}

	public void pausePbox() {
		this.pbox.doPause();
	}

	public void resumePbox() {
		this.pbox.doResume();
	}

	public void standbyPbox() {
		this.pbox.doStandby();
	}
	
	public void release() {
		this.standbyPbox();
		PostboxManager.getManager().removePostbox(this.pbox.getPboxName());
	}
}
