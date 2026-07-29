package com.travelplanner.api.controller;

import com.travelplanner.api.dto.admin.AdminResetPasswordRequest;
import com.travelplanner.api.dto.admin.AdminUserDetailResponse;
import com.travelplanner.api.dto.admin.AdminUserPageResponse;
import com.travelplanner.api.dto.admin.UpdateAdminUserRequest;
import com.travelplanner.application.page.PageQuery;
import com.travelplanner.application.admin.AdminResetPasswordCommand;
import com.travelplanner.application.admin.AdminUserDirectory;
import com.travelplanner.application.admin.AdminUserService;
import com.travelplanner.application.admin.SetUserEnabledCommand;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.valueobject.UserContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account administration over HTTP (PLAN §4.0.6, UC-A15, UC-A16).
 *
 * <h2>{@code ADMIN} in the database, {@code ROLE_ADMIN} in Spring</h2>
 *
 * <p>PLAN §4.0.6 fixes both spellings and they are not interchangeable. {@code user.role} stores
 * {@code ADMIN} and the {@code /auth/me} payload publishes {@code ADMIN};
 * {@code JwtAuthenticationFilter} is the single place the framework's {@code ROLE_} prefix is
 * applied, granting the authority {@code ROLE_ADMIN}. {@code hasRole('ADMIN')} below re-adds that
 * prefix itself, which is why the argument is the bare name — writing {@code hasRole('ROLE_ADMIN')}
 * would look for {@code ROLE_ROLE_ADMIN} and deny everybody.
 *
 * <h2>Two independent checks, deliberately</h2>
 *
 * <p>{@code SecurityConfig} also requires {@code ROLE_ADMIN} for {@code /api/v1/admin/**} in the
 * filter chain. That is not redundancy for its own sake: the chain rule protects the whole prefix,
 * including a controller somebody adds later and forgets to annotate, while the annotation stays
 * with the code it guards so the requirement survives a routing change. A single misconfiguration
 * has to defeat both to open this surface.
 *
 * <h2>Routing only</h2>
 *
 * <p>Every rule lives in {@code application/admin/} ({@code AGENTS.md}: no business logic in
 * controllers). This class binds parameters, delegates, and maps the result — it makes no decision
 * about who may do what beyond the annotation above.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiresDatabase
public class AdminUserController {

    private final AdminUserService admin;
    private final AdminUserDirectory directory;

    public AdminUserController(AdminUserService admin, AdminUserDirectory directory) {
        this.admin = admin;
        this.directory = directory;
    }

    /**
     * UC-A15. {@code sort} accepts {@code created_at} and {@code -created_at} only; anything else is
     * a typed {@code validation_failed} rather than a silently ignored parameter.
     */
    @GetMapping
    public AdminUserPageResponse list(@RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) String sort) {
        PageQuery query = PageQuery.of(page, pageSize, sort);
        return AdminUserPageResponse.from(directory.list(query), query);
    }

    /** UC-A15. {@code 404 user_not_found} for an id that does not resolve. */
    @GetMapping("/{userId}")
    public AdminUserDetailResponse detail(@PathVariable UUID userId) {
        return AdminUserDetailResponse.from(directory.detail(userId));
    }

    /**
     * PLAN §4.0.6 — enable or disable. <b>Disabling terminates every session the account holds</b>
     * (ADR 009 §1), which happens before this method returns.
     *
     * <p>The caller comes from {@code @AuthenticationPrincipal}, built by the security filter from
     * the signed cookie and a live database read. It is never taken from the body, because an actor
     * a request can assert is not an actor an audit trail can rely on.
     */
    @PutMapping("/{userId}")
    public AdminUserDetailResponse update(@PathVariable UUID userId,
            @Valid @RequestBody UpdateAdminUserRequest request,
            @AuthenticationPrincipal UserContext caller) {
        return AdminUserDetailResponse.from(
                admin.setEnabled(new SetUserEnabledCommand(userId, request.enabled()), caller));
    }

    /**
     * UC-A16 — set a temporary password and terminate every session for the account (ADR 009 §1).
     *
     * <p>{@code 204}, with nothing in the body. The administrator already knows the value they sent
     * and communicates it out of band; echoing it back would put a working credential into a
     * response, a browser cache, and any proxy log between the two.
     */
    @PutMapping("/{userId}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID userId,
            @Valid @RequestBody AdminResetPasswordRequest request,
            @AuthenticationPrincipal UserContext caller) {
        admin.resetPassword(new AdminResetPasswordCommand(userId, request.newPassword()), caller);
        return ResponseEntity.noContent().build();
    }
}
