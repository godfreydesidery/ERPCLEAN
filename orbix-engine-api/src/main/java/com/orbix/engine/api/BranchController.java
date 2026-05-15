package com.orbix.engine.api;

import com.orbix.engine.modules.admin.domain.dto.BranchResponseDto;
import com.orbix.engine.modules.admin.domain.dto.CreateBranchRequestDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateBranchRequestDto;
import com.orbix.engine.modules.admin.service.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Admin branch management (F1.1). Gated by {@code ADMIN.MANAGE_BRANCHES}. */
@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN.MANAGE_BRANCHES')")
public class BranchController {

    private final BranchService service;

    @GetMapping
    public List<BranchResponseDto> listBranches() {
        return service.listBranches();
    }

    @GetMapping("/{id}")
    public BranchResponseDto getBranch(@PathVariable Long id) {
        return service.getBranch(id);
    }

    @PostMapping
    public ResponseEntity<BranchResponseDto> createBranch(
            @Valid @RequestBody CreateBranchRequestDto request) {
        BranchResponseDto branch = service.createBranch(request);
        return ResponseEntity.created(URI.create("/api/v1/branches/" + branch.id())).body(branch);
    }

    @PatchMapping("/{id}")
    public BranchResponseDto updateBranch(@PathVariable Long id,
                                          @Valid @RequestBody UpdateBranchRequestDto request) {
        return service.updateBranch(id, request);
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateBranch(@PathVariable Long id) {
        service.deactivateBranch(id);
        return ResponseEntity.noContent().build();
    }
}
