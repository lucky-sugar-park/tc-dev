package com.wow.be.tc.ui.bookmark;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wow.be.tc.ToolControlException;

import lombok.RequiredArgsConstructor;

/** 본인 북마크 (docs/frontend-design.md §2.2) — mes-dev 와 같은 규칙: 중복 409 ERR_409_EXISTS, 상태 JSON 4000자까지 */
@Service
@RequiredArgsConstructor
public class BookmarkService {

	static final int MAX_STATE = 4000;

	private static final ObjectMapper JSON = new ObjectMapper();

	private final BookmarkRepository repository;

	@Transactional(readOnly = true)
	public List<BookmarkInfo> list(String userId) {
		return repository.findByUserIdOrderByNameAsc(userId).stream().map(BookmarkEntity::info).toList();
	}

	@Transactional
	public BookmarkInfo add(String userId, BookmarkInfo info) throws ToolControlException {
		validate(info);
		if (repository.existsById(new BookmarkEntity.Key(userId, info.getName()))) {
			throw ToolControlException.of("ERR_409_EXISTS", "같은 이름의 북마크가 있습니다: " + info.getName());
		}
		info.setUserId(userId);
		BookmarkEntity e = new BookmarkEntity();
		e.from(info);
		return repository.save(e).info();
	}

	@Transactional
	public BookmarkInfo update(String userId, String name, BookmarkInfo info) throws ToolControlException {
		BookmarkEntity e = repository.findById(new BookmarkEntity.Key(userId, name))
			.orElseThrow(() -> ToolControlException.of(ToolControlException.NOT_FOUND, "북마크가 없습니다: " + name));
		if (info.getName() != null && !info.getName().equals(name)) {
			// 이름 바꾸기 — 키가 바뀌므로 새로 만들고 옛것을 지운다
			BookmarkInfo renamed = add(userId, info);
			repository.delete(e);
			return renamed;
		}
		info.setName(name);
		info.setUserId(userId);
		validate(info);
		e.from(info);
		return repository.save(e).info();
	}

	@Transactional
	public void delete(String userId, String name) throws ToolControlException {
		BookmarkEntity.Key key = new BookmarkEntity.Key(userId, name);
		if (!repository.existsById(key)) {
			throw ToolControlException.of(ToolControlException.NOT_FOUND, "북마크가 없습니다: " + name);
		}
		repository.deleteById(key);
	}

	private static void validate(BookmarkInfo info) {
		if (info.getName() == null || info.getName().isBlank() || info.getName().length() > 200) {
			throw new IllegalArgumentException("북마크 이름은 1~200자입니다");
		}
		if (info.getPathName() == null || info.getPathName().isBlank() || info.getPathName().length() > 1000) {
			throw new IllegalArgumentException("경로(pathName)는 1~1000자입니다");
		}
		String state = writeState(info.getState());
		if (state != null && state.length() > MAX_STATE) {
			throw new IllegalArgumentException("화면 상태가 너무 깁니다(" + state.length() + "자, " + MAX_STATE + "자까지)");
		}
	}

	static String writeState(Map<String, String> state) {
		if (state == null) {
			return null;
		}
		try {
			return JSON.writeValueAsString(state);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("화면 상태를 저장할 수 없습니다");
		}
	}

	static Map<String, String> readState(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return JSON.readValue(json, new TypeReference<Map<String, String>>() { });
		} catch (JsonProcessingException e) {
			return null;
		}
	}
}
