package com.vedryxtech.voiceagent.user.application;

import com.vedryxtech.voiceagent.exception.DuplicateResourceException;
import com.vedryxtech.voiceagent.exception.ResourceNotFoundException;
import com.vedryxtech.voiceagent.user.api.dto.CreateUserRequest;
import com.vedryxtech.voiceagent.user.domain.User;
import com.vedryxtech.voiceagent.user.domain.UserRole;
import com.vedryxtech.voiceagent.user.persistence.UserRepository;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User create(CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("A user with email '" + email + "' already exists");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        user.setRoles(rolesOrDefault(request.roles()));
        user.setEnabled(Boolean.TRUE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        User saved = repository.save(user);
        log.info("Created user {}", saved.getEmail());
        return saved;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmailIgnoreCase(normalizeEmail(email));
    }

    @Override
    public Optional<User> findById(String userId) {
        if (userId == null || !ObjectId.isValid(userId)) {
            return Optional.empty();
        }
        return repository.findById(new ObjectId(userId));
    }

    @Override
    public User require(String userId) {
        return findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("user", "id", String.valueOf(userId)));
    }

    @Override
    public List<User> listAll() {
        return repository.findAll();
    }

    @Override
    public User setEnabled(String userId, boolean enabled) {
        User user = require(userId);
        user.setEnabled(enabled);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return repository.save(user);
    }

    @Override
    public void recordSuccessfulLogin(User user) {
        user.setLastLoginAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(user);
    }

    private static Set<UserRole> rolesOrDefault(Set<UserRole> roles) {
        return roles == null || roles.isEmpty()
                ? new LinkedHashSet<>(Set.of(UserRole.MEMBER))
                : new LinkedHashSet<>(roles);
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
