package com.wow.be.tc.ui.i18n;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.confighist.ConfigChangeInfo.TargetType;
import com.wow.be.tc.confighist.ConfigChangeRecorder;

import lombok.RequiredArgsConstructor;

/**
 * 다국어 문구 (docs/frontend-design.md §2.2). 화면은 언어 하나의 {코드: 값} 을 받는다. 관리 화면은 코드마다 네 언어를 함께 고친다.
 * 문구는 몇천 줄이라 캐시 없이 DB 를 읽는다(MES 의 Redis 캐시는 두지 않는다).
 */
@Service
@RequiredArgsConstructor
public class I18nService {

	public static final List<String> LANGS = List.of("KO", "EN", "JA", "ZH");

	public static final String KIND_LABEL = "common.code.i18n.kind.label";

	public static final String KIND_MENU = "common.code.i18n.kind.menu";

	private final I18nItemRepository repository;

	private final ConfigChangeRecorder changeRecorder;

	/**
	 * 코드 하나의 네 언어 — 관리 화면 한 줄. {@code use} 를 빼고 보내면 사용(Jackson 3 은 빠진 원시형을 거부하므로 Boolean).
	 */
	public record I18nEntry(String code, String kind, String parent, String description, Boolean use, Map<String, String> values) {
	}

	public static String lang(String lang) {
		String l = lang == null || lang.isBlank() ? "KO" : lang.trim().toUpperCase(Locale.ROOT);
		if (!LANGS.contains(l)) {
			throw new IllegalArgumentException("언어는 " + LANGS + " 중 하나입니다: " + lang);
		}
		return l;
	}

	@Transactional(readOnly = true)
	public Map<String, String> messages(String lang) {
		Map<String, String> out = new TreeMap<>();
		for (I18nItemEntity e : repository.findByIdLangCode(lang(lang))) {
			if (e.inUse() && e.getValue() != null) {
				out.put(e.getId().getCode(), e.getValue());
			}
		}
		return out;
	}

	/** 관리 화면 — 코드별로 묶는다. q 는 코드·값에 든 글자, kind 는 종류 */
	@Transactional(readOnly = true)
	public List<I18nEntry> entries(String q, String kind) {
		String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
		Map<String, List<I18nItemEntity>> byCode = repository.findAll().stream()
			.collect(Collectors.groupingBy(e -> e.getId().getCode(), TreeMap::new, Collectors.toList()));
		return byCode.entrySet().stream()
			.map(en -> toEntry(en.getKey(), en.getValue()))
			.filter(e -> kind == null || kind.isBlank() || kind.equals(e.kind()))
			.filter(e -> needle.isEmpty() || e.code().toLowerCase(Locale.ROOT).contains(needle)
				|| e.values().values().stream().anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(needle)))
			.toList();
	}

	private static I18nEntry toEntry(String code, List<I18nItemEntity> rows) {
		Map<String, String> values = new LinkedHashMap<>();
		LANGS.forEach(l -> values.put(l, null));
		rows.sort(Comparator.comparing(e -> LANGS.indexOf(e.getId().getLangCode())));
		I18nItemEntity first = rows.get(0);
		rows.forEach(e -> values.put(e.getId().getLangCode(), e.getValue()));
		return new I18nEntry(code, first.getKind(), first.getParent(), first.getDescription(), first.inUse(), values);
	}

	/** 코드 하나를 네 언어로 저장한다(없으면 만든다). 빈 값인 언어는 지운다 */
	@Transactional
	public I18nEntry save(String code, I18nEntry entry) {
		if (code == null || !code.matches("[A-Za-z0-9_.\\-]{1,255}")) {
			throw new IllegalArgumentException("코드는 영숫자·_ . - 255자까지입니다: " + code);
		}
		List<I18nItemEntity> before = repository.findByIdCode(code);
		I18nEntry old = before.isEmpty() ? null : toEntry(code, before);
		Map<String, String> values = entry.values() != null ? entry.values() : Map.of();
		for (String l : LANGS) {
			String v = values.get(l);
			I18nItemEntity.Key key = new I18nItemEntity.Key(code, l);
			if (v == null || v.isBlank()) {
				repository.findById(key).ifPresent(repository::delete);
				continue;
			}
			I18nItemEntity e = repository.findById(key).orElseGet(I18nItemEntity::new);
			e.setId(key);
			e.setKind(entry.kind() != null ? entry.kind() : KIND_LABEL);
			e.setParent(entry.parent());
			e.setDescription(entry.description());
			e.setUse(entry.use() == null || entry.use());
			e.setValue(v);
			repository.save(e);
		}
		I18nEntry saved = toEntryOrNull(code);
		if (old == null) {
			changeRecorder.created(TargetType.I18N, code, code, saved);
		} else {
			changeRecorder.updated(TargetType.I18N, code, code, old, saved);
		}
		return saved;
	}

	@Transactional
	public void delete(String code) throws ToolControlException {
		List<I18nItemEntity> rows = repository.findByIdCode(code);
		if (rows.isEmpty()) {
			throw ToolControlException.of(ToolControlException.NOT_FOUND, "다국어 코드가 없습니다: " + code);
		}
		changeRecorder.deleted(TargetType.I18N, code, code, toEntry(code, rows));
		repository.deleteAll(rows);
	}

	private I18nEntry toEntryOrNull(String code) {
		List<I18nItemEntity> rows = repository.findByIdCode(code);
		return rows.isEmpty() ? null : toEntry(code, rows);
	}

	/**
	 * 씨앗 — 없는 (코드, 언어) 만 만든다. 관리자가 고친 값은 덮지 않는다(MES ScreenI18nSeed 와 같은 규칙).
	 *
	 * @return 만든 줄 수
	 */
	@Transactional
	public int seedMissing(List<I18nItemInfo> rows) {
		Set<I18nItemEntity.Key> have = repository.findAll().stream().map(I18nItemEntity::getId).collect(Collectors.toSet());
		int n = 0;
		for (I18nItemInfo r : rows) {
			I18nItemEntity.Key key = new I18nItemEntity.Key(r.getCode(), r.getLangCode());
			if (have.add(key)) {
				I18nItemEntity e = new I18nItemEntity();
				e.from(r);
				repository.save(e);
				n++;
			}
		}
		return n;
	}
}
