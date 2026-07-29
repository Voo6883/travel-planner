package com.travelplanner.config;

/**
 * Refuses to start a production deployment that has no real mailer.
 *
 * <p><strong>Why this exists.</strong> {@code RegistrationService} creates accounts already
 * verified when {@code travelplanner.mail.provider=stub}, because the stub has no mailbox and a
 * verification link that goes nowhere makes sign-up impossible to complete. That is correct for
 * local development and CI, and catastrophic in production: every address would be treated as
 * proven without anybody receiving a mail, so a person could register
 * {@code someone-else@example.com} and be accepted as its owner.
 *
 * <p>The safe default and the safe deployment are therefore different values, and the gap between
 * them is exactly the kind of thing discovered months later. This turns "deployed to prod and
 * forgot {@code RESEND_API_KEY}" from a silent account-takeover route into a boot failure naming
 * the variable to set.
 *
 * <p>A plain static class called from {@link MailConfig}, mirroring {@code AiConfigValidator}: the
 * rule is conditional on the active profile and the message has to name the knob, which is more
 * than a {@code @Validated} annotation can express.
 */
final class MailConfigValidator {

    static final String PROD_PROFILE = "prod";

    private MailConfigValidator() {
    }

    /**
     * @param activeProfiles from {@code Environment.getActiveProfiles()}
     * @throws IllegalStateException with an actionable message; the context then fails to start
     */
    static void validate(MailProperties properties, String[] activeProfiles) {
        if (!properties.isStub()) {
            return;
        }
        if (isProduction(activeProfiles)) {
            throw new IllegalStateException(
                    "travelplanner.mail.provider=" + MailProperties.STUB_PROVIDER + " under the '"
                            + PROD_PROFILE + "' profile. The stub sends nothing, so registration "
                            + "would mark every address verified without anyone proving they own "
                            + "it. Set MAILER_PROVIDER=resend and RESEND_API_KEY.");
        }
    }

    private static boolean isProduction(String[] activeProfiles) {
        for (String profile : activeProfiles) {
            if (PROD_PROFILE.equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
