package org.project.sms.config;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

	@Bean
	public SecretKey jwtSigningKey(JwtProperties properties) {
		byte[] key;
		try {
			key = Base64.getDecoder().decode(properties.getSecret());
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("JWT_SECRET must be Base64 encoded");
		}
		if (key.length < 32) {
			throw new IllegalArgumentException("JWT_SECRET must decode to at least 32 bytes");
		}
		if (properties.getExpiration().isZero() || properties.getExpiration().isNegative()) {
			throw new IllegalArgumentException("JWT expiration must be positive");
		}

		return new SecretKeySpec(key, "HmacSHA256");
	}

	@Bean
	public Clock jwtClock() {
		return Clock.systemUTC();
	}

	@Bean
	public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	@Bean
	public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties, Clock jwtClock) {
		var decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
		var timestampValidator = new JwtTimestampValidator(Duration.ZERO);
		timestampValidator.setClock(jwtClock);
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestampValidator,
				new JwtIssuerValidator(properties.getIssuer()),
				new JwtClaimValidator<String>(JwtClaimNames.SUB, subject -> subject != null && !subject.isBlank()),
				new JwtClaimValidator<Object>(JwtClaimNames.EXP, expiration -> expiration != null)));
		return decoder;
	}

}
