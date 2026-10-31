package org.project.sms.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.project.sms.dto.LoginRequestDto;
import org.project.sms.dto.LoginResponseDto;
import org.project.sms.repository.AuthRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final AuthRepository authRepository;

	private final PasswordEncoder passwordEncoder;

	@Transactional(readOnly = true)
	public LoginResponseDto login(LoginRequestDto request) {
		// BCrypt accepts at most 72 UTF-8 bytes; reject longer inputs without truncation.
		if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
			return new LoginResponseDto(false, null, null);
		}

		return authRepository.findByName(request.getName())
				.filter(user -> passwordEncoder.matches(request.getPassword(), user.getPassword()))
				.map(user -> new LoginResponseDto(true, user.getName(), Instant.now()))
				.orElseGet(() -> new LoginResponseDto(false, null, null));
	}

}
