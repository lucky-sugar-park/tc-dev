package com.wow.be.tc.ui.code;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.Requires;
import com.wow.be.tc.ToolControlException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 공통 코드(LOV) (docs/frontend-design.md §2.2·§4.1). {@code lov} 는 mes-dev shared 의 계약(로그인만). 관리는 운영 옵션 권한.
 */
@Tag(name = "UI — 공통 코드", description = "화면 선택지(LOV)와 관리")
@RestController
@RequestMapping("/api/common-codes")
@RequiredArgsConstructor
@Requires("tc.option")
public class CommonCodeController {

	private final CommonCodeService service;

	@Operation(summary = "선택지", description = "parent 바로 아래의 쓰는 코드(코드 순). leaf=true 면 끝 코드까지")
	@GetMapping("/lov")
	public List<CommonCodeInfo> lov(@RequestParam("parent") String parent,
			@RequestParam(value = "leaf", defaultValue = "false") boolean leaf) {
		return service.lov(parent, leaf);
	}

	@Operation(summary = "관리 — 목록", description = "parent 가 비면 그룹(맨 위)")
	@Requires(action = 'R')
	@GetMapping
	public List<CommonCodeInfo> list(@RequestParam(value = "parent", required = false) String parent) {
		return service.list(parent);
	}

	@Operation(summary = "관리 — 만들기")
	@Requires(action = 'C')
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CommonCodeInfo create(@RequestBody CommonCodeInfo info) throws ToolControlException {
		return service.create(info);
	}

	@Operation(summary = "관리 — 고치기(코드·상위는 바꾸지 않는다)")
	@Requires(action = 'U')
	@PutMapping("/{code}")
	public CommonCodeInfo update(@PathVariable("code") String code, @RequestBody CommonCodeInfo info) throws ToolControlException {
		return service.update(code, info);
	}

	@Operation(summary = "관리 — 지우기(하위 코드가 있으면 409)")
	@Requires(action = 'D')
	@DeleteMapping("/{code}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable("code") String code) throws ToolControlException {
		service.delete(code);
	}
}
