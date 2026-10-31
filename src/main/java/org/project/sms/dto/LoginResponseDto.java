package org.project.sms.dto;

import java.time.Instant;

import lombok.Value;
import lombok.ToString;

@Value
public class LoginResponseDto {

	boolean authenticated;

	String name;

	Instant loginTime;

	@ToString.Exclude
	String token;

}
