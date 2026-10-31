package com.wow.be.tc.ui.i18n;

import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 다국어 문구 한 줄 */
@Data
@EqualsAndHashCode(callSuper = false)
public class I18nItemInfo extends AbstractInfo {

	private String code;

	/** KO·EN·JA·ZH */
	private String langCode;

	private String parent;

	private String kind;

	private String value;

	private String description;

	private boolean use = true;

	@Override
	public String toPlainText() {
		return code + "[" + langCode + "]=" + value;
	}
}
