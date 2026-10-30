package com.wow.be.tc.dispatch;

import com.wow.be.framework.dispatch.ResponseMessage;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * tc 응답 메시지. framework {@link ResponseMessage}(헤더·success·errorCode·errorMessage·data)에 PLC 처리 결과를 더한다.
 *
 * <ul>
 *   <li>{@code success}/{@code errorCode}/{@code errorMessage} — tc 처리 결과. PLC 가 오류 코드를 돌려주면
 *       {@code success=false}, {@code errorCode=ERR_502_TC_PLC_ERROR} 다.</li>
 *   <li>{@link #resultCode}/{@link #resultDescription} — PLC 가 돌려준 종료 코드(MELSEC end code) 그대로. 레거시와 같은 이름이다.</li>
 *   <li>{@code data} — 응답 인터페이스의 데이터 속성으로 해석한 값.</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TcMessageResponse extends ResponseMessage {

	/** 직전 응답과 원본 바이트가 달라졌는가 (같은 opcode 기준) */
	private boolean changed;

	/** PLC 종료 코드. PLC 와 통신하기 전에 실패하면 null */
	private Integer resultCode;

	private String resultDescription;
}
