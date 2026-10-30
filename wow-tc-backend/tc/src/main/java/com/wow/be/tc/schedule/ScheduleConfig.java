package com.wow.be.tc.schedule;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.quartz.Scheduler;
import org.quartz.spi.JobFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class ScheduleConfig {

	/** Quartz 속성 접두사 */
	public static final String QUARTZ_PROPERTIES_PREFIX = "tc.quartz.properties";

	@Autowired
    private ScheduleTriggerListener triggerListener;

    @Autowired
    private ScheduleJobListener jobListener;

    @Bean
	public JobFactory jobFactory(ApplicationContext applicationContext) {
		log.info("Creating JobFactory...");
		AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
		jobFactory.setApplicationContext(applicationContext);
		return jobFactory;
	}
    
    @Bean
    public Scheduler scheduler(SchedulerFactoryBean schedulerFactoryBean) {
    	return schedulerFactoryBean.getScheduler();
    }
    
    @Bean
	public SchedulerFactoryBean schedulerFactoryBean(ApplicationContext applicationContext, DataSource dataSource) throws IOException {
    	log.info("Creating ScheduleFactoryBaen...");
		SchedulerFactoryBean schedulerFactory = new SchedulerFactoryBean();
		schedulerFactory.setQuartzProperties(quartzProperties(applicationContext.getEnvironment()));
	    schedulerFactory.setWaitForJobsToCompleteOnShutdown(true);
	    schedulerFactory.setAutoStartup(true);
	    schedulerFactory.setJobFactory(jobFactory(applicationContext));
	    schedulerFactory.setDataSource(dataSource);
	    schedulerFactory.setGlobalTriggerListeners(triggerListener);
	    schedulerFactory.setGlobalJobListeners(jobListener);
	    schedulerFactory.setWaitForJobsToCompleteOnShutdown(true);
	    return schedulerFactory;
	}
    
    /**
     * Quartz 설정 — application.yml 의 {@value #QUARTZ_PROPERTIES_PREFIX}.* 를 그대로 Quartz 속성으로 넘긴다.
     * (예: {@code tc.quartz.properties.org.quartz.threadPool.threadCount: 4})
     * DataSource 는 {@link SchedulerFactoryBean#setDataSource} 로 앱 DataSource 를 주므로 Quartz 자체 DataSource 설정은 두지 않는다.
     */
    public Properties quartzProperties(Environment environment) {
    	Map<String, String> map = Binder.get(environment)
    			.bind(QUARTZ_PROPERTIES_PREFIX, Bindable.mapOf(String.class, String.class))
    			.orElse(Map.of());
	    Properties properties = new Properties();
	    properties.putAll(map);
	    return properties;
	}
}
