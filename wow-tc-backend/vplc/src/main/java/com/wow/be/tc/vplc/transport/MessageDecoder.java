package com.wow.be.tc.vplc.transport;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.wow.be.tc.vplc.Types.FrameFormat;
import com.wow.be.tc.vplc.machine.Message;
import com.wow.be.tc.vplc.machine.MessageRequest;
import com.wow.be.tc.vplc.machine.MessageRequest.RequestType;
import com.wow.be.tc.vplc.utils.ByteUtil;
import com.wow.be.tc.vplc.utils.ByteUtil.Endian;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageDecoder extends ByteToMessageDecoder {

	private static int BINARY_FORMAT_MIN_HEADER_LEN=21;

	private static int BINARY_FORMAT_DATE_LEN_POS_FROM=7;

	private static int BINARY_FORMAT_DATE_LEN_POS_TO=8;

	private static int ASCIII_FORMAT_MIN_HEADER_LEN=42;

	private static int ASCIII_FORMAT_DATE_LEN_POS_FROM=14;

	private static int ASCIII_FORMAT_DATE_LEN_POS_TO=17;

	private static int SHORT_LEN_MESSAGE_FRAME=-1;

	private FrameFormat messageFrameFormat;

	public MessageDecoder(FrameFormat messageFrameFormat) {
		log.info("MessageDecoder.MessageDecoder(FrameFormat messageFrameFormat)..., {}", messageFrameFormat.name());
		this.messageFrameFormat=messageFrameFormat;
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		log.debug("MessageDecoder.decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)...");
		String remoteAddress=ctx.channel().remoteAddress().toString();
		log.debug("Client channelId: {}, remoteAddress: {}", ctx.channel().id().asLongText(), remoteAddress);

		int len=SHORT_LEN_MESSAGE_FRAME;
		switch(messageFrameFormat) {
		case ASCII:
			len=readAsciiiFormat (in);
			break;
		case BINARY:
		default:
			len=readBinaryFormat (in);
			break;
		}

		if(len!=SHORT_LEN_MESSAGE_FRAME) {
			byte[] frame=new byte[len];
			in.readBytes(frame);

			if(messageFrameFormat==FrameFormat.BINARY) {
				if(frame[0]!=0x50 || frame[1]!=0x00) {
					log.warn("Wrong message from {}, message should start with 0x500x00", remoteAddress);
					return;
				}
			} else if(messageFrameFormat==FrameFormat.ASCII) {
				if(frame[0]!='5' || frame[1]!='0' || frame[2]!='0' || frame[3]!='0') {
					log.warn("Wrong message from {}, message should start with 5000", remoteAddress);
					return;
				}
			}

			Message message=extractMessage(frame);
			message.setClientChannelId(ctx.channel().id().asLongText());
			message.setFormat(messageFrameFormat);

			Payload payload=new Payload();
			payload.setTransactionId(UUID.randomUUID().toString());
			payload.setMessageId(UUID.randomUUID().toString());
			payload.setChannelId(ctx.channel().id().asLongText());
			payload.setTimestamp(new Date().getTime());
			payload.setRawMessage(frame);
			payload.setMessage(message);
			
			message.setTransactionId(payload.getTransactionId());
			message.setMessageId(payload.getMessageId());

			out.add(payload);
		}
	}

	private int readBinaryFormat(ByteBuf in) {
		if(in.readableBytes()<BINARY_FORMAT_MIN_HEADER_LEN) return SHORT_LEN_MESSAGE_FRAME;

		// 이 부분에서 unsupportedOperationException 발생함: direct buffer가 메시지 임
		int size=in.readableBytes();
		byte[] arr=new byte[size];
		in.getBytes(in.readerIndex(), arr);
		byte[] len=new byte[2];
		len[0]=arr[BINARY_FORMAT_DATE_LEN_POS_FROM];
		len[1]=arr[BINARY_FORMAT_DATE_LEN_POS_TO];
		int dataLen=ByteUtil.bytesToInt(len, Endian.LITTLE);
		if(arr.length<BINARY_FORMAT_DATE_LEN_POS_TO+1+dataLen) return SHORT_LEN_MESSAGE_FRAME;

		return BINARY_FORMAT_DATE_LEN_POS_TO+1+dataLen;
	}

	private Message extractMessage(byte[] request) {
		if(messageFrameFormat==FrameFormat.BINARY) {
			return extractMessageWithBinaryFormat(request);
		} else if (messageFrameFormat==FrameFormat.ASCII) {
			return extractMessageWithAsciiiFormat(request);
		} else {
			return extractMessageWithBinaryFormat(request);
		}
	}

	private Message extractMessageWithBinaryFormat(byte[] request) {
		int idx=0;
		MessageRequest message=new MessageRequest();
		message.setSubHeader1(request[idx++]);
		message.setSubHeader2(request[idx++]);
		message.setNetNumber1(request[idx++]);
		message.setPlcNumber1(request[idx++]);
		message.setOpponentIONumber1(request[idx++]);
		message.setOpponentIONumber2(request[idx++]);
		message.setOpponentNumber1(request[idx++]);
		message.setDataLen1(request[idx++]);
		message.setDataLen2(request[idx++]);
		message.setTimer1(request[idx++]);
		message.setTimer2(request[idx++]);
		message.setCommand1(request[idx++]);
		message.setCommand2(request[idx++]);
		message.setSubCommand1(request[idx++]);
		message.setSubCommand2(request[idx++]);

		if(message.getCommand2()==0x14) message.setReqType(RequestType.WRITE);
		else message.setReqType(RequestType.READ);
		
		byte[] startAddr=new byte[3];
		startAddr[0]=request[idx++];
		startAddr[1]=request[idx++];
		startAddr[2]=request[idx++];
		
		message.setStartAddress(ByteUtil.bytesToInt(startAddr, Endian.LITTLE));
		message.setDeviceCode1(request[idx++]);
		
		byte[] wordCount=new byte[2];
		wordCount[0]=request[idx++];
		wordCount[1]=request[idx++];
		
		message.setWordCount(ByteUtil.bytesToInt(wordCount, Endian.LITTLE));

		byte[] data=new byte[request.length-idx];
		int pos=0;
		while(idx<request.length) data[pos++]=request[idx++];
		message.setData(data);
		message.setOriginalData(request);

		return message;
	}

	private Message extractMessageWithAsciiiFormat(byte[] request) {
		int idx=0;
		MessageRequest message=new MessageRequest();
		message.setSubHeader1(request[idx++]);
		message.setSubHeader2(request[idx++]);
		message.setSubHeader3(request[idx++]);
		message.setSubHeader4(request[idx++]);
		message.setNetNumber1(request[idx++]);
		message.setNetNumber2(request[idx++]);
		message.setPlcNumber1(request[idx++]);
		message.setPlcNumber2(request[idx++]);
		message.setOpponentIONumber1(request[idx++]);
		message.setOpponentIONumber2(request[idx++]);
		message.setOpponentIONumber3(request[idx++]);
		message.setOpponentIONumber4(request[idx++]);
		message.setOpponentNumber1(request[idx++]);
		message.setOpponentNumber2(request[idx++]);
		message.setDataLen1(request[idx++]);
		message.setDataLen2(request[idx++]);
		message.setDataLen3(request[idx++]);
		message.setDataLen4(request[idx++]);
		message.setTimer1(request[idx++]);
		message.setTimer2(request[idx++]);
		message.setTimer3(request[idx++]);
		message.setTimer4(request[idx++]);
		message.setCommand1(request[idx++]);
		message.setCommand2(request[idx++]);
		message.setCommand3(request[idx++]);
		message.setCommand4(request[idx++]);
		message.setSubCommand1(request[idx++]);
		message.setSubCommand2(request[idx++]);
		message.setSubCommand3(request[idx++]);
		message.setSubCommand4(request[idx++]);

		if(message.getCommand3()==0x31 && message.getCommand4()==0x34) message.setReqType(RequestType.WRITE);
		else message.setReqType(RequestType.READ);

		byte[] data=new byte[request.length-idx];
		int pos=0;
		while(idx<request.length) data[pos++]=request[idx++];
		message.setData(data);
		message.setOriginalData(request);

		return message;
	}

	private int readAsciiiFormat(ByteBuf in) {
		if(in.readableBytes()<ASCIII_FORMAT_MIN_HEADER_LEN) return SHORT_LEN_MESSAGE_FRAME;

		int size=in.readableBytes();
		byte[] arr=new byte[size];
		in.getBytes(in.readerIndex(), arr);
		byte[] len=new byte[4];
		len[3]=arr[ASCIII_FORMAT_DATE_LEN_POS_FROM];
		len[2]=arr[ASCIII_FORMAT_DATE_LEN_POS_FROM+1];
		len[1]=arr[ASCIII_FORMAT_DATE_LEN_POS_FROM+2];
		len[0]=arr[ASCIII_FORMAT_DATE_LEN_POS_TO];
		int dataLen=Integer.parseInt(new String(len));
		if(arr.length<ASCIII_FORMAT_DATE_LEN_POS_TO+1+dataLen) return SHORT_LEN_MESSAGE_FRAME;

		return ASCIII_FORMAT_DATE_LEN_POS_TO+1+dataLen;
	}
}
