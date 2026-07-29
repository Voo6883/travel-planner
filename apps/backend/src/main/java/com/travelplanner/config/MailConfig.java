package com.travelplanner.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Binds the task 09 property classes, and rejects a mail configuration that is unsafe for the
 * profile it is running under.
 *
 * <p>A configuration class of its own rather than another {@code @EnableConfigurationProperties} on
 * {@link SecurityConfig}: that class builds the HTTP filter chain, and hanging unrelated bindings
 * off it would mean the mail settings quietly stop existing the day somebody makes the security
 * chain conditional.
 */
@Configuration
@EnableConfigurationProperties({MailProperties.class, AccountLifecycleProperties.class})
public class MailConfig implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    private final MailProperties properties;
    private final Environment environment;

    public MailConfig(MailProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    /**
     * Runs once the properties are bound and before anything depends on them, so a prod deployment
     * with no mailer fails at boot rather than on the first sign-up.
     */
    @Override
    public void afterPropertiesSet() {
        MailConfigValidator.validate(properties, environment.getActiveProfiles());

        if (properties.isStub()) {
            // Stated once, at boot. Someone wondering why sign-up never asked them to confirm an
            // address should find the answer in the log rather than in the source.
            log.warn("Mailer is the stub — new accounts are created ALREADY VERIFIED and no mail "
                    + "is sent. Development only. Set MAILER_PROVIDER=resend with RESEND_API_KEY "
                    + "to require real verification; the application refuses to start as a stub "
                    + "under the 'prod' profile.");
        }
    }
}
