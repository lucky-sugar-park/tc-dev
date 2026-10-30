package com.wow.be.tc.dbconn.impl;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.confighist.ConfigChangeInfo.TargetType;
import com.wow.be.tc.confighist.ConfigChangeRecorder;
import com.wow.be.tc.dbconn.DbConnectionInfo;
import com.wow.be.tc.dbconn.DbConnectionProperties;
import com.wow.be.tc.dbconn.DbConnectionService;
import com.wow.be.tc.delivery.DeliveryTarget.DeliveryType;
import com.wow.be.tc.delivery.impl.DeliveryDefinitionRepository;
import com.wow.be.tc.security.SecretCipher;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 외부 DB 연결 관리. 연결마다 작은 Hikari 풀을 두고, 쓸 때마다 저장된 연결 정보와 비교해 바뀌었으면 풀을 새로 만든다 —
 * 다른 tc 인스턴스에서 바꾼 값도 다음 기록부터 반영된다.
 */
@Slf4j
@Service
@EnableConfigurationProperties(DbConnectionProperties.class)
public class DefaultDbConnectionService implements DbConnectionService {

	private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9_-]{1,100}");

	/** 테이블 이름 — 영문·숫자·밑줄, 앞에 스키마 하나까지. SQL 에 그대로 들어가므로 이 모양만 받는다 */
	public static final Pattern TABLE_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}(\\.[A-Za-z_][A-Za-z0-9_]{0,63})?");

	private final DbConnectionRepository repository;

	private final DeliveryDefinitionRepository deliveryRepository;

	private final SecretCipher cipher;

	private final DbConnectionProperties properties;

	private final DataSource tcDataSource;

	private final ConfigChangeRecorder changeRecorder;

	private final Map<String, Pool> pools = new ConcurrentHashMap<>();

	private record Pool(String fingerprint, HikariDataSource dataSource) {
	}

	public DefaultDbConnectionService(DbConnectionRepository repository, DeliveryDefinitionRepository deliveryRepository,
			SecretCipher cipher, DbConnectionProperties properties, DataSource tcDataSource, ConfigChangeRecorder changeRecorder) {
		this.repository = repository;
		this.deliveryRepository = deliveryRepository;
		this.cipher = cipher;
		this.properties = properties;
		this.tcDataSource = tcDataSource;
		this.changeRecorder = changeRecorder;
	}

	@Override
	@Transactional
	public DbConnectionInfo register(DbConnectionInfo info) throws ToolControlException {
		validate(info);
		if (TC.equals(info.getName())) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "'tc' 는 tc 자체 DB 의 예약 이름이라 쓸 수 없습니다.");
		}
		if (repository.existsById(info.getName())) {
			throw ToolControlException.of("ERR_409", "같은 이름의 DB 연결이 이미 있습니다: " + info.getName());
		}
		DbConnectionEntity entity = new DbConnectionEntity();
		entity.from(info);
		entity.setEncryptedPassword(StringUtils.hasLength(info.getPassword()) ? cipher.encrypt(info.getPassword()) : null);
		DbConnectionInfo created = repository.save(entity).info();
		changeRecorder.created(TargetType.DB_CONNECTION, created.getName(), created.getName(), created);
		return created;
	}

	@Override
	@Transactional
	public DbConnectionInfo update(DbConnectionInfo info) throws ToolControlException {
		DbConnectionEntity entity = load(info.getName());
		validate(info);
		DbConnectionInfo before = entity.info();
		entity.from(info);
		if (StringUtils.hasLength(info.getPassword())) {
			entity.setEncryptedPassword(cipher.encrypt(info.getPassword()));
		}
		DbConnectionInfo after = repository.save(entity).info();
		changeRecorder.updated(TargetType.DB_CONNECTION, after.getName(), after.getName(), before, after);
		return after;
	}

	@Override
	@Transactional
	public void delete(String name) throws ToolControlException {
		DbConnectionEntity entity = load(name);
		if (deliveryRepository.existsByDeliveryTypeAndTargetName(DeliveryType.DB_TABLE, name)) {
			throw ToolControlException.of("ERR_409", "이 DB 연결을 쓰는 전달 정의가 있어 지울 수 없습니다: " + name);
		}
		changeRecorder.deleted(TargetType.DB_CONNECTION, name, name, entity.info());
		repository.delete(entity);
		close(name);
	}

	@Override
	@Transactional(readOnly = true)
	public DbConnectionInfo find(String name) throws ToolControlException {
		return load(name).info();
	}

	@Override
	@Transactional(readOnly = true)
	public List<DbConnectionInfo> searchAll() {
		List<DbConnectionInfo> list = new ArrayList<>();
		repository.findAll().forEach(e -> list.add(e.info()));
		return list;
	}

	@Override
	public Map<String, Object> test(String name) {
		Map<String, Object> result = new LinkedHashMap<>();
		try (Connection conn = dataSource(name).getConnection()) {
			DatabaseMetaData md = conn.getMetaData();
			result.put("success", conn.isValid(3));
			result.put("database", md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
		} catch (ToolControlException e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		} catch (SQLException | RuntimeException e) {
			result.put("success", false);
			result.put("message", "연결하지 못했습니다: " + e.getMessage());
		}
		return result;
	}

	@Override
	public DataSource dataSource(String name) throws ToolControlException {
		if (TC.equals(name)) {
			return tcDataSource;
		}
		DbConnectionEntity entity = load(name);
		if (!entity.isUse()) {
			close(name);
			throw ToolControlException.of("ERR_409", "사용 중지된 DB 연결입니다: " + name);
		}
		String fingerprint = entity.fingerprint();
		Pool pool = pools.get(name);
		if (pool != null && pool.fingerprint().equals(fingerprint)) {
			return pool.dataSource();
		}
		synchronized (pools) {
			pool = pools.get(name);
			if (pool != null && pool.fingerprint().equals(fingerprint)) {
				return pool.dataSource();
			}
			if (pool != null) {
				log.info("DB 연결 정보가 바뀌어 풀을 새로 만듭니다: {}", name);
				pool.dataSource().close();
			}
			Pool created = new Pool(fingerprint, createPool(entity));
			pools.put(name, created);
			return created.dataSource();
		}
	}

	@Override
	public void validateTable(String name, String table, Collection<String> requiredColumns) throws ToolControlException {
		if (table == null || !TABLE_PATTERN.matcher(table).matches()) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST,
				"테이블 이름은 영문·숫자·밑줄로, 스키마는 점(.) 하나로 씁니다: " + table);
		}
		String schema = table.contains(".") ? table.substring(0, table.indexOf('.')) : null;
		String bare = table.contains(".") ? table.substring(table.indexOf('.') + 1) : table;

		Set<String> found = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		try (Connection conn = dataSource(name).getConnection()) {
			DatabaseMetaData md = conn.getMetaData();
			String catalog = schema != null ? schema : conn.getCatalog();
			String escape = md.getSearchStringEscape();
			String pattern = escape == null ? bare : bare.replace("_", escape + "_");
			for (String candidate : List.of(pattern, pattern.toUpperCase(Locale.ROOT), pattern.toLowerCase(Locale.ROOT))) {
				try (ResultSet rs = md.getColumns(catalog, null, candidate, "%")) {
					while (rs.next()) {
						if (bare.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
							found.add(rs.getString("COLUMN_NAME"));
						}
					}
				}
				if (!found.isEmpty()) {
					break;
				}
			}
		} catch (SQLException | RuntimeException e) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST,
				"DB 연결 " + name + " 에서 테이블을 확인하지 못했습니다: " + e.getMessage());
		}
		if (found.isEmpty()) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "테이블이 없습니다: " + name + " / " + table);
		}
		List<String> missing = requiredColumns.stream().filter(c -> !found.contains(c)).toList();
		if (!missing.isEmpty()) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST,
				"테이블 " + table + " 에 필요한 칼럼이 없습니다: " + missing);
		}
	}

	@PreDestroy
	public void closeAll() {
		pools.keySet().forEach(this::close);
	}

	private void close(String name) {
		Pool pool = pools.remove(name);
		if (pool != null) {
			pool.dataSource().close();
		}
	}

	private HikariDataSource createPool(DbConnectionEntity entity) throws ToolControlException {
		HikariConfig config = new HikariConfig();
		config.setPoolName("tc-db-" + entity.getName());
		config.setJdbcUrl(entity.getJdbcUrl());
		config.setUsername(entity.getUsername());
		if (entity.getEncryptedPassword() != null) {
			config.setPassword(cipher.decrypt(entity.getEncryptedPassword()));
		}
		config.setMaximumPoolSize(Math.max(1, entity.getMaxPoolSize()));
		config.setMinimumIdle(0);
		config.setIdleTimeout(60_000);
		config.setConnectionTimeout(properties.getConnectTimeout().toMillis());
		// 만들 때 연결을 시도하지 않는다 — 대상 DB 가 잠시 내려가 있어도 풀은 만들고, 기록할 때 실패로 남긴다
		config.setInitializationFailTimeout(-1);
		return new HikariDataSource(config);
	}

	private DbConnectionEntity load(String name) throws ToolControlException {
		if (!StringUtils.hasText(name)) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "DB 연결 이름이 없습니다.");
		}
		return repository.findById(name).orElseThrow(() -> ToolControlException.of("ERR_404", "DB 연결이 없습니다: " + name));
	}

	private void validate(DbConnectionInfo info) throws ToolControlException {
		if (info == null || !StringUtils.hasText(info.getName()) || !NAME_PATTERN.matcher(info.getName()).matches()) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST,
				"DB 연결 이름은 영문 소문자·숫자·밑줄·하이픈으로 씁니다(100자 이하).");
		}
		checkJdbcUrl(info.getJdbcUrl(), properties);
		if (info.getMaxPoolSize() < 1 || info.getMaxPoolSize() > 20) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "최대 커넥션 수는 1~20 입니다.");
		}
	}

	/** 허용 접두어로 시작하고 금지 옵션이 없는지 */
	static void checkJdbcUrl(String url, DbConnectionProperties properties) throws ToolControlException {
		if (!StringUtils.hasText(url) || properties.getAllowedUrlPrefixes().stream().noneMatch(url::startsWith)) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST,
				"JDBC 주소는 " + properties.getAllowedUrlPrefixes() + " 로 시작해야 합니다.");
		}
		for (String option : properties.getForbiddenUrlOptions()) {
			// ?a=1&b=2, ;a=1, (a=1) 모양의 옵션 모두 — 이름 앞의 구분자와 = 로 찾는다
			if (Pattern.compile("[?&;(,]\\s*" + Pattern.quote(option) + "\\s*=", Pattern.CASE_INSENSITIVE).matcher(url).find()) {
				throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "JDBC 주소에 쓸 수 없는 옵션입니다: " + option);
			}
		}
	}
}
