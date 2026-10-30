package com.wow.be.tc.interfs.impl;

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
public class PropEntityId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name="INTERFACE_ID")
	private Long interfaceId;
	
	@Column(name="NAME")
	private String name;	
}
