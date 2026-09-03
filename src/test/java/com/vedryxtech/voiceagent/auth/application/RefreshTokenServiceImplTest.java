package com.vedryxtech.voiceagent.auth.application;

import com.vedryxtech.voiceagent.auth.domain.RefreshToken;
import com.vedryxtech.voiceagent.auth.persistence.RefreshTokenRepository;
import com.vedryxtech.voiceagent.exception.UnauthorizedException;
import com.vedryxtech.voiceagent.user.application.UserService;
import com.vedryxtech.voiceagent.user.domain.User;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The refresh contract in one file: issue → rotate → old rejected; logout revokes; a disabled
 * user's refresh is rejected (H-3).
 */
class RefreshTokenServiceImplTest {

    private RefreshTokenRepository repository;
    private UserService userService;
    private RefreshTokenServiceImpl service;
    private final Map<String, RefreshToken> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        userService = mock(UserService.class);
        store.clear();

        when(repository.save(any(RefreshToken.class))).thenAnswer(call -> {
            RefreshToken token = call.getArgument(0);
            if (token.getId() == null) {
                token.setId(new ObjectId());
            }
            store.put(token.getTokenHash(), token);
            return token;
        });
        when(repository.findByTokenHash(any(String.class)))
                .thenAnswer(call -> Optional.ofNullable(store.get(call.getArgument(0))));

        service = new RefreshTokenServiceImpl(repository, userService);
    }

    @Test
    void issued_tokens_start_valid_and_lookup_finds_them() {
        User user = enabledUser();
        RefreshTokenService.Issued issued = service.issue(user);

        assertThat(issued.token()).startsWith("vrt_");
        assertThat(issued.expiresInSeconds()).isEqualTo(30L * 24 * 3600);
        assertThat(store).hasSize(1);
        RefreshToken stored = store.values().iterator().next();
        assertThat(stored.isRevoked()).isFalse();
        assertThat(stored.isRotated()).isFalse();
        assertThat(stored.getExpiresAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    void rotate_invalidates_the_old_token_and_issues_a_successor() {
        User user = enabledUser();
        when(userService.findById(user.getIdAsString())).thenReturn(Optional.of(user));
        RefreshTokenService.Issued issued = service.issue(user);

        RefreshTokenService.Rotated rotated = service.rotate(issued.token());
        assertThat(rotated.newRefreshToken()).isNotBlank().isNotEqualTo(issued.token());

        // Old token can no longer be rotated a second time.
        assertThatThrownBy(() -> service.rotate(issued.token()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void a_disabled_user_cannot_refresh_and_their_token_is_poisoned() {
        User user = enabledUser();
        when(userService.findById(user.getIdAsString())).thenReturn(Optional.of(user));
        RefreshTokenService.Issued issued = service.issue(user);

        // Simulate the user being disabled between login and refresh.
        User disabled = disabledCopy(user);
        when(userService.findById(user.getIdAsString())).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.rotate(issued.token()))
                .isInstanceOf(UnauthorizedException.class);

        // And a second attempt fails at the store lookup because the token was revoked.
        assertThatThrownBy(() -> service.rotate(issued.token()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void a_deleted_user_cannot_refresh_either() {
        User user = enabledUser();
        when(userService.findById(user.getIdAsString())).thenReturn(Optional.of(user));
        RefreshTokenService.Issued issued = service.issue(user);

        // findById returns empty — user was deleted.
        when(userService.findById(user.getIdAsString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate(issued.token()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void logout_revokes_a_valid_token() {
        User user = enabledUser();
        when(userService.findById(user.getIdAsString())).thenReturn(Optional.of(user));
        RefreshTokenService.Issued issued = service.issue(user);

        service.revoke(issued.token());

        assertThatThrownBy(() -> service.rotate(issued.token()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void logout_is_idempotent_on_an_unknown_token() {
        service.revoke("vrt_totally-fake");
        // No save call, no throw.
        verify(repository, never()).save(any());
    }

    @Test
    void an_empty_refresh_is_refused_without_a_repo_hit() {
        assertThatThrownBy(() -> service.rotate("  "))
                .isInstanceOf(UnauthorizedException.class);
        verify(repository, never()).findByTokenHash(any());
    }

    private static User enabledUser() {
        User user = new User();
        user.setId(new ObjectId());
        user.setEmail("someone@vedryxtech.com");
        user.setEnabled(Boolean.TRUE);
        return user;
    }

    private static User disabledCopy(User source) {
        User copy = new User();
        copy.setId(source.getId());
        copy.setEmail(source.getEmail());
        copy.setEnabled(Boolean.FALSE);
        return copy;
    }
}
