package com.wow.be.tc.vplc.machine;

import java.nio.ByteBuffer;

import com.wow.be.tc.vplc.Types.FrameFormat;
import com.wow.be.tc.vplc.utils.ByteUtil;
import com.wow.be.tc.vplc.utils.ByteUtil.Endian;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
public class MessageResponse extends Message {
	
	private static int RESP_HEADER_LEN_BINARY=11;
	
	private static int RESP_HEADER_LEN_ASCIII=22;

	private byte errCode1;
	
	private byte errCode2;
	
	private byte errCode3;
	
	private byte errCode4;
	
	private byte[] data;

	@Override
	public String forString() {
		return null;
	}

	@Override
	public void extract(byte[] data) {
		// Nothing to do...
	}
	
	public void apply(MessageRequest req) {
		super.setSubHeader1((byte)0xD0);
		super.setSubHeader2((byte)0x00);
		if(super.getFormat()==FrameFormat.ASCII) {
			super.setSubHeader1(req.getSubHeader3());
			super.setSubHeader2(req.getSubHeader4());
		}
		
		super.setNetNumber1(req.getNetNumber1());
		if(super.getFormat()==FrameFormat.ASCII) {
			super.setNetNumber2(req.getNetNumber2());
		}
		
		super.setPlcNumber1(req.getPlcNumber1());
		if(super.getFormat()==FrameFormat.ASCII) {
			super.setPlcNumber2(req.getPlcNumber2());
		}
		
		super.setOpponentIONumber1(req.getOpponentIONumber1());
		super.setOpponentIONumber2(req.getOpponentIONumber2());
		if(super.getFormat()==FrameFormat.ASCII) {
			super.setOpponentIONumber3(req.getOpponentIONumber3());
			super.setOpponentIONumber4(req.getOpponentIONumber4());
		}

		super.setOpponentNumber1(req.getOpponentNumber1());
		if(super.getFormat()==FrameFormat.ASCII) {
			super.setOpponentNumber2(req.getOpponentNumber2());
		}

		int respLen=this.getData()==null?0:this.getData().length;
		if(super.getFormat()==FrameFormat.ASCII) {
			respLen+=4;
			byte[] dataLenArr=ByteUtil.intToBytes(respLen, 2, Endian.BIG);
			StringBuffer sb=new StringBuffer();
			for(byte b:dataLenArr) {
				sb.append(Integer.toString((b&0xff) + 0x100, 16).substring(1));
			}
			byte[] dataLenAscii=sb.toString().toUpperCase().getBytes();
			super.setDataLen1(dataLenAscii[0]);
			super.setDataLen2(dataLenAscii[2]);
			super.setDataLen3(dataLenAscii[3]);
			super.setDataLen4(dataLenAscii[4]);
		} else {
			respLen+=2;
			byte[] lenArrBin=ByteUtil.intToBytes(respLen, 2, Endian.LITTLE);
			super.setDataLen1(lenArrBin[0]);
			super.setDataLen2(lenArrBin[1]);
		}
	}

	@Override
	public byte[] toByteArray() {
		switch(super.getFormat()) {
		case ASCII:
			return toAsciiByteArray();
		case BINARY:
		default:
			return toBinaryByteArray();
		}
	}
	
	private byte[] toAsciiByteArray() {
		ByteBuffer bb=ByteBuffer.allocate(data.length+RESP_HEADER_LEN_ASCIII);
		bb.put(super.getSubHeader1());
		bb.put(super.getSubHeader2());
		bb.put(super.getSubHeader3());
		bb.put(super.getSubHeader4());
		bb.put(super.getNetNumber1());
		bb.put(super.getNetNumber2());
		bb.put(super.getPlcNumber1());
		bb.put(super.getPlcNumber2());
		bb.put(super.getOpponentIONumber1());
		bb.put(super.getOpponentIONumber2());		
		bb.put(super.getOpponentIONumber3());
		bb.put(super.getOpponentIONumber4());
		bb.put(super.getOpponentNumber1());
		bb.put(super.getOpponentNumber2());
		bb.put(super.getDataLen1());
		bb.put(super.getDataLen2());
		bb.put(super.getDataLen3());
		bb.put(super.getDataLen4());
		bb.put(this.getErrCode1());
		bb.put(this.getErrCode2());
		bb.put(this.getErrCode3());
		bb.put(this.getErrCode4());

		return bb.array();
	}
	
	private byte[] toBinaryByteArray() {
		ByteBuffer bb=ByteBuffer.allocate(data.length+RESP_HEADER_LEN_BINARY);
		bb.put(super.getSubHeader1());
		bb.put(super.getSubHeader2());
		bb.put(super.getNetNumber1());
		bb.put(super.getPlcNumber1());
		bb.put(super.getOpponentIONumber1());
		bb.put(super.getOpponentIONumber2());		
		bb.put(super.getOpponentNumber1());
		bb.put(super.getDataLen1());
		bb.put(super.getDataLen2());
		bb.put(this.getErrCode1());
		bb.put(this.getErrCode2());
		bb.put(this.getData());

		return bb.array();
	}

	@Override
	public String toJson() {
		// TODO Auto-generated method stub
		return null;
	}
}
