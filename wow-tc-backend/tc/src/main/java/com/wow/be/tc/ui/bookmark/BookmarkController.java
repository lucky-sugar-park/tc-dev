package com.wow.be.tc.ui.bookmark;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.AuthenticatedOnly;
import com.wow.be.tc.ToolControlException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 본인 북마크 — mes-dev shared 의 계약(docs/frontend-design.md §2.2). 사용자는 늘 로그인한 사람이다(본문의 userId 는 무시).
 * 화면은 고치기·지우기를 {@code ?name=} 으로 부른다.
 */
@Tag(name = "UI — 북마크", description = "본인 북마크")
@RestController
@RequestMapping("/api/me/bookmarks")
@RequiredArgsConstructor
@AuthenticatedOnly
public class BookmarkController {

	private final BookmarkService service;

	@Operation(summary = "내 북마크(이름 순)")
	@GetMapping
	public List<BookmarkInfo> list() {
		return service.list(me());
	}

	@Operation(summary = "더하기(같은 이름 409 ERR_409_EXISTS)")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public BookmarkInfo add(@RequestBody BookmarkInfo info) throws ToolControlException {
		return service.add(me(), info);
	}

	@Operation(summary = "고치기(본문 name 이 다르면 이름 바꾸기)")
	@PutMapping
	public BookmarkInfo update(@RequestParam("name") String name, @RequestBody BookmarkInfo info) throws ToolControlException {
		return service.update(me(), name, info);
	}

	@Operation(summary = "지우기")
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@RequestParam("name") String name) throws ToolControlException {
		service.delete(me(), name);
	}

	private static String me() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}
}
