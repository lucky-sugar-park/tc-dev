package com.wow.be.tc.schedule;

import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * Job 을 실행마다 새로 만들고 {@code @Autowired} 필드를 채운다. JobDataMap 값은 빈 속성으로 넣지 않는다 — Job 이
 * {@code context.getMergedJobDataMap()} 에서 읽는다.
 */
@Slf4j
public final class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory implements ApplicationContextAware {

	private AutowireCapableBeanFactory beanFactory;

	@Override
	public void setApplicationContext(ApplicationContext context) {
		beanFactory = context.getAutowireCapableBeanFactory();
	}

	@Override
	public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler) throws SchedulerException {
		JobDetail jobDetail = bundle.getJobDetail();
		try {
			Job job = jobDetail.getJobClass().getDeclaredConstructor().newInstance();
			beanFactory.autowireBean(job);
			return job;
		} catch (ReflectiveOperationException | RuntimeException e) {
			throw new SchedulerException("Job 을 만들지 못했습니다: " + jobDetail.getJobClass().getName(), e);
		}
	}
}
