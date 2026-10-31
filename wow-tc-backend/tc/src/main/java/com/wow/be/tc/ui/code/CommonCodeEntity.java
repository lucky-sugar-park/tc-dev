package com.wow.be.tc.ui.code;

import com.wow.be.framework.persist.PersistentEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 공통 코드(LOV) — mes-dev {@code COMMON_CODE} 와 같은 칼럼(docs/frontend-design.md §2.2·§4.4). 그룹은 {@code common.code.<그룹>},
 * 항목은 {@code common.code.<그룹>.<값>}, 이름의 다국어는 {@code i18n.<코드>}.
 */
@Entity
@Table(name = "COMMON_CODE", indexes = @Index(name = "IX_COMMON_CODE_PARENT", columnList = "PARENT"))
@Data
@EqualsAndHashCode(callSuper = false)
public class CommonCodeEntity extends PersistentEntity<CommonCodeInfo> {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "CODE", length = 255)
	private String code;

	@Column(name = "NAME", length = 255)
	private String name;

	@Column(name = "I18N_CODE", length = 255)
	private String i18nCode;

	@Column(name = "PARENT", length = 255)
	private String parent;

	@Column(name = "DESCRIPTION", length = 1024)
	private String description;

	@Column(name = "IS_USE")
	private Boolean use;

	boolean inUse() {
		return use == null || use;
	}

	@Override
	public CommonCodeInfo info() {
		CommonCodeInfo info = new CommonCodeInfo();
		info.setCode(code);
		info.setName(name);
		info.setI18nCode(i18nCode);
		info.setParent(parent);
		info.setDescription(description);
		info.setIsUse(inUse());
		info.setCreatedBy(createdBy);
		info.setCreatedDate(createdDate);
		info.setUpdatedBy(updatedBy);
		info.setUpdatedDate(updatedDate);
		return info;
	}

	@Override
	public void from(CommonCodeInfo info) {
		this.code = info.getCode();
		this.name = info.getName();
		this.i18nCode = info.getI18nCode();
		this.parent = info.getParent();
		this.description = info.getDescription();
		this.use = info.getIsUse() == null || info.getIsUse();
	}

	@Override
	public String toPlainText() {
		return code + "=" + name;
	}
}
