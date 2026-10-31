package com.wow.be.tc.interfs;

import java.util.ArrayList;
import java.util.List;

import com.wow.be.framework.AbstractInfo;
import com.wow.be.tc.Types.InterfaceType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@Data
@EqualsAndHashCode(callSuper=false)
public class InterfaceInfo extends AbstractInfo {
	
	/**
	 * 인터페이스 아이디
	 */
	private long id;
	
	/**
	 * Unique 해야 함
	 * example, opcode: plc.example.status.read or plc.example.status.read.reply
	 */
	private String name;
	
	/**
	 * 이 인터페이스 요청을 처리할 command 클래스 이름
	 * - 별도로 지정하지 않으면, 모든 opcode에 대해서 기본적으로 GenericMessageCommand 적용
	 * - UI 상에서 관리자가 선택하는 클래스가 지정됨 (현재는 GenericMessageCommand 하나 밖에 없음-필요할 경우 나중에 추가될 수 있음)
	 */
	private String commandClassName;
	
	private boolean useGenericCommandClass;
	
	/**
	 * WRITE일 경우에는 null 값이고, READ일 경우에는 다른 인터페이스 이름이어야 함
	 * WRITE일 경우에도 default로 정의되어 있는 비정상적으로 끝났을 경우의 메시지를 reply 할 수 있음
	 */
	private String replyName;
	
	/**
	 * 인텨페이스 대상이 되는 PLC 이름 (이름이 더 가독성이 좋음-유일하기 때문에 상관 없음)
	 */
	private String plcName;
	
	/**
	 * READ | WRITE | REPLY | PUSH (via WebHook)
	 */
	private InterfaceType interfaceType;
	
	/**
	 * 헤더정보 (헤더 정보는 인터페이스를 정의할 때에 prop의 값이 할당되어야 함)
	 */
	private List<PropInfo> headerProps;
	
	/**
	 * 실제 데이터. prop의 값은 
	 * - READ일 경우: 인터페이스를 정의할 때에 prop의 값이 할당되어야 함
	 * - WRITE일경우: 실제 WRITE 요청이 들어올 때에 값을 알 수 있음
	 * - READ에 대한 REPLY의 경우: PLC로부터 읽어온 후에 값이 할당되어야 함  
	 */
	private List<PropInfo> dataProps;
	
	/**
	 * data의 총 길이 (READ일 경우에는 0)
	 */
	private int dataLength;
	
	private boolean use;
	
	/**
	 * 비동기 응답은 WebHook을 통해서 해야 함
	 */
	private boolean sync;
	
	/**
	 * 결과 응답여부 (false 면 PLC에 요청 후 처리 결과를 반환하지 않는다)
	 */
	private boolean reply;
	
	/**
	 * 지정하지 않으면 -1로 세팅됨.
	 * sync==true 이면 webHook이 필요 없음 (-1)
	 */
	private List<String> webHookNameList;
	
	private String description;

	/**
	 * 정의 방식 — {@code POINTS}(점 목록) 또는 {@code RAW}(헤더·데이터 바이트 속성, 호환). 등록 때 비우면 RAW,
	 * 수정 때 비우면 저장된 값(docs/plc-protocol-design.md §4.2)
	 */
	private InterfaceMode mode;

	/** POINTS 일 때의 점 목록. 수정 때 null 이면 저장된 점을 그대로 둔다 */
	private List<PointInfo> points;

	/** POINTS WRITE — 쓴 뒤 같은 점을 다시 읽어 응답 data 에 넣는다(결정 P8) */
	private boolean readBack;
	
	public InterfaceInfo() {
		this.headerProps=new ArrayList<>();
		this.dataProps=new ArrayList<>();
		this.webHookNameList=new ArrayList<>();
	}

	public void addHeaderProp(PropInfo prop) {
		this.headerProps.add(prop);
	}
	
	public void addDataProp(PropInfo prop) {
		this.dataProps.add(prop);
	}
	
	public void addWebHookName(String webHookName) {
		webHookNameList.add(webHookName);
	}

	@Override
	public String toPlainText() {
		return new StringBuilder().toString();
	}	
}