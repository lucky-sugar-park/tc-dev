package com.wow.be.tc.vplc.opcua;

import java.util.Date;

import com.google.gson.Gson;
import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer.Simulation;
import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer.TagSpec;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

/**
 * 가상 OPC UA 서버의 태그 정의(docs/virtual-devices.md) — 가상 PLC 하나에 여러 개. 처음 값은 JSON(형에 맞춰 바꾼다).
 * 값 흉내는 종류·주기·최소·최대. 지금 값은 메모리에만 있다(MELSEC 메모리와 같다).
 */
@Data
@Entity
@Table(name = "VPLC_OPCUA_TAG", uniqueConstraints = @UniqueConstraint(columnNames = { "VPLC_ID", "PATH" }))
public class OpcUaTagEntity {

	@Id
	@Column(name = "ID", length = 80)
	private String id;

	@Column(name = "VPLC_ID", length = 80, nullable = false)
	private String vplcId;

	@Column(name = "PATH", length = 200, nullable = false)
	private String path;

	@Column(name = "TYPE", length = 20, nullable = false)
	private String type;

	@Column(name = "INIT_VALUE", length = 1000)
	private String initValue;

	@Column(name = "SIM_KIND", length = 20)
	private String simKind;

	@Column(name = "SIM_PERIOD_MS")
	private Long simPeriodMs;

	@Column(name = "SIM_MIN")
	private Double simMin;

	@Column(name = "SIM_MAX")
	private Double simMax;

	@Column(name = "DESCRIPTION", length = 500)
	private String description;

	@Column(name = "CREATED_DATE")
	private Date createdDate;

	public TagSpec spec() {
		Object v = initValue != null ? new Gson().fromJson(initValue, Object.class) : null;
		Simulation sim = simKind != null ? new Simulation(simKind, simPeriodMs, simMin, simMax) : null;
		return new TagSpec(path, TagType.valueOf(type), v, sim, description);
	}

	public static OpcUaTagEntity of(String vplcId, TagSpec spec) {
		OpcUaTagEntity e = new OpcUaTagEntity();
		e.setId(vplcId + ":" + spec.path());
		e.setVplcId(vplcId);
		e.setPath(spec.path());
		e.setType(spec.type().name());
		e.setInitValue(spec.value() != null ? new Gson().toJson(spec.value()) : null);
		if (spec.simulation() != null && spec.simulation().kind() != null) {
			e.setSimKind(spec.simulation().kind().toUpperCase());
			e.setSimPeriodMs(spec.simulation().periodMs());
			e.setSimMin(spec.simulation().min());
			e.setSimMax(spec.simulation().max());
		}
		e.setDescription(spec.description());
		e.setCreatedDate(new Date());
		return e;
	}
}
