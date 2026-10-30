package com.wow.be.tc.plc;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.FrameFormat;
import com.wow.be.tc.Types.PropType;
import com.wow.be.tc.Types.RequestType;
import com.wow.be.tc.interfs.PropInfo;
import com.wow.be.tc.utils.ByteUtil.Endian;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public abstract class PlcRequest {

	private String reqId;

	private String reqName;
	
	private Date timestamp;
	
	private RequestType reqType;
	
	private FrameFormat frameFormat;
	
	private Map<String, Object> headers;
	
	private byte[] data;
	
	public PlcRequest() {
		headers=new HashMap<>();
	}
	
	public void addHeader(String name, Object value) {
		headers.put(name, value);
	}
	
	public abstract byte[] convertRequest() throws ToolControlException;
	
	public void extractHeader(List<PropInfo> props) throws ToolControlException {
		log.debug("");
		props.sort((a,b)->{;
			return a.getOrder()-b.getOrder();
		});

		for(Iterator<PropInfo> iter=props.iterator();iter.hasNext(); ) {
			PropInfo prop=iter.next();
			checkHeaderProp(prop);
			this.addHeader(prop.getName(), prop.getValue());
		}
		log.debug("");
	}
	
	public void extractData(List<PropInfo> propDefs, int dataLen, Map<String, Object> realData) throws ToolControlException {
		log.debug("");
		
		if(propDefs==null || propDefs.size()<=0) {
			return;
		}

		// props order 순서로 오름차순 정렬함 (PLC에 정확한 값을 전달하기 위해서는 반드시 지켜져야 함)
		propDefs.sort((a,b)->{
			return a.getOrder()-b.getOrder();
		});
		ByteBuffer bb=ByteBuffer.allocate(dataLen);

		for(Iterator<PropInfo> iter=propDefs.iterator();iter.hasNext(); ) {
			PropInfo prop=iter.next();
			Object strRealValue=realData.get(prop.getName());
			checkDataProp(prop, strRealValue);
						
			byte[] bytes=prop.extractRealDataAsBytes(strRealValue, Endian.LITTLE);
			bb.put(bytes);
		}
		this.setData(bb.array());
		if(dataLen != this.data.length) {
			throw new ToolControlException("요청 데이터 길이가 인터페이스 정의와 다릅니다: " + this.reqName + " (정의 " + dataLen + ", 실제 " + this.data.length + ")");
		}
	}
	
	protected void checkHeaderProp(PropInfo prop) throws ToolControlException {
		if(prop.getType()==PropType.BYTE) {
			byte[] bytes=(byte[])prop.getValue();
			if(bytes.length!=prop.getLength()) {
				throw new ToolControlException("헤더 길이가 정의와 다릅니다: " + prop.getName() + " (정의 " + prop.getLength() + ", 실제 " + bytes.length + ")");
			}
		} else if(prop.getType()==PropType.STRING) {
			String str=(String)prop.getValue();
			if(str.length()!=prop.getLength()) {
				throw new ToolControlException("헤더 길이가 정의와 다릅니다: " + prop.getName() + " (정의 " + prop.getLength() + ", 실제 " + str.length() + ")");
			}
		}
	}
	
	protected void checkDataProp(PropInfo prop, Object realValue) throws ToolControlException {
		if(realValue==null) {
			throw new ToolControlException("데이터 값은 필수입니다: " + prop.getName());
		}
		if(realValue instanceof String) {
			String strValue=(String) realValue;
			if(strValue.length()!=prop.getLength()) {
				throw new ToolControlException("데이터 길이가 정의와 다릅니다: " + prop.getName() + " (정의 " + prop.getLength() + ", 실제 " + strValue.length() + ")");
			}			
		}
	}
}
