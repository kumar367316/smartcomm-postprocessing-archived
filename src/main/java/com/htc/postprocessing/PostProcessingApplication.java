package com.htc.postprocessing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author kumar.charanswain
 *
 */
@SpringBootApplication
@EnableScheduling
public class PostProcessingApplication {

	public static void main(String[] args) {
		SpringApplication.run(PostProcessingApplication.class, args);
	}

}
