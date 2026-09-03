package com.vedryxtech.voiceagent.user.application;

import com.vedryxtech.voiceagent.user.api.dto.CreateUserRequest;
import com.vedryxtech.voiceagent.user.domain.User;

import java.util.List;
import java.util.Optional;

/** Users, without tenant scoping — single-tenant now. */
public interface UserService {

    User create(CreateUserRequest request);

    Optional<User> findByEmail(String email);

    /** Non-throwing lookup used by the refresh path (H-3). */
    Optional<User> findById(String userId);

    User require(String userId);

    List<User> listAll();

    User setEnabled(String userId, boolean enabled);

    void recordSuccessfulLogin(User user);
}
