package com.wow.be.tc.ui.code;

import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 공통 코드 — 응답 모양은 mes-dev LOV 와 같다({@code code, name, i18nCode, parent, description, isUse}) */
@Data
@EqualsAndHashCode(callSuper = false)
public class CommonCodeInfo extends AbstractInfo {

	private String code;

	private String name;

	private String i18nCode;

	private String parent;

	private String description;

	private Boolean isUse = Boolean.TRUE;

	@Override
	public String toPlainText() {
		return code + "=" + name;
	}
}
