package com.wow.be.tc.vplc.management.impl;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

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
	
	@Column(name="CLIENT_IP_ADDRESS")
	private String clientIp;

}
