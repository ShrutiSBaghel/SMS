package org.project.sms.dto;

import java.time.Instant;

import lombok.Value;

@Value
public class LoginResponseDto {

	boolean authenticated;

	String name;

	Instant loginTime;

}
