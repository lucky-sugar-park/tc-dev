package com.wow.be.tc.interfs.impl;

import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.plc.protocol.ByteOrder;
import com.wow.be.tc.plc.protocol.PointType;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 인터페이스의 점(INTERFACE_POINT, 결정 P2) — 키는 (인터페이스 ID, 점 이름).
 */
@Entity
@Table(name="INTERFACE_POINT")
@Data
@ToString(exclude="interfs")
@EqualsAndHashCode(exclude="interfs")
public class InterfacePointEntity {

	@EmbeddedId
	private PropEntityId pointId;

	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="INTERFACE_ID", referencedColumnName="ID", nullable=false, updatable=false, insertable=false)
	private InterfaceEntity interfs;

	@Column(name="SEQ")
	private Integer order;

	@Column(name="ADDRESS", length=200, nullable=false)
	private String address;

	@Column(name="TYPE", length=16, nullable=false)
	private String type;

	@Column(name="CNT")
	private Integer count;

	@Column(name="LEN")
	private Integer length;

	@Column(name="BYTE_ORDER", length=8)
	private String byteOrder;

	@Column(name="WORD_ORDER", length=8)
	private String wordOrder;

	@Column(name="DESCRIPTION", length=1024)
	private String description;

	public PointInfo info() {
		PointInfo p=new PointInfo();
		p.setName(pointId.getName());
		p.setOrder(order!=null?order:0);
		p.setAddress(address);
		p.setType(PointType.valueOf(type));
		p.setCount(count!=null && count>0?count:1);
		p.setLength(length!=null?length:0);
		p.setByteOrder(byteOrder!=null?ByteOrder.valueOf(byteOrder):null);
		p.setWordOrder(wordOrder!=null?ByteOrder.valueOf(wordOrder):null);
		p.setDescription(description);
		return p;
	}

	public static InterfacePointEntity of(long interfaceId, PointInfo p) {
		InterfacePointEntity e=new InterfacePointEntity();
		e.setPointId(new PropEntityId(interfaceId, p.getName()));
		e.setOrder(p.getOrder());
		e.setAddress(p.getAddress().trim());
		e.setType(p.getType().name());
		e.setCount(p.getCount());
		e.setLength(p.getLength());
		e.setByteOrder(p.getByteOrder()!=null?p.getByteOrder().name():null);
		e.setWordOrder(p.getWordOrder()!=null?p.getWordOrder().name():null);
		e.setDescription(p.getDescription());
		return e;
	}
}
