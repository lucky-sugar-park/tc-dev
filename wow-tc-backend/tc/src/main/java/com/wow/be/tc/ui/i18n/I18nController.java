package com.wow.be.tc.ui.i18n;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.PublicApi;
import com.wow.be.framework.security.authz.Requires;
import com.wow.be.tc.ToolControlException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 다국어 (docs/frontend-design.md §2.2·§4.1). {@code messages} 는 mes-dev shared 의 계약 — 로그인 화면이 먼저 부르므로 공개다.
 * 관리(코드별 네 언어)는 운영 옵션 권한.
 */
@Tag(name = "UI — 다국어", description = "화면 문구(KO·EN·JA·ZH)")
@RestController
@RequestMapping("/api/i18n")
@RequiredArgsConstructor
@Requires("tc.option")
public class I18nController {

	private final I18nService service;

	@Operation(summary = "언어 하나의 문구", description = "{코드: 값}, 쓰는 것만. 로그인 전에도 부른다")
	@PublicApi
	@GetMapping("/messages")
	public Map<String, String> messages(@RequestParam(value = "lang", required = false) String lang) {
		return service.messages(lang);
	}

	@Operation(summary = "관리 — 코드별 네 언어", description = "q: 코드·값에 든 글자, kind: 종류(common.code.i18n.kind.*)")
	@Requires(action = 'R')
	@GetMapping("/items")
	public List<I18nService.I18nEntry> items(@RequestParam(value = "q", required = false) String q,
			@RequestParam(value = "kind", required = false) String kind) {
		return service.entries(q, kind);
	}

	@Operation(summary = "관리 — 코드 하나 저장(없으면 만듦)", description = "values 의 빈 언어는 지운다")
	@Requires(action = 'U')
	@PutMapping("/items/{code}")
	public I18nService.I18nEntry save(@PathVariable("code") String code, @RequestBody I18nService.I18nEntry entry) {
		return service.save(code, entry);
	}

	@Operation(summary = "관리 — 코드 지우기(네 언어 모두)")
	@Requires(action = 'D')
	@DeleteMapping("/items/{code}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable("code") String code) throws ToolControlException {
		service.delete(code);
	}
}
