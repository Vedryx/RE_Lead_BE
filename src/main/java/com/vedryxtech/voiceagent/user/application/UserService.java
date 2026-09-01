package com.vedryxtech.voiceagent.user.application;

import com.vedryxtech.voiceagent.user.domain.User;
import com.vedryxtech.voiceagent.user.api.dto.CreateUserRequest;

import java.util.List;
import java.util.Optional;

public interface UserService {

    /** Creates a user under the given organization. */
    User create(String organizationId, CreateUserRequest request);

    Optional<User> findByEmail(String email);

    User require(String userId);

    List<User> listAll();

    User setEnabled(String userId, boolean enabled);

    void recordSuccessfulLogin(User user);
}
