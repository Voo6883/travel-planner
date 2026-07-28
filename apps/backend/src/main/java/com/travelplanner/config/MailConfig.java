package com.travelplanner.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the task 09 property classes.
 *
 * <p>A configuration class of its own rather than another {@code @EnableConfigurationProperties} on
 * {@link SecurityConfig}: that class builds the HTTP filter chain, and hanging unrelated bindings
 * off it would mean the mail settings quietly stop existing the day somebody makes the security
 * chain conditional.
 */
@Configuration
@EnableConfigurationProperties({MailProperties.class, AccountLifecycleProperties.class})
public class MailConfig {
}
