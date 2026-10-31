package com.wow.be.tc.ui.i18n;

import java.io.Serializable;

import com.wow.be.framework.persist.PersistentEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 다국어 문구 한 줄(코드·언어) — mes-dev {@code I18N_ITEM} 과 같은 칼럼(docs/frontend-design.md §2.2·§4.4).
 * KIND 는 MES 처럼 전체 LOV 코드(예 {@code common.code.i18n.kind.label}).
 */
@Entity
@Table(name = "I18N_ITEM", indexes = { @Index(name = "IX_I18N_ITEM_KIND", columnList = "KIND"),
		@Index(name = "IX_I18N_ITEM_LANG", columnList = "LANG_CODE") })
@Data
@EqualsAndHashCode(callSuper = false)
public class I18nItemEntity extends PersistentEntity<I18nItemInfo> {

	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private Key id;

	@Column(name = "PARENT", length = 255)
	private String parent;

	@Column(name = "KIND", length = 255)
	private String kind;

	@Column(name = "VALUE", length = 2000)
	private String value;

	@Column(name = "DESCRIPTION", length = 1024)
	private String description;

	/** NULL 은 사용으로 본다(MES 와 같음) */
	@Column(name = "IS_USE")
	private Boolean use;

	@Embeddable
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Key implements Serializable {

		private static final long serialVersionUID = 1L;

		@Column(name = "CODE", length = 255)
		private String code;

		@Column(name = "LANG_CODE", length = 8)
		private String langCode;
	}

	boolean inUse() {
		return use == null || use;
	}

	@Override
	public I18nItemInfo info() {
		I18nItemInfo info = new I18nItemInfo();
		info.setCode(id.getCode());
		info.setLangCode(id.getLangCode());
		info.setParent(parent);
		info.setKind(kind);
		info.setValue(value);
		info.setDescription(description);
		info.setUse(inUse());
		info.setCreatedBy(createdBy);
		info.setCreatedDate(createdDate);
		info.setUpdatedBy(updatedBy);
		info.setUpdatedDate(updatedDate);
		return info;
	}

	@Override
	public void from(I18nItemInfo info) {
		this.id = new Key(info.getCode(), info.getLangCode());
		this.parent = info.getParent();
		this.kind = info.getKind();
		this.value = info.getValue();
		this.description = info.getDescription();
		this.use = info.isUse();
	}

	@Override
	public String toPlainText() {
		return id + "=" + value;
	}
}
