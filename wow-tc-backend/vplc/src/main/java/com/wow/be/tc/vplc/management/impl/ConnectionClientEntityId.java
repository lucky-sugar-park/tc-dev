package com.wow.be.tc.vplc.management.impl;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 붙어 있는 연결 하나의 키 — (가상 PLC, 포트, 연결 아이디). 예전에는 (가상 PLC, 포트, 클라이언트 IP)라 같은 PC 의 연결 여러 개가
 * 한 줄로 겹쳐, 먼저 끊긴 연결이 줄을 지우면 다음 끊김이 지울 줄을 못 찾았다(docs/vplc-stop-error.md).
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ConnectionClientEntityId implements Serializable {

	private static final long serialVersionUID = 1L;
	
	@Column(name="VPLC_ID")
	private String vplcId;
	
	@Column(name="PORT")
	private Integer port;
	
	@Column(name="CLIENT_CHANNEL_ID", length=200)
	private String clientChannelId;

}
