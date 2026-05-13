package com.orbix.engine.api;

import com.orbix.engine.modules.admin.domain.dto.FirstRunRequestDto;
import com.orbix.engine.modules.admin.domain.dto.FirstRunResponseDto;
import com.orbix.engine.modules.admin.service.FirstRunSetupService;
import com.orbix.engine.modules.admin.service.FirstRunSetupService.AlreadyBootstrappedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/setup")
@RequiredArgsConstructor
public class SetupController {

    private final FirstRunSetupService firstRunSetupService;

    @PostMapping("/first-run")
    public ResponseEntity<FirstRunResponseDto> firstRun(@Valid @RequestBody FirstRunRequestDto request) {
        FirstRunResponseDto response = firstRunSetupService.bootstrap(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(AlreadyBootstrappedException.class)
    public ResponseEntity<Object> onAlreadyBootstrapped(AlreadyBootstrappedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "already_bootstrapped", "message", ex.getMessage()));
    }
}
