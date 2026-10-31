package org.project.sms.service;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.project.sms.dto.LoginRequestDto;
import org.project.sms.entity.User;
import org.project.sms.repository.AuthRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

	@Mock
	private AuthRepository authRepository;

	private AuthService authService;

	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

	@BeforeEach
	void configureService() {
		authService = new AuthService(authRepository, passwordEncoder);
	}

	@Test
	void loginReturnsVerifiedNameAndServerTimestamp() {
		var user = new User();
		user.setName("Verified name");
		user.setPassword(passwordEncoder.encode("test-password"));
		when(authRepository.findByName("tester")).thenReturn(Optional.of(user));
		var before = Instant.now();

		var response = authService.login(request("test-password"));

		assertThat(response.isAuthenticated()).isTrue();
		assertThat(response.getName()).isEqualTo("Verified name");
		assertThat(response.getLoginTime()).isBetween(before, Instant.now());
		assertThat(response.toString()).doesNotContain("test-password", user.getPassword());
	}

	@Test
	void loginRejectsWrongPasswordWithoutProfileData() {
		var user = new User();
		user.setName("tester");
		user.setPassword(passwordEncoder.encode("test-password"));
		when(authRepository.findByName("tester")).thenReturn(Optional.of(user));

		var response = authService.login(request("incorrect"));

		assertThat(response.isAuthenticated()).isFalse();
		assertThat(response.getName()).isNull();
		assertThat(response.getLoginTime()).isNull();
	}

	@Test
	void loginRejectsUnknownUser() {
		when(authRepository.findByName("tester")).thenReturn(Optional.empty());

		var response = authService.login(request("test-password"));

		assertThat(response.isAuthenticated()).isFalse();
		assertThat(response.getName()).isNull();
		assertThat(response.getLoginTime()).isNull();
	}

	@Test
	void loginRejectsPasswordExceedingBCryptByteLimit() {
		assertThat(authService.login(request("é".repeat(37))).isAuthenticated()).isFalse();
		verifyNoInteractions(authRepository);
	}

	@Test
	void requestToStringExcludesPassword() {
		assertThat(request("sensitive-password").toString()).doesNotContain("sensitive-password");
	}

	private LoginRequestDto request(String password) {
		var request = new LoginRequestDto();
		request.setName("tester");
		request.setPassword(password);
		return request;
	}

}
