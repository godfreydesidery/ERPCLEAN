package com.orbix.engine.api;

import com.orbix.engine.modules.admin.domain.dto.BranchResponseDto;
import com.orbix.engine.modules.admin.domain.dto.CreateBranchRequestDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateBranchRequestDto;
import com.orbix.engine.modules.admin.service.BranchService;
import com.orbix.engine.modules.common.validation.ValidUlid;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Admin branch management (F1.1). Gated by {@code ADMIN.MANAGE_BRANCHES}. */
@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN.MANAGE_BRANCHES')")
@Validated
public class BranchController {

    private final BranchService service;

    @GetMapping
    public List<BranchResponseDto> listBranches() {
        return service.listBranches();
    }

    @GetMapping("/uid/{uid}")
    public BranchResponseDto getBranch(@PathVariable @ValidUlid String uid) {
        return service.getBranchByUid(uid);
    }

    @PostMapping
    public ResponseEntity<BranchResponseDto> createBranch(
            @Valid @RequestBody CreateBranchRequestDto request) {
        BranchResponseDto branch = service.createBranch(request);
        return ResponseEntity.created(URI.create("/api/v1/branches/uid/" + branch.uid())).body(branch);
    }

    @PatchMapping("/uid/{uid}")
    public BranchResponseDto updateBranch(@PathVariable @ValidUlid String uid,
                                          @Valid @RequestBody UpdateBranchRequestDto request) {
        return service.updateBranchByUid(uid, request);
    }

    @PostMapping("/uid/{uid}/deactivate")
    public ResponseEntity<Void> deactivateBranch(@PathVariable @ValidUlid String uid) {
        service.deactivateBranchByUid(uid);
        return ResponseEntity.noContent().build();
    }
}
