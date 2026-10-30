package com.wow.be.tc.plc.connector.melsec;

import java.util.Date;
import java.util.List;

import com.wow.be.tc.plc.connector.Payload;
import com.wow.be.tc.utils.ByteUtil.Endian;
import com.wow.be.tc.utils.ByteUtil;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyMessageDecoder extends ByteToMessageDecoder {
	
	private static int BINARY_FORMAT_RESP_MIN_HEADER_LEN=11;
	
	private static int BINARY_FORMAT_RESP_DATA_LEN_POS_FROM=7;
	
	private static int BINARY_FORMAT_RESP_DATA_LEN_POS_TO=8;
	
	private static int ASCIII_FORMAT_RESP_MIN_HEADER_LEN=22;
	
	private static int ASCIII_FORMAT_RESP_DATA_LEN_POS_FROM=14;
	
	private static int ASCIII_FORMAT_RESP_DATA_LEN_POS_TO=17;
	
	private static int SHORT_OF_LEN_RESP_MESSAGE_FRAME=-1;
	
	private String messageFrameFormat;
	
	public NettyMessageDecoder() {
		super();
	}
	
	public NettyMessageDecoder (String messageFrameFormat) {
		super();
		this.messageFrameFormat=messageFrameFormat;
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		log.debug("MessageDecoder.decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) is called.");

		int len=SHORT_OF_LEN_RESP_MESSAGE_FRAME;
		switch(messageFrameFormat) {
		case "BINARY":
			len=readBinaryFormat(in);
			break;
		case "ASCII":
			len=readAsciiFormat(in);
			break;
		default:
			len=readBinaryFormat(in);
		}

		if(len!=SHORT_OF_LEN_RESP_MESSAGE_FRAME) {
			byte[] frame=new byte[len];
			in.readBytes(frame);
			
			if(messageFrameFormat.equals("BINARY")) {
				if(frame[0]!=(byte)0xD0 || frame[1]!=(byte)0x00) {
					log.warn("");
					return;
				}	
			} else if(messageFrameFormat.equals("ASCII")) {
				if(frame[0]!=0x44 || frame[1]!=0x30 || frame[2]!=0x30 || frame[3]!=0x30) {
					log.warn("");
					return;
				}
			}
	
			Payload payload=new Payload();
			payload.setTimestamp(new Date().getTime());
			payload.setFrameFormat(messageFrameFormat);
			payload.setData(frame);

			out.add(payload);
		}
	}
	
	private int readBinaryFormat(ByteBuf in) {
		if(in.readableBytes()<BINARY_FORMAT_RESP_MIN_HEADER_LEN) {
			return SHORT_OF_LEN_RESP_MESSAGE_FRAME;
		}
		
		int size=in.readableBytes();
		byte[] arr=new byte[size];
		in.getBytes(in.readerIndex(), arr);
		byte[] len=new byte[2];
		len[0]=arr[BINARY_FORMAT_RESP_DATA_LEN_POS_FROM];
		len[1]=arr[BINARY_FORMAT_RESP_DATA_LEN_POS_TO];
		int dataLen=ByteUtil.bytesToInt(len, Endian.LITTLE);
		
		if(arr.length<BINARY_FORMAT_RESP_DATA_LEN_POS_TO+1+dataLen) {
			return SHORT_OF_LEN_RESP_MESSAGE_FRAME;
		}

		return BINARY_FORMAT_RESP_DATA_LEN_POS_TO+1+dataLen;
	}
	
	private int readAsciiFormat(ByteBuf in) {
		if(in.readableBytes()<ASCIII_FORMAT_RESP_MIN_HEADER_LEN) {
			return SHORT_OF_LEN_RESP_MESSAGE_FRAME;
		}

		byte[] arr=in.array();
		byte[] len=new byte[4];
		len[3]=arr[ASCIII_FORMAT_RESP_DATA_LEN_POS_FROM];
		len[2]=arr[ASCIII_FORMAT_RESP_DATA_LEN_POS_FROM+1];
		len[1]=arr[ASCIII_FORMAT_RESP_DATA_LEN_POS_FROM+2];
		len[0]=arr[ASCIII_FORMAT_RESP_DATA_LEN_POS_TO];
		int dataLen=Integer.parseInt(new String(len));

		if(dataLen<ASCIII_FORMAT_RESP_DATA_LEN_POS_TO+1+dataLen) {
			return SHORT_OF_LEN_RESP_MESSAGE_FRAME;
		}

		return ASCIII_FORMAT_RESP_DATA_LEN_POS_TO+1+dataLen;
	}
}
