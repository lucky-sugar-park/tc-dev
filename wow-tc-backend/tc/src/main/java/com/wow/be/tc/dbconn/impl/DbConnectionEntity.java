package com.wow.be.tc.dbconn.impl;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.dbconn.DbConnectionInfo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 외부 DB 연결. 비밀번호는 {@code SecretCipher} 로 암호화한 값만 둔다.
 */
@Entity
@Table(name = "DB_CONNECTION")
@Data
@ToString(callSuper = true, exclude = "encryptedPassword")
@EqualsAndHashCode(callSuper = false)
public class DbConnectionEntity extends PersistentEntity<DbConnectionInfo> {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "NAME", length = 100)
	private String name;

	@Column(name = "JDBC_URL", nullable = false, length = 1000)
	private String jdbcUrl;

	@Column(name = "USERNAME", length = 200)
	private String username;

	@Column(name = "ENCRYPTED_PASSWORD", length = 1000)
	private String encryptedPassword;

	@Column(name = "MAX_POOL_SIZE", nullable = false)
	private int maxPoolSize;

	@Column(name = "IS_USE", nullable = false)
	private boolean use;

	@Column(name = "DESCRIPTION", length = 1024)
	private String description;

	/** 비밀번호는 넣지 않는다 — {@link #encryptedPassword} 는 서비스가 따로 채운다 */
	@Override
	public DbConnectionInfo info() {
		DbConnectionInfo info = new DbConnectionInfo();
		info.setName(name);
		info.setJdbcUrl(jdbcUrl);
		info.setUsername(username);
		info.setPasswordSet(encryptedPassword != null);
		info.setMaxPoolSize(maxPoolSize);
		info.setUse(use);
		info.setDescription(description);
		info.setCreatedBy(createdBy);
		info.setCreatedDate(createdDate);
		info.setUpdatedBy(updatedBy);
		info.setUpdatedDate(updatedDate);
		return info;
	}

	@Override
	public void from(DbConnectionInfo info) {
		this.name = info.getName();
		this.jdbcUrl = info.getJdbcUrl();
		this.username = info.getUsername();
		this.maxPoolSize = info.getMaxPoolSize();
		this.use = info.isUse();
		this.description = info.getDescription();
	}

	/** 풀을 다시 만들어야 하는지 가리는 값 — 연결에 쓰는 항목만 */
	String fingerprint() {
		return jdbcUrl + "|" + username + "|" + encryptedPassword + "|" + maxPoolSize;
	}

	@Override
	public String toPlainText() {
		return name;
	}
}
