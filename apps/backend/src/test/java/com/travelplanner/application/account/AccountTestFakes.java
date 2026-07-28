package com.travelplanner.application.account;

import com.travelplanner.application.auth.AuthTestFakes.FakeHasher;
import com.travelplanner.application.auth.AuthTestFakes.FakeIdentities;
import com.travelplanner.application.auth.AuthTestFakes.FakeRefreshTokens;
import com.travelplanner.application.auth.AuthTestFakes.FakeUsers;
import com.travelplanner.application.auth.PasswordPolicy;
import com.travelplanner.application.auth.SessionRevocationService;
import com.travelplanner.application.mail.AccountMailService;
import com.travelplanner.application.mail.MailDispatcher;
import com.travelplanner.application.mail.MailTemplateRenderer;
import com.travelplanner.config.AccountLifecycleProperties;
import com.travelplanner.config.AuthSecurityProperties;
import com.travelplanner.config.MailProperties;
import com.travelplanner.domain.enums.AccountTokenPurpose;
import com.travelplanner.domain.model.AccountToken;
import com.travelplanner.domain.port.AccountTokenPort;
import com.travelplanner.domain.port.MailRateLimitPort;
import com.travelplanner.domain.port.MailerPort;
import com.travelplanner.domain.valueobject.MailMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory ports and real collaborators for the account-lifecycle unit tests.
 *
 * <p><strong>The mail stack is real, not mocked.</strong> {@link MailTemplateRenderer},
 * {@link MailDispatcher}, and {@link AccountMailService} are the production classes; only the
 * outermost {@link MailerPort} is replaced by {@link CapturingMailer}. That is deliberate: it means
 * these tests exercise the actual templates on the classpath, so a missing file, a placeholder with
 * no model value, or an unescaped username is a unit-test failure rather than something discovered
 * when a user does not receive a link.
 *
 * <p>Hand-written fakes rather than mocks, matching {@code AuthTestFakes}: these tests are about
 * behaviour across several calls — a token is issued, redeemed, then redeemed again — and a mock
 * returning a canned value per call would let a broken implementation pass.
 */
public final class AccountTestFakes {

    /** Anything that satisfies the default policy; the value itself is never meaningful. */
    public static final String STRONG_PASSWORD = "correct-horse-battery";

    private AccountTestFakes() {
    }

    public static PasswordPolicy passwordPolicy() {
        return new PasswordPolicy(new FakeHasher(), new AuthSecurityProperties());
    }

    public static SessionRevocationService revocation(FakeUsers users) {
        return new SessionRevocationService(users, new FakeRefreshTokens());
    }

    public static AccountStore accountStore(FakeUsers users) {
        return new AccountStore(users, passwordPolicy(), revocation(users));
    }

    public static AccountTokenService tokenService(FakeAccountTokens tokens) {
        return new AccountTokenService(tokens, new AccountLifecycleProperties());
    }

    /** The real renderer and dispatcher over a capturing mailer, so templates are exercised. */
    public static AccountMailService mailService(CapturingMailer mailer) {
        return new AccountMailService(new MailTemplateRenderer(), new MailDispatcher(mailer),
                mailProperties());
    }

    public static AccountLifecycleMailer lifecycleMailer(CapturingMailer mailer,
            FakeMailRateLimits limits, FakeIdentities identities) {
        return new AccountLifecycleMailer(mailService(mailer),
                new MailRateLimiter(limits, new AccountLifecycleProperties()), identities);
    }

    /** A base URL with no trailing slash and a recognisable host, so link assertions read clearly. */
    public static MailProperties mailProperties() {
        MailProperties properties = new MailProperties();
        properties.setAppBaseUrl("https://app.example.test");
        return properties;
    }

    /** Everything the mailed message carries, for a test that wants to follow a link. */
    public static final class CapturingMailer implements MailerPort {

        public final List<MailMessage> sent = new ArrayList<>();
        /** When set, every send throws — the provider-outage case. */
        public RuntimeException failure;

        @Override
        public void send(MailMessage message) {
            if (failure != null) {
                throw failure;
            }
            sent.add(message);
        }

        public MailMessage only() {
            if (sent.size() != 1) {
                throw new AssertionError("expected exactly one message, got " + sent.size());
            }
            return sent.get(0);
        }

        public MailMessage last() {
            return sent.get(sent.size() - 1);
        }

        public List<String> subjects() {
            return sent.stream().map(MailMessage::subject).toList();
        }

        /** Read-only view, for assertions that iterate rather than index. */
        public List<MailMessage> sent() {
            return List.copyOf(sent);
        }
    }

    /** {@link AccountTokenPort} in memory, including the conditional-consume race semantics. */
    public static final class FakeAccountTokens implements AccountTokenPort {

        public final Map<UUID, AccountToken> byId = new LinkedHashMap<>();

        @Override
        public AccountToken save(AccountToken token) {
            byId.put(token.id(), token);
            return token;
        }

        @Override
        public Optional<AccountToken> findByTokenHash(String tokenHash) {
            return byId.values().stream()
                    .filter(token -> token.tokenHash().equals(tokenHash)).findFirst();
        }

        @Override
        public int consume(UUID tokenId, Instant consumedAt) {
            AccountToken token = byId.get(tokenId);
            // Mirrors `UPDATE … WHERE consumed_at IS NULL`: a second attempt updates no row, which
            // is what makes the token single-use under a race rather than merely by convention.
            if (token == null || token.isConsumed()) {
                return 0;
            }
            byId.put(tokenId, token.consumedAt(consumedAt));
            return 1;
        }

        @Override
        public int consumeAllForUser(UUID userId, AccountTokenPurpose purpose, Instant consumedAt) {
            List<AccountToken> live = byId.values().stream()
                    .filter(token -> token.userId().equals(userId) && token.purpose() == purpose
                            && !token.isConsumed())
                    .toList();
            live.forEach(token -> byId.put(token.id(), token.consumedAt(consumedAt)));
            return live.size();
        }

        @Override
        public int purgeExpiredBefore(Instant cutoff) {
            List<AccountToken> expired = byId.values().stream()
                    .filter(token -> token.expiresAt().isBefore(cutoff)).toList();
            expired.forEach(token -> byId.remove(token.id()));
            return expired.size();
        }

        public Optional<AccountToken> liveFor(UUID userId, AccountTokenPurpose purpose) {
            return byId.values().stream()
                    .filter(token -> token.userId().equals(userId) && token.purpose() == purpose
                            && !token.isConsumed())
                    .findFirst();
        }
    }

    /** {@link MailRateLimitPort} in memory. Records are never cleared unless a test asks. */
    public static final class FakeMailRateLimits implements MailRateLimitPort {

        public final List<String> recorded = new ArrayList<>();

        @Override
        public int countSince(String scope, String subjectHash, Instant since) {
            return (int) recorded.stream().filter(key(scope, subjectHash)::equals).count();
        }

        @Override
        public void record(String scope, String subjectHash) {
            recorded.add(key(scope, subjectHash));
        }

        @Override
        public int purgeOlderThan(Instant cutoff) {
            return 0;
        }

        private static String key(String scope, String subjectHash) {
            return scope + "|" + subjectHash;
        }
    }
}
