package com.wow.be.tc.ui.code;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.confighist.ConfigChangeInfo.TargetType;
import com.wow.be.tc.confighist.ConfigChangeRecorder;

import lombok.RequiredArgsConstructor;

/** 공통 코드(LOV) — 화면 선택지와 관리 (docs/frontend-design.md §2.2) */
@Service
@RequiredArgsConstructor
public class CommonCodeService {

	private final CommonCodeRepository repository;

	private final ConfigChangeRecorder changeRecorder;

	/**
	 * 쓰는 코드만, 코드 순. {@code leaf} 면 바로 아래가 아니라 그 아래 끝 코드까지(MES {@code findLeafDescendants}).
	 */
	@Transactional(readOnly = true)
	public List<CommonCodeInfo> lov(String parent, boolean leaf) {
		if (parent == null || parent.isBlank()) {
			throw new IllegalArgumentException("parent 가 필요합니다");
		}
		List<CommonCodeInfo> out = new ArrayList<>();
		collect(parent, leaf, out);
		return out;
	}

	private void collect(String parent, boolean leaf, List<CommonCodeInfo> out) {
		for (CommonCodeEntity e : repository.findByParentOrderByCode(parent)) {
			if (!e.inUse()) {
				continue;
			}
			if (leaf && repository.existsByParent(e.getCode())) {
				collect(e.getCode(), true, out);
			} else {
				out.add(e.info());
			}
		}
	}

	/** 관리 화면 — parent 가 비면 그룹(맨 위) 목록 */
	@Transactional(readOnly = true)
	public List<CommonCodeInfo> list(String parent) {
		List<CommonCodeEntity> rows = parent == null || parent.isBlank() ? repository.findByParentIsNullOrderByCode()
			: repository.findByParentOrderByCode(parent);
		return rows.stream().map(CommonCodeEntity::info).toList();
	}

	@Transactional
	public CommonCodeInfo create(CommonCodeInfo info) throws ToolControlException {
		validate(info);
		if (repository.existsById(info.getCode())) {
			throw ToolControlException.of("ERR_409_EXISTS", "이미 있는 코드입니다: " + info.getCode());
		}
		if (info.getParent() != null && !info.getParent().isBlank() && !repository.existsById(info.getParent())) {
			throw new IllegalArgumentException("상위 코드가 없습니다: " + info.getParent());
		}
		CommonCodeEntity e = new CommonCodeEntity();
		e.from(info);
		CommonCodeInfo saved = repository.save(e).info();
		changeRecorder.created(TargetType.COMMON_CODE, saved.getCode(), saved.getName(), saved);
		return saved;
	}

	@Transactional
	public CommonCodeInfo update(String code, CommonCodeInfo info) throws ToolControlException {
		CommonCodeEntity e = repository.findById(code)
			.orElseThrow(() -> ToolControlException.of(ToolControlException.NOT_FOUND, "코드가 없습니다: " + code));
		CommonCodeInfo before = e.info();
		info.setCode(code);
		info.setParent(before.getParent());
		validate(info);
		e.from(info);
		CommonCodeInfo saved = repository.save(e).info();
		changeRecorder.updated(TargetType.COMMON_CODE, code, saved.getName(), before, saved);
		return saved;
	}

	@Transactional
	public void delete(String code) throws ToolControlException {
		CommonCodeEntity e = repository.findById(code)
			.orElseThrow(() -> ToolControlException.of(ToolControlException.NOT_FOUND, "코드가 없습니다: " + code));
		if (repository.existsByParent(code)) {
			throw ToolControlException.of(ToolControlException.CONFLICT, "하위 코드가 있어 지울 수 없습니다: " + code);
		}
		changeRecorder.deleted(TargetType.COMMON_CODE, code, e.getName(), e.info());
		repository.delete(e);
	}

	private static void validate(CommonCodeInfo info) {
		if (info.getCode() == null || !info.getCode().matches("[A-Za-z0-9_.\\-]{1,255}")) {
			throw new IllegalArgumentException("코드는 영숫자·_ . - 255자까지입니다: " + info.getCode());
		}
		if (info.getName() == null || info.getName().isBlank()) {
			throw new IllegalArgumentException("이름이 필요합니다");
		}
	}

	/** 씨앗 — 없는 코드만 만든다 */
	@Transactional
	public int seedMissing(List<CommonCodeInfo> rows) {
		Set<String> have = repository.findAll().stream().map(CommonCodeEntity::getCode).collect(Collectors.toSet());
		int n = 0;
		for (CommonCodeInfo r : rows) {
			if (have.add(r.getCode())) {
				CommonCodeEntity e = new CommonCodeEntity();
				e.from(r);
				repository.save(e);
				n++;
			}
		}
		return n;
	}
}
