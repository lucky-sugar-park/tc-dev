package com.wow.be.tc.dispatch.impl;

import java.util.Arrays;
import java.util.Date;

import org.springframework.stereotype.Service;

import com.wow.be.tc.dispatch.PlcResponseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 마지막 PLC 응답을 Redis({@link PlcResponseEntity})에 둔다.
 *
 * <p>레거시는 {@code @Cacheable}/{@code @CachePut} 만 붙이고 저장 코드를 주석 처리해 아무것도 기억하지 않았고,
 * 비교 코드는 {@code before=null} 로 두 번째 응답부터 NPE 가 났다. 여기서는 리포지터리에 직접 읽고 쓴다.
 *
 * <p>비교 키는 opcode 하나다. 같은 opcode 로 다른 주소를 요청하는 인터페이스라면 키를 넓혀야 한다(요청 data 포함 등).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultPlcResponseService implements PlcResponseService {

	private final PlcResponseRedisCacheRepository plcResponseRepository;

	@Override
	public boolean checkChangedAndRemember(String opcode, byte[] original) {
		if (opcode == null || original == null) {
			return true;
		}
		try {
			byte[] before = plcResponseRepository.findById(opcode).map(PlcResponseEntity::getReplyData).orElse(null);
			if (Arrays.equals(before, original)) {
				return false;
			}
			plcResponseRepository.save(new PlcResponseEntity(opcode, new Date(), original, -1L));
			return true;
		} catch (RuntimeException e) {
			// Redis 를 쓸 수 없어도 PLC 요청은 계속한다
			log.warn("PLC 응답 변경 여부를 확인하지 못해 '바뀜'으로 처리합니다: opcode={}, cause={}", opcode, e.getMessage());
			return true;
		}
	}
}
