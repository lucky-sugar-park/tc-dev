package com.wow.be.tc.ui;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.AuthenticatedOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * MES shared 셸이 부르지만 tc 에는 없는 것 — 빈 값으로 답한다(docs/frontend-design.md §4.1, 결정 U2). 메뉴·화면 경로는 프론트에
 * 고정(빈 목록이면 셸이 코드의 메뉴·경로를 쓴다), 운영 옵션은 셸 기본값을 쓴다. 실패로 답해도 셸은 돌지만 콘솔 오류와
 * "모르는 경로" 확인이 꺼지는 것을 피한다.
 */
@Tag(name = "UI — 셸", description = "정적 메뉴를 쓰게 하는 빈 응답")
@RestController
@AuthenticatedOnly
public class UiShellController {

	@Operation(summary = "메뉴(빈 목록 — 프론트 정적 메뉴)")
	@GetMapping("/api/menus")
	public List<Object> menus() {
		return List.of();
	}

	@Operation(summary = "화면 경로(빈 목록 — 프론트 정적 경로)")
	@GetMapping("/api/ui-routes")
	public List<Object> uiRoutes(@RequestParam(value = "app", required = false) String app) {
		return List.of();
	}

	@Operation(summary = "셸 운영 옵션(빈 값 — 셸 기본값)")
	@GetMapping("/api/operation-options/effective")
	public Map<String, String> effectiveOptions(@RequestParam(value = "ids", required = false) String ids) {
		return Map.of();
	}
}
