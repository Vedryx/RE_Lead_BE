package com.vedryxtech.voiceagent.user.api;

import com.vedryxtech.voiceagent.user.api.dto.CreateUserRequest;
import com.vedryxtech.voiceagent.user.api.dto.UpdateUserRequest;
import com.vedryxtech.voiceagent.user.api.dto.UserResponse;
import com.vedryxtech.voiceagent.user.application.UserService;
import com.vedryxtech.voiceagent.user.mapper.AccountMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "4. Users", description = "Manage teammates. Admin-only mutations; members may list.")
@RestController
@RequestMapping(path = "/api/v1/users", produces = "application/json")
public class UserController {

    private final UserService userService;
    private final AccountMapper mapper;

    public UserController(UserService userService, AccountMapper mapper) {
        this.userService = userService;
        this.mapper = mapper;
    }

    @Operation(summary = "Add a teammate", description = "Admins only.")
    @PostMapping(consumes = "application/json")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = mapper.toResponse(userService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "List teammates")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MEMBER')")
    public List<UserResponse> list() {
        return userService.listAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Operation(summary = "Enable or disable a teammate")
    @PatchMapping(path = "/{userId}", consumes = "application/json")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse patch(@PathVariable String userId,
                              @Valid @RequestBody UpdateUserRequest request) {
        return mapper.toResponse(userService.setEnabled(userId, request.enabled()));
    }
}
