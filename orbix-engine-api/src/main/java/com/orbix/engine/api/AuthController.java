package com.orbix.engine.api;

import com.orbix.engine.modules.auth.domain.dto.LoginRequestDto;
import com.orbix.engine.modules.auth.domain.dto.LoginResponseDto;
import com.orbix.engine.modules.auth.domain.dto.LogoutRequestDto;
import com.orbix.engine.modules.auth.domain.dto.RefreshRequestDto;
import com.orbix.engine.modules.auth.service.AuthService;
import com.orbix.engine.modules.auth.service.AuthService.InvalidCredentialsException;
import com.orbix.engine.modules.auth.service.AuthService.InvalidRefreshTokenException;
import com.orbix.engine.modules.common.domain.dto.ApiResponseDto;
import com.orbix.engine.modules.common.domain.enums.ResponseCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponseDto login(@Valid @RequestBody LoginRequestDto request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public LoginResponseDto refresh(@Valid @RequestBody RefreshRequestDto request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequestDto request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-everywhere")
    public ResponseEntity<Void> logoutEverywhere() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long userId = (Long) auth.getPrincipal();
        authService.logoutEverywhere(userId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponseDto<Object>> onInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiResponseDto.error(401, ResponseCode.AUTH_INVALID_CREDENTIALS, ex.getMessage())
        );
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiResponseDto<Object>> onInvalidRefresh(InvalidRefreshTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiResponseDto.error(401, ResponseCode.UNAUTHORIZED, ex.getMessage())
        );
    }
}
