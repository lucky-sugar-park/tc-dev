package com.wow.be.tc.plc.connector.melsec;

import java.io.UnsupportedEncodingException;
import java.util.List;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.PropInfo;
import com.wow.be.tc.plc.PlcResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MelsecPlcResponse extends PlcResponse {

	public MelsecPlcResponse () {
		super();
	}

	@Override
	public void splitHeaderAndReplyData(byte[] plcResponse) throws ToolControlException {
		log.debug("MelsecPlcResponse.splitHeaderAndReplyData(byte[] plcResponse) for req: ", super.getReqId());

		InterfaceInfo riInfo=super.getReplyInterfaceInfo();
		if(riInfo==null) return;

		List<PropInfo> replyHeaders=riInfo.getHeaderProps();
		replyHeaders.sort((a, b)->{
			return a.getOrder()-b.getOrder();
		});

		int idx=0, j=0;
		for(int i=0; i<replyHeaders.size();i++) {
			PropInfo header=replyHeaders.get(i);
			int len=header.getLength();
			byte[] bytes=new byte[len];
			j=0;
			while(j<len) {
				bytes[j++]=plcResponse[idx++];
			}
			super.getReplyHeaders().put(header.getName(), bytes);
		}
		
		if(riInfo.getDataLength()!=(plcResponse.length-idx)) {
			throw new ToolControlException("PLC 응답 데이터 길이가 응답 인터페이스 정의와 다릅니다: " + riInfo.getName() + " (정의 " + riInfo.getDataLength() + ", 실제 " + (plcResponse.length-idx) + ")");
		}
		
		try {
			super.orgRespAllData=new byte[plcResponse.length];
			System.arraycopy(plcResponse, 0, super.orgRespAllData, 0, plcResponse.length);
		
			super.orgData=new byte[plcResponse.length-idx];
			System.arraycopy(plcResponse, idx, super.orgData, 0, riInfo.getDataLength());
		
			super.extractReplypData(super.orgData);
		} catch (UnsupportedEncodingException e) {
			log.warn("", e);
			throw new ToolControlException("PLC 응답 데이터를 해석하지 못했습니다: " + riInfo.getName(), e);
		}
	}
}
