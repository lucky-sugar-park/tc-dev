package com.wow.be.tc.ui.bookmark;

import java.util.Map;

import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 북마크 — mes-dev 와 같은 모양({@code name, pathName, description, state}) */
@Data
@EqualsAndHashCode(callSuper = false)
public class BookmarkInfo extends AbstractInfo {

	/** 응답에만 — 요청의 값은 무시하고 로그인 사용자로 정한다 */
	private String userId;

	private String name;

	private String pathName;

	private String description;

	private Map<String, String> state;

	@Override
	public String toPlainText() {
		return name + " → " + pathName;
	}
}
