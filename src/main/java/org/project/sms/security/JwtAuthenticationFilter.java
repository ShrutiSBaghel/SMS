package org.project.sms.security;

import java.io.IOException;
import java.util.List;

import org.project.sms.service.JwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return ("POST".equals(request.getMethod()) && "/auth/login".equals(request.getServletPath()))
				|| CorsUtils.isPreFlightRequest(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null) {
			chain.doFilter(request, response);
			return;
		}
		if (!header.regionMatches(true, 0, "Bearer ", 0, 7) || header.substring(7).isBlank()) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		try {
			var jwt = jwtService.validateToken(header.substring(7));
			var context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(jwt.getSubject(), null, List.of()));
			SecurityContextHolder.setContext(context);
		} catch (JwtException | IllegalArgumentException exception) {
			SecurityContextHolder.clearContext();
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		chain.doFilter(request, response);
	}

}
