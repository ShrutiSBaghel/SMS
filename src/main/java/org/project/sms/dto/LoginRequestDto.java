package org.project.sms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class LoginRequestDto {

	@NotBlank
	private String name;

	@NotBlank
	@ToString.Exclude
	private String password;

}
