package com.wow.be.tc.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import com.wow.be.tc.ui.code.CommonCodeInfo;
import com.wow.be.tc.ui.code.CommonCodeService;
import com.wow.be.tc.ui.i18n.I18nItemInfo;
import com.wow.be.tc.ui.i18n.I18nService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 화면 문구·공통 코드 씨앗 (docs/frontend-design.md §4.4). 기동 때 없는 줄만 만든다 — 관리 화면에서 고친 값은 덮지 않는다.
 *
 * <ul>
 *   <li>{@code ui-seed/i18n/*.tsv} — 한 줄 {@code 코드<TAB>ko<TAB>en<TAB>ja<TAB>zh}, {@code #} 은 주석. {@code 10-shared.tsv} 는
 *       mes-dev 의 셸 문구 그대로, {@code i18n.menu.*} 는 종류 menu.</li>
 *   <li>{@code ui-seed/codes.tsv} — 한 줄 {@code 코드<TAB>상위<TAB>ko<TAB>en<TAB>ja<TAB>zh}(상위가 비면 그룹). 이름은 ko,
 *       다국어는 {@code i18n.<코드>}.</li>
 * </ul>
 * 다중화로 두 인스턴스가 함께 떠도 된다 — 한쪽이 먼저 넣은 줄은 키 충돌로 실패하고 다음 기동 때 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UiSeedLoader {

	private final I18nService i18nService;

	private final CommonCodeService commonCodeService;

	@Value("${tc.ui.seed-enabled:true}")
	private boolean enabled;

	@EventListener(ApplicationReadyEvent.class)
	public void seed() {
		if (!enabled) {
			return;
		}
		try {
			List<I18nItemInfo> i18n = new ArrayList<>();
			List<CommonCodeInfo> codes = new ArrayList<>();
			Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:ui-seed/i18n/*.tsv");
			Arrays.sort(files, Comparator.comparing(Resource::getFilename));
			for (Resource r : files) {
				for (String[] c : read(r, 5)) {
					add(i18n, c[0], c[0].startsWith("i18n.menu.") ? I18nService.KIND_MENU : I18nService.KIND_LABEL, c, 1);
				}
			}
			for (String[] c : read(new PathMatchingResourcePatternResolver().getResource("classpath:ui-seed/codes.tsv"), 6)) {
				CommonCodeInfo code = new CommonCodeInfo();
				code.setCode(c[0]);
				code.setParent(c[1].isBlank() ? null : c[1]);
				code.setName(c[2]);
				code.setI18nCode("i18n." + c[0]);
				codes.add(code);
				add(i18n, "i18n." + c[0], I18nService.KIND_LABEL, c, 2);
			}
			int n = i18nService.seedMissing(i18n);
			int m = commonCodeService.seedMissing(codes);
			log.info("화면 씨앗: 다국어 {}줄, 공통 코드 {}개 새로 만듦(이미 있는 것은 그대로)", n, m);
		} catch (IOException | RuntimeException e) {
			log.warn("화면 씨앗을 넣지 못했습니다(다음 기동 때 다시) — {}", e.toString());
		}
	}

	private static void add(List<I18nItemInfo> out, String code, String kind, String[] cols, int from) {
		for (int i = 0; i < I18nService.LANGS.size(); i++) {
			String v = cols[from + i];
			if (v.isBlank()) {
				continue;
			}
			I18nItemInfo row = new I18nItemInfo();
			row.setCode(code);
			row.setLangCode(I18nService.LANGS.get(i));
			row.setKind(kind);
			row.setValue(v);
			out.add(row);
		}
	}

	private static List<String[]> read(Resource r, int columns) throws IOException {
		List<String[]> rows = new ArrayList<>();
		if (!r.exists()) {
			return rows;
		}
		try (BufferedReader in = new BufferedReader(new InputStreamReader(r.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = in.readLine()) != null) {
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}
				String[] c = Arrays.copyOf(line.split("\t", -1), columns);
				for (int i = 0; i < c.length; i++) {
					c[i] = c[i] == null ? "" : c[i].trim();
				}
				if (!c[0].isEmpty()) {
					rows.add(c);
				}
			}
		}
		return rows;
	}
}
