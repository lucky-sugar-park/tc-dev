package com.wow.be.tc.plc;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.FrameFormat;
import com.wow.be.tc.Types.PropType;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.PropInfo;
import com.wow.be.tc.utils.ByteUtil.Endian;
import com.wow.be.tc.utils.ByteUtil;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public abstract class PlcResponse {

	private String reqId;
	
	private FrameFormat messageFrameFormat;
	
	private boolean success;
	
	private boolean arrived;
	
	private int resultCode;
	
	private String resultDescription;
	
	protected Map<String, Object> replyHeaders;
	
	protected Map<String, Object> replyData;
	
	protected byte[] orgRespAllData;
	
	protected byte[] orgData;
	
	private InterfaceInfo replyInterfaceInfo;
	
	public PlcResponse () {
		replyData=new HashMap<>();
		replyHeaders=new HashMap<>();
	}
	
	public abstract void splitHeaderAndReplyData(byte[] plcResponse) throws ToolControlException;
	
	public void extractReplypData(byte[] oriReplyData) throws ToolControlException, UnsupportedEncodingException {
		log.debug("PlcResponse.extractRespData(byte[] oriRespData)...");

		if(this.replyInterfaceInfo==null || replyInterfaceInfo.getDataLength()!=orgData.length) {
			throw new ToolControlException("응답 인터페이스가 없거나 응답 데이터 길이가 정의와 다릅니다.");
		}

		List<PropInfo> dataDefs=this.replyInterfaceInfo.getDataProps();
		dataDefs.sort((a,b)->{
			return a.getOrder()-b.getOrder();
		});

		int idx=0;
		for(Iterator<PropInfo> iter=this.replyInterfaceInfo.getDataProps().iterator();iter.hasNext(); ) {
			PropInfo prop=iter.next();
			int len=prop.getLength();
			if(len>1 && prop.getType()==PropType.BOOLEAN) {
				throw new ToolControlException("BOOLEAN 속성의 길이는 1이어야 합니다: " + prop.getName() + " (" + len + ")");
			}

			byte[] bytes=new byte[len];
			for(int i=0;i<len;i++) bytes[i]=oriReplyData[idx++];

			Object realValue=null;
			switch(prop.getType()) {
			case BOOLEAN:
				realValue=bytes[0]==0x01?true:false;
				break;
			case SHORT:
				realValue=ByteUtil.bytesToShort(bytes, Endian.LITTLE);
				break;
			case INT:
				realValue=ByteUtil.bytesToInt(bytes, Endian.LITTLE);
				break;
			case LONG:
				realValue=ByteUtil.bytesToLong(bytes, Endian.LITTLE);
				break;
			case DOUBLE:
				realValue=ByteUtil.bytesToFloat(bytes, Endian.LITTLE);
				break;
			case STRING:
				realValue=new String(bytes, "UTF-8");
				break;
			case BYTE:
			default:
				// BYTE
				realValue=bytes;
				break;
			}
			replyData.put(prop.getName(), realValue);
		}		
	}
}
