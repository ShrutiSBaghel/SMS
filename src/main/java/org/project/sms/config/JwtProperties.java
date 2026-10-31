package org.project.sms.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "sms.jwt")
@Validated
@Getter
@Setter
public class JwtProperties {

	@NotBlank
	@Value("${sms.jwt.secret}")
	private String secret;

	@NotBlank
	private String issuer = "sms";

	@NotNull
	private Duration expiration = Duration.ofMinutes(30);

}
