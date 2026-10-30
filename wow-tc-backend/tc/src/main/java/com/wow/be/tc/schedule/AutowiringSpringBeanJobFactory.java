package com.wow.be.tc.schedule;

import java.lang.reflect.InvocationTargetException;

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

@Slf4j
public final class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory implements ApplicationContextAware {

	private AutowireCapableBeanFactory beanFactory;

	public void setApplicationContext(ApplicationContext context) {
		beanFactory = context.getAutowireCapableBeanFactory();
		log.info("beanFactory: {}", beanFactory);
	}

	@Override
	public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler) throws SchedulerException {
//		final Object job = super.createJobInstance(bundle);
//        beanFactory.autowireBean(job);
//
//        return job;
        
		JobDetail jobDetail = bundle.getJobDetail();
	    Class<? extends Job> jobClass = jobDetail.getJobClass();
	    try {
	      return jobClass.getDeclaredConstructor().newInstance();
	    } catch (Exception e) {
	      SchedulerException se = new SchedulerException("Problem instantiating class '" + jobDetail.getJobClass().getName() + "'", e);
	      throw se;
	    }
	}

	@Override
	protected Object createJobInstance(final TriggerFiredBundle bundle) {
		log.debug("");
		Object job = null;
		try {
			job = bundle.getJobDetail().getJobClass().getDeclaredConstructor().newInstance();
		} catch(IllegalAccessException e) {
			log.warn("");
		} catch (IllegalArgumentException e) {
			log.warn("", e);
		}catch (InstantiationException e) {
			log.warn("", e);
		} catch (InvocationTargetException e) {
			log.warn("", e);
		} catch (NoSuchMethodException e) {
			log.warn("", e);
		} catch (SecurityException e) {
			log.warn("", e);
		}
		beanFactory.autowireBean(job);
		beanFactory.initializeBean(job, null);
		
		return job;
	}
}
