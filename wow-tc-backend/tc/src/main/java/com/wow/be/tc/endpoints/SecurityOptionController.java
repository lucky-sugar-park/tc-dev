package com.wow.be.tc.endpoints;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.PermissionModeSource.Mode;
import com.wow.be.framework.security.authz.Requires;
import com.wow.be.tc.confighist.ConfigChangeInfo.TargetType;
import com.wow.be.tc.confighist.ConfigChangeRecorder;
import com.wow.be.tc.security.TcPermissionModeSource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 보안 운영 옵션 — 권한 검사 모드(AUDIT/ENFORCE). 관리자 전용, 재기동 없이 바뀐다.
 */
@Tag(name = "Security Option", description = "권한 검사 모드 조회·변경 — 관리자 전용")
@RestController
@RequestMapping("/api/v1/security/permission-mode")
@RequiredArgsConstructor
@Requires("tc.option")
public class SecurityOptionController {

	private final TcPermissionModeSource modeSource;

	private final ConfigChangeRecorder changeRecorder;

	public record ModeRequest(Mode mode) {
	}

	@Operation(summary = "권한 검사 모드 조회")
	@Requires(action = 'R')
	@GetMapping
	public Map<String, String> current() {
		return Map.of("mode", modeSource.current().name());
	}

	@Operation(summary = "권한 검사 모드 변경", description = "mode 를 비우면 설정값(tc.security.permission-mode)으로 되돌린다.")
	@PutMapping
	public Map<String, String> change(@RequestBody ModeRequest req) {
		Mode before = modeSource.current();
		modeSource.change(req.mode());
		Mode after = modeSource.current();
		changeRecorder.updated(TargetType.OPTION, "permission-mode", "권한 검사 모드", Map.of("mode", before.name()),
			Map.of("mode", after.name()));
		return Map.of("mode", after.name());
	}
}
