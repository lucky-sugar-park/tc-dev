package com.wow.be.tc.schedule.jobs;

import java.util.Date;

import org.quartz.JobExecutionContext;

import com.wow.be.framework.persist.PersistentService;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.schedule.ScheduleJob;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataTableFlashJob extends ScheduleJob {
	
	public enum FlushByType {
		NATIVE_QUERY, SERVICE_BEAN, SIMPLE_SAVING_DAYS
	}
	
	public DataTableFlashJob() {
		super();
	}

	/**
	 * 이력 메시지관리 화면과 스케쥴 관리 화면에서 (각기 다른 뷰의 모양으로) 관리할 수 있음<br/>
	 * 3가지 방식의 Flash 타입을 지원함 (NATIVE_QUERY, SERVICE_BEAN, SIMPLE_SAVING_DAYS)
	 * 1. Native query를 적용하는 방식
	 *    - native query에 필요한 파라미터와 값을 정보로 가지고 있어야 함
	 *    - 파라미터 타입: 상수 (문자열, 숫자, 날짜), 변수(보관주기)
	 *    - EntityManager를 통해서 처리함
	 * 2. Springframework의 서비스 빈을 적용하는 방식
	 *    - 이 빈 서비스는 반드시 com.wow.be.framework.persist.PersistentService를 implements 한 클래스여야 한다
	 *    - 이 인터페이스에는 총 6개의 default 메서드가 정의되어 있으며, 필요한 함수를 Override해야 한다
	 *    - flushBySavingPeriod, flushBySingleConstantTimestamp, flushByRangeConstantTimestamp
	 *    - flushByConstantStrings, flushByConstantNumbers, flushByCondition
	 *    - 빈 이름, 메서드 이름 (상기 6개중 하나), target 이름, target에 필요한 상수 또는 변수 (문자열, 숫자, 날짜)를 가지고 있어야 한다
	 *    - 메서드 이름에 따라서 필요한 값이 달라져야 함 (flushBySavingPeriod를 우선적으로 구현함)
	 *    - 스케줄 상세(SCHEDULE_JOB_DETAIL)의 methodName 값은 레거시 이름(flashBySavingPeriod)으로 저장되어 있어 그 값으로 분기한다
	 * 3. 단일 테이블에서 단순 보관주기를 적용하는 방식
	 *    - 테이블 이름, 날짜비교 컬럼이름, 보관주기
	 *    - EntityManager를 통해서 처리함
	 * : 2번을 우선적으로 개발함 (1번과 3번 방식은 UI 상으로만 아이디어 개발만 함)
	 *    규칙이 이미 존재하면 더 이상 생성할 수 없음-수정/모니터링(정지/지정/멈춤/재시작) 및 조작화면, 규칙이 없으면 생성하는 화면을 보여줘야 함<br/>
     *    첫재라인: 잡 이름 (기본제공-수정불가능-이 이름으로 생성여부를 판단해야 함), 잡 클래스 이름 (기본제공-수정불가능) <br/>
     *    둘째라인: Flash 방식 (콤보박스)<br/>
     *    셋째라인: Flash 방식에 따른 입력화면 - 서비스 빈 이름 (기본제공-수정불가), 함수선택 (기본제공-수정불가) - Target 이름 - 보관주기<br/>
     *    실행주기: CRON Expression<br/>
     *    넷째라인: 확인 버튼
	 */
	@Override
	public void executeJob(JobExecutionContext context) throws ToolControlException {
		log.debug("AuditMessageFlashJob.executeJob(JobExecutionContext context)...");
		
		String flashByType=(String)context.getMergedJobDataMap().get("flashByType");
		try {
			switch(flashByType) {
			case "NATIVE_QUERY":
				handleFlushByNativeQuery(context);
				break;
			case "SERVICE_BEAN":
				handleFlushByServiceBean(context);
				break;
			case "SIMPLE_SAVING_DAYS":
				handleFlushBySimpleSavingPeriod(context);
				break;
			default:
				log.warn("No supported flushByType: {}", flashByType);
				return;
			}
			context.setResult("SUCCESS");
		} catch (Throwable t) {
			log.warn("Fail to execution for AuditMessageFlashJob.executeJob(JobExecutionContext context)", t);
			context.setResult("FAIL");
		}
		
		log.info("삭제 성공: 몇건");
	}
	
	private void handleFlushByNativeQuery(JobExecutionContext context) throws ToolControlException {
		log.debug("");
		// TO-DO
	}
	
	private void handleFlushByServiceBean(JobExecutionContext context) throws ToolControlException {
		log.debug("");

		String beanName   = (String) context.getMergedJobDataMap().getString("serviceBeanName");
		String methodName = (String) context.getMergedJobDataMap().getString("methodName");
		String targetName = (String) context.getMergedJobDataMap().getString("targetName");

		@SuppressWarnings("rawtypes")
		PersistentService service=(PersistentService)applicationContext.getBean(beanName);
		
		switch(methodName) {
		case "flashBySavingPeriod":
			int savingDays = Integer.parseInt((String)context.getMergedJobDataMap().get("savingDays"));
			long current=new Date().getTime();
			long beyondFrom=current-(savingDays*24*60*60*1000);
			long count = service.flushBySavingPeriod(targetName, new Date(beyondFrom));
			context.setResult(beanName+":"+methodName+":"+targetName+", " + count + " records are flashed successfully.");
			break;
		default:
			// flushBySingleConstantTimestamp, flushByRangeConstantTimestamp
			// flushByConstantStrings, flushByConstantNumbers, flushByCondition
			break;
			
		}
	}
	
	private void handleFlushBySimpleSavingPeriod(JobExecutionContext context) throws ToolControlException {
		log.debug("");
		// TO-DO
	}
}
