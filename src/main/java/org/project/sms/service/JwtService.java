package org.project.sms.service;

import java.time.Clock;
import java.time.Instant;

import org.project.sms.config.JwtProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtService {

	private final JwtEncoder jwtEncoder;

	private final JwtDecoder jwtDecoder;

	private final JwtProperties jwtProperties;

	private final Clock jwtClock;

	public String generateToken(String name) {
		Instant now = jwtClock.instant();
		var claims = JwtClaimsSet.builder()
				.issuer(jwtProperties.getIssuer())
				.subject(name)
				.issuedAt(now)
				.notBefore(now)
				.expiresAt(now.plus(jwtProperties.getExpiration()))
				.build();

		return jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	public Jwt validateToken(String token) {
		return jwtDecoder.decode(token);
	}

}
