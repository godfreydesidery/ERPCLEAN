package com.orbix.engine.modules.production.service;

import com.orbix.engine.modules.production.domain.dto.BomDto;
import com.orbix.engine.modules.production.domain.dto.CreateBomRequestDto;
import com.orbix.engine.modules.production.domain.dto.PatchBomRequestDto;
import com.orbix.engine.modules.production.domain.enums.BomStatus;

import java.util.List;

/**
 * BOM authoring (F7.3a). DRAFT -> ACTIVE -> RETIRED lifecycle. Sub-recipe
 * cycle detection runs at activation (lines may reference other BOMs which
 * may themselves reference more BOMs; the BFS reaching its own id throws).
 */
public interface BomService {

    BomDto create(CreateBomRequestDto request);

    BomDto patch(String uid, PatchBomRequestDto request);

    /** DRAFT -> ACTIVE; runs the cycle check. */
    BomDto activate(String uid);

    /** ACTIVE -> RETIRED. Sets {@code valid_to = today}. */
    BomDto retire(String uid);

    /**
     * Clone the current ACTIVE version into a new DRAFT version (N+1), so the
     * recipe can be edited without disturbing in-flight batches. The caller
     * activates the new DRAFT separately; activation auto-retires the prior
     * ACTIVE at {@code newVersion.validFrom − 1}. Addressed by the source
     * BOM's {@code uid}.
     */
    BomDto version(String uid);

    BomDto get(String uid);

    List<BomDto> list(Long sectionId, Long outputItemId, BomStatus status);

    /**
     * Returns {@code true} when the section has at least one BOM that is ACTIVE or DRAFT
     * (i.e. not RETIRED). Used by the admin module to guard section deactivation (F7.3)
     * without violating the module-boundary rule (admin → production via service interface only).
     */
    boolean hasActiveBomForSection(Long sectionId);
}
