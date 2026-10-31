package com.wow.be.tc.ui.bookmark;

import java.io.Serializable;

import com.wow.be.framework.persist.PersistentEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 사용자 북마크 — mes-dev {@code BOOKMARK} 와 같은 칼럼(docs/frontend-design.md §2.2·§4.4) */
@Entity
@Table(name = "BOOKMARK")
@IdClass(BookmarkEntity.Key.class)
@Data
@EqualsAndHashCode(callSuper = false)
public class BookmarkEntity extends PersistentEntity<BookmarkInfo> {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "USER_ID", length = 128)
	private String userId;

	@Id
	@Column(name = "NAME", length = 200)
	private String name;

	@Column(name = "PATH_NAME", length = 1000)
	private String pathName;

	@Column(name = "DESCRIPTION", length = 1000)
	private String description;

	/** 화면 상태(문자열 맵)를 JSON 으로 */
	@Column(name = "STATE_JSON", length = 4000)
	private String stateJson;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Key implements Serializable {

		private static final long serialVersionUID = 1L;

		private String userId;

		private String name;
	}

	@Override
	public BookmarkInfo info() {
		BookmarkInfo info = new BookmarkInfo();
		info.setUserId(userId);
		info.setName(name);
		info.setPathName(pathName);
		info.setDescription(description);
		info.setState(BookmarkService.readState(stateJson));
		info.setCreatedBy(createdBy);
		info.setCreatedDate(createdDate);
		info.setUpdatedBy(updatedBy);
		info.setUpdatedDate(updatedDate);
		return info;
	}

	@Override
	public void from(BookmarkInfo info) {
		this.userId = info.getUserId();
		this.name = info.getName();
		this.pathName = info.getPathName();
		this.description = info.getDescription();
		this.stateJson = BookmarkService.writeState(info.getState());
	}

	@Override
	public String toPlainText() {
		return userId + ":" + name;
	}
}
