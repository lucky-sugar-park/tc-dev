package com.wow.be.tc.interfs;

import java.util.List;
import java.util.Map;

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
	 * example, opcode: plc.example.status.read
	 */
	private String name;
	
	/**
	 * 인텨페이스 대상이 되는 PLC 이름 (이름이 더 가독성이 좋음-유일하기 때문에 상관 없음)
	 */
	private String plcName;
	
	/**
	 * READ | WRITE
	 */
	private InterfaceType interfaceType;
	
	private boolean use;
	
	private String description;

	/** 점 목록(이름·주소·형 …) — 프로토콜 드라이버가 요청 프레임으로 묶는다. 수정 때 null 이면 저장된 점을 그대로 둔다 */
	private List<PointInfo> points;

	/** WRITE — 쓴 뒤 같은 점을 다시 읽어 응답 data 에 넣는다(결정 P8) */
	private boolean readBack;

	/**
	 * 인터페이스 settings — 커넥터 설정을 덮어쓰는 것(MELSEC 국 번호, Modbus unitId …)과 인터페이스 전용(mergeGap, writeFunction).
	 * 키는 프로토콜 설명의 interfaceSettings. 비면 커넥터 설정 그대로(docs/interface-detail-design.md §3)
	 */
	private Map<String, Object> settings;
	
	@Override
	public String toPlainText() {
		return new StringBuilder().toString();
	}	
}