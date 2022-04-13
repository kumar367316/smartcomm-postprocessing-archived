package com.htc.postprocessing.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author kumar.charanswain
 *
 */

@RestController
public class PostProcessingController {

	@PostMapping(path = "/message")
	public String message() {
		return "postprocessing";
	}
}