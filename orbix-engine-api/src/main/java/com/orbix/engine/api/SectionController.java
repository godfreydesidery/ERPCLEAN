package com.orbix.engine.api;

import com.orbix.engine.modules.admin.domain.dto.CreateSectionRequestDto;
import com.orbix.engine.modules.admin.domain.dto.SectionResponseDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateSectionRequestDto;
import com.orbix.engine.modules.admin.service.SectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Admin section management (F1.1). Gated by {@code ADMIN.MANAGE_SECTIONS}. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN.MANAGE_SECTIONS')")
public class SectionController {

    private final SectionService service;

    @GetMapping("/branches/{branchId}/sections")
    public List<SectionResponseDto> listSections(@PathVariable Long branchId) {
        return service.listSections(branchId);
    }

    @PostMapping("/branches/{branchId}/sections")
    public ResponseEntity<SectionResponseDto> createSection(
            @PathVariable Long branchId,
            @Valid @RequestBody CreateSectionRequestDto request) {
        SectionResponseDto section = service.createSection(branchId, request);
        return ResponseEntity.created(URI.create("/api/v1/sections/" + section.id())).body(section);
    }

    @PatchMapping("/sections/{id}")
    public SectionResponseDto updateSection(@PathVariable Long id,
                                            @Valid @RequestBody UpdateSectionRequestDto request) {
        return service.updateSection(id, request);
    }

    @PostMapping("/sections/{id}/deactivate")
    public ResponseEntity<Void> deactivateSection(@PathVariable Long id) {
        service.deactivateSection(id);
        return ResponseEntity.noContent().build();
    }
}
