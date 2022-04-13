package com.htc.postprocessing.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.htc.postprocessing.constant.PostProcessingConstant;
import com.htc.postprocessing.service.PostProcessingService;

/**
 * @author kumar.charanswain
 *
 */

@Service
public class PostProcessingScheduler {

	Logger logger = LoggerFactory.getLogger(PostProcessingScheduler.class);

	@Autowired
	PostProcessingService postProcessingService;
	
	@Scheduled(cron = PostProcessingConstant.CRONJOB_INTERVAL)
	public void smartCommPostProcessing() {
		String message = postProcessingService.smartComPostProcessing();
		logger.info(message);
		System.out.println(message);
	}

}