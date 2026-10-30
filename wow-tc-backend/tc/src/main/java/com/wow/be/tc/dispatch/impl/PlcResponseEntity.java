package com.wow.be.tc.dispatch.impl;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@Builder
@RedisHash
@NoArgsConstructor
@AllArgsConstructor
public class PlcResponseEntity {

	@Id
	private String opcode;
	
	private Date timestamp;
	
	private byte[] replyData;
	
	@TimeToLive(unit = TimeUnit.MILLISECONDS)
    private Long timeToLive;	
}
