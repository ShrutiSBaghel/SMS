package org.project.sms.controller;

import org.project.sms.dto.LoginRequestDto;
import org.project.sms.dto.LoginResponseDto;
import org.project.sms.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@CrossOrigin(value = "http://localhost:4200")
public class AuthController {

	private final AuthService authService;

	@PostMapping("/login")
	public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
		var response = authService.login(request);

		return ResponseEntity.status(response.isAuthenticated() ? HttpStatus.OK : HttpStatus.UNAUTHORIZED)
				.body(response);
	}

}
