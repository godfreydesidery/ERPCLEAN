package com.orbix.engine.api;

import com.orbix.engine.modules.iam.domain.dto.AccessibleBranchDto;
import com.orbix.engine.modules.iam.domain.dto.SetActiveBranchRequestDto;
import com.orbix.engine.modules.iam.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Per-user session context (F0.5). Any authenticated user may inspect the
 * branches they can work in and switch their active branch.
 */
@RestController
@RequestMapping("/api/v1/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService service;

    @GetMapping("/branches")
    public List<AccessibleBranchDto> accessibleBranches() {
        return service.listAccessibleBranches();
    }

    @PutMapping("/active-branch")
    public ResponseEntity<Void> setActiveBranch(@Valid @RequestBody SetActiveBranchRequestDto request) {
        service.setActiveBranch(request.branchId());
        return ResponseEntity.noContent().build();
    }
}
