package org.project.sms.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.project.sms.config.JwtConfig;
import org.project.sms.config.JwtProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTests {

	private final JwtConfig config = new JwtConfig();

	private final Instant now = Instant.parse("2030-01-01T12:00:00Z");

	private JwtProperties properties;

	private JwtEncoder encoder;

	private JwtService jwtService;

	@BeforeEach
	void configureJwt() {
		properties = new JwtProperties();
		properties.setSecret(Base64.getEncoder().encodeToString(new byte[32]));
		var key = config.jwtSigningKey(properties);
		var clock = Clock.fixed(now, ZoneOffset.UTC);
		encoder = config.jwtEncoder(key);
		jwtService = new JwtService(encoder, config.jwtDecoder(key, properties, clock), properties, clock);
	}

	@Test
	void tokenContainsVerifiedIdentityIssuerAndThirtyMinuteExpiry() {
		String token = jwtService.generateToken("tester");
		var jwt = jwtService.validateToken(token);

		assertThat(jwt.getSubject()).isEqualTo("tester");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("sms");
		assertThat(jwt.getIssuedAt()).isEqualTo(now);
		assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
		assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
	}

	@Test
	void validationRejectsExpiredFutureAndWrongIssuerTokens() {
		for (var claims : java.util.List.of(
				JwtClaimsSet.builder().issuer("sms").subject("tester").expiresAt(now.minusSeconds(1)).build(),
				JwtClaimsSet.builder().issuer("sms").subject("tester").notBefore(now.plusSeconds(1))
						.expiresAt(now.plusSeconds(60)).build(),
				JwtClaimsSet.builder().issuer("different").subject("tester").expiresAt(now.plusSeconds(60)).build(),
				JwtClaimsSet.builder().issuer("sms").subject("tester").issuedAt(now).build(),
				JwtClaimsSet.builder().issuer("sms").expiresAt(now.plusSeconds(60)).build())) {
			String token = encode(encoder, claims);
			assertThatThrownBy(() -> jwtService.validateToken(token)).isInstanceOf(JwtException.class);
		}
	}

	@Test
	void validationRejectsTokenSignedWithAnotherKeyAndMalformedToken() {
		var otherProperties = new JwtProperties();
		byte[] otherKey = new byte[32];
		java.util.Arrays.fill(otherKey, (byte) 1);
		otherProperties.setSecret(Base64.getEncoder().encodeToString(otherKey));
		String token = encode(config.jwtEncoder(config.jwtSigningKey(otherProperties)), JwtClaimsSet.builder()
				.issuer("sms").subject("tester").expiresAt(now.plusSeconds(60)).build());

		assertThatThrownBy(() -> jwtService.validateToken(token)).isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> jwtService.validateToken("not-a-token")).isInstanceOf(JwtException.class);
	}

	@Test
	void startupRejectsInvalidKeyAndNonpositiveExpiration() {
		properties.setSecret("not base64");
		assertThatThrownBy(() -> config.jwtSigningKey(properties)).isInstanceOf(IllegalArgumentException.class);
		properties.setSecret(Base64.getEncoder().encodeToString(new byte[16]));
		assertThatThrownBy(() -> config.jwtSigningKey(properties)).isInstanceOf(IllegalArgumentException.class);
		properties.setSecret(Base64.getEncoder().encodeToString(new byte[32]));
		properties.setExpiration(Duration.ZERO);
		assertThatThrownBy(() -> config.jwtSigningKey(properties)).isInstanceOf(IllegalArgumentException.class);
	}

	private String encode(JwtEncoder jwtEncoder, JwtClaimsSet claims) {
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

}
