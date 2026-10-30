package com.wow.be.tc.dispatch.impl;

import org.springframework.data.repository.CrudRepository;

/**
 * Redis 저장소. 이름을 *RedisCacheRepository 로 두어 framework RedisConfig 의 Redis 리포지터리 스캔에 잡히게 한다.
 */
public interface PlcResponseRedisCacheRepository extends CrudRepository<PlcResponseEntity, String> {

}
