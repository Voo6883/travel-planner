package com.travelplanner.application.auth;

import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.port.IdentityProviderPort;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Routes an authentication request to the one adapter that speaks for a provider (PLAN §4.0.5 —
 * the same pattern as LLM provider routing in §5.4).
 *
 * <p>Task 08 registers one adapter. Task 10 adds Firebase and GitHub by declaring beans; nothing
 * here changes and no service acquires a second code path. Without this indirection, adding a
 * provider would mean either an {@code if} in a service or an ambiguous-bean failure the moment a
 * second {@link IdentityProviderPort} exists.
 */
@Component
public class IdentityProviderRegistry {

    private final Map<AuthProvider, IdentityProviderPort> adapters;

    public IdentityProviderRegistry(List<IdentityProviderPort> providers) {
        Map<AuthProvider, IdentityProviderPort> byProvider = new EnumMap<>(AuthProvider.class);
        for (IdentityProviderPort provider : providers) {
            IdentityProviderPort previous = byProvider.put(provider.provider(), provider);
            if (previous != null) {
                // Two adapters for one provider means one of them silently never runs, and which
                // one wins depends on bean ordering. Fail at startup instead.
                throw new IllegalStateException(
                        "Two identity adapters claim " + provider.provider());
            }
        }
        this.adapters = Map.copyOf(byProvider);
    }

    public IdentityProviderPort forProvider(AuthProvider provider) {
        IdentityProviderPort adapter = adapters.get(provider);
        if (adapter == null) {
            throw new IllegalStateException("No identity adapter for " + provider);
        }
        return adapter;
    }
}
