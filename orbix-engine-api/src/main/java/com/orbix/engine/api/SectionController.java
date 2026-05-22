package com.orbix.engine.api;

import com.orbix.engine.modules.admin.domain.dto.CreateSectionRequestDto;
import com.orbix.engine.modules.admin.domain.dto.SectionResponseDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateSectionRequestDto;
import com.orbix.engine.modules.admin.service.SectionService;
import com.orbix.engine.modules.common.validation.ValidUlid;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Admin section management (F1.1). Gated by {@code ADMIN.MANAGE_SECTIONS}. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN.MANAGE_SECTIONS')")
@Validated
public class SectionController {

    private final SectionService service;

    @GetMapping("/branches/uid/{branchUid}/sections")
    public List<SectionResponseDto> listSections(@PathVariable @ValidUlid String branchUid) {
        return service.listSectionsByBranchUid(branchUid);
    }

    @PostMapping("/branches/uid/{branchUid}/sections")
    public ResponseEntity<SectionResponseDto> createSection(
            @PathVariable @ValidUlid String branchUid,
            @Valid @RequestBody CreateSectionRequestDto request) {
        SectionResponseDto section = service.createSectionByBranchUid(branchUid, request);
        return ResponseEntity.created(URI.create("/api/v1/sections/uid/" + section.uid())).body(section);
    }

    @PatchMapping("/sections/uid/{uid}")
    public SectionResponseDto updateSection(@PathVariable @ValidUlid String uid,
                                            @Valid @RequestBody UpdateSectionRequestDto request) {
        return service.updateSectionByUid(uid, request);
    }

    @PostMapping("/sections/uid/{uid}/deactivate")
    public ResponseEntity<Void> deactivateSection(@PathVariable @ValidUlid String uid) {
        service.deactivateSectionByUid(uid);
        return ResponseEntity.noContent().build();
    }
}
