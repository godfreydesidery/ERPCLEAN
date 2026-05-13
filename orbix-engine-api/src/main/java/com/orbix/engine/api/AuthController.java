package com.orbix.engine.api;

import com.orbix.engine.platform.security.AuthService;
import com.orbix.engine.platform.security.AuthService.InvalidCredentialsException;
import com.orbix.engine.platform.security.LoginRequest;
import com.orbix.engine.platform.security.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Object> onInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(java.util.Map.of("error", "invalid_credentials", "message", ex.getMessage()));
    }
}
