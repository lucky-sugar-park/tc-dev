package com.wow.be.tc.endpoints;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.Requires;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.dbconn.DbConnectionInfo;
import com.wow.be.tc.dbconn.DbConnectionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 결과를 기록할 외부 DB 연결 관리 API (docs/db-table-delivery.md). 등록·수정·삭제는 관리자만, 조회는 운영자도
 * (DB_TABLE 전달 정의를 만들 때 연결 이름을 고르도록). 모든 변경은 재기동 없이 반영된다.
 */
@Tag(name = "DB Connection", description = "결과 기록용 외부 DB 연결 — 변경은 관리자 전용")
@RestController
@RequestMapping("/api/v1/db-connections")
@RequiredArgsConstructor
// 권한(설계 §4) — REST 메서드로 동작을 추론한다(GET R, POST C, PUT U, DELETE D). 연결 시험만 X
@Requires("tc.dbconn")
public class DbConnectionController {

	private final DbConnectionService dbConnectionService;

	@Operation(summary = "DB 연결 목록", description = "비밀번호는 주지 않는다(passwordSet 만). 이름 'tc'(tc 자체 DB)는 목록에 없지만 쓸 수 있다.")
	@Requires(action = 'R')
	@GetMapping
	public List<DbConnectionInfo> search() {
		return dbConnectionService.searchAll();
	}

	@Operation(summary = "DB 연결 한 건")
	@Requires(action = 'R')
	@GetMapping("/{name}")
	public DbConnectionInfo find(@PathVariable("name") String name) throws ToolControlException {
		return dbConnectionService.find(name);
	}

	@Operation(summary = "DB 연결 등록", description = "비밀번호는 TC_SECRET_KEY 로 암호화해 저장한다(키가 없으면 400).")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DbConnectionInfo register(@RequestBody DbConnectionInfo info) throws ToolControlException {
		return dbConnectionService.register(info);
	}

	@Operation(summary = "DB 연결 수정", description = "password 를 비우면 기존 비밀번호를 쓴다. 다음 기록부터 새 값으로 연결한다.")
	@PutMapping("/{name}")
	public DbConnectionInfo update(@PathVariable("name") String name, @RequestBody DbConnectionInfo info) throws ToolControlException {
		info.setName(name);
		return dbConnectionService.update(info);
	}

	@Operation(summary = "DB 연결 삭제", description = "이 연결을 쓰는 DB_TABLE 전달 정의가 있으면 409.")
	@DeleteMapping("/{name}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable("name") String name) throws ToolControlException {
		dbConnectionService.delete(name);
	}

	@Operation(summary = "DB 연결 시험", description = "커넥션을 하나 얻어 확인한다. 'tc' 도 된다.")
	@Requires(action = 'X')
	@PostMapping("/{name}/test")
	public Map<String, Object> test(@PathVariable("name") String name) {
		return dbConnectionService.test(name);
	}
}
