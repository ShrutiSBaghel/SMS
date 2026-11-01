package org.project.sms.controller;

import org.project.sms.dto.HealthResponseDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class HealthController {

	private final JdbcTemplate jdbcTemplate;

	@GetMapping("/health")
	public HealthResponseDto health() {
		jdbcTemplate.queryForObject("select 1", Integer.class);
		return new HealthResponseDto("UP");
	}

}
