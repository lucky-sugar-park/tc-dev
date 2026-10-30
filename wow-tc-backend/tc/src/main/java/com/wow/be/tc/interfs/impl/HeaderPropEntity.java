package com.wow.be.tc.interfs.impl;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.PropType;
import com.wow.be.tc.interfs.PropInfo;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString
@Builder
@Entity
@Table(name="HEADER_PROP")
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
@Slf4j
public class HeaderPropEntity extends PersistentEntity<PropInfo> {

	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private PropEntityId propId;
	
	@Column(name="ORDER_SEQ")
	private Integer order;
	
	@ManyToOne(fetch=FetchType.LAZY)
//	@MapsId("interfaceId")
	@JoinColumn(name="INTERFACE_ID", referencedColumnName="ID", nullable=false, updatable=false, insertable=false)
	private InterfaceEntity interfs;
	
	@Column(name="TYPE")
	private String type;
	
	@Column(name="VALUE")
	private String value;
	
	@Column(name="LENGTH")
	private Integer length;
	
	@Column(name="DESCRIPTION", length=1024)
	private String description;
	
	public HeaderPropEntity() {
		super();
	}
	
	@Override
	public PropInfo info() {
		PropInfo pInfo=new PropInfo();
		pInfo.setName(propId.getName());
		pInfo.setOrder(order!=null?order:0);
		pInfo.setInterfaceId(propId.getInterfaceId());
		pInfo.setType(PropType.valueOf(type));
		try {
			pInfo.setValue(value);
		} catch (ToolControlException e) {
			e.printStackTrace();
		}
		pInfo.setStrValue(value);
		pInfo.setLength(length);
		pInfo.setDescription(description);
		pInfo.setCreatedBy(createdBy);
		pInfo.setCreatedDate(createdDate);
		pInfo.setUpdatedBy(updatedBy);
		pInfo.setUpdatedDate(updatedDate);

		return pInfo;
	}

	@Override
	public void from(PropInfo info) {
		propId=new PropEntityId();
		propId.setInterfaceId(info.getInterfaceId());
		propId.setName(info.getName());
		
		this.setOrder(info.getOrder());
		this.setType(info.getType().name());
		// REST 로 받은 PropInfo 는 strValue 만 채워지고 value 는 null 이다(value 는 @JsonIgnore).
		// value 로만 저장하면 빈 문자열이 저장돼 PLC 요청 헤더가 비므로, value 가 없으면 strValue 를 저장한다.
		this.setValue(info.getValue()!=null?info.getValueAsString():info.getStrValue());
		this.setLength(info.getLength());
		this.setDescription(info.getDescription());
		this.setCreatedBy(info.getCreatedBy());
		this.setCreatedDate(info.getCreatedDate());
		this.setUpdatedBy(info.getUpdatedBy());
		this.setUpdatedDate(info.getUpdatedDate());
	}

	@Override
	public String toPlainText() {
		log.debug("");
		return null;
	}

}
