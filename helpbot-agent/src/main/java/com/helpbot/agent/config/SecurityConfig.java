package com.helpbot.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;


@Configuration
@EnableWebSecurity
public class SecurityConfig
{
	@Bean
	public PasswordEncoder passwordEncoder()
	{
		return new BCryptPasswordEncoder();
	}

	@Bean
	@Profile("local")
	public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder)
	{
		var customer = User.builder()
				.username("customer")
				.password(passwordEncoder.encode("customer"))
				.roles("CUSTOMER")
				.build();

		var employee = User.builder()
				.username("employee")
				.password(passwordEncoder.encode("employee"))
				.roles("EMPLOYEE")
				.build();

		return new InMemoryUserDetailsManager(customer, employee);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception
	{
		http
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
						.requestMatchers("/chat/**").hasAnyRole("CUSTOMER", "EMPLOYEE")
						.anyRequest().permitAll()
				)
				.httpBasic(Customizer.withDefaults());

		return http.build();
	}
}
