package com.orbix.engine.modules.production.service;

import com.orbix.engine.modules.admin.domain.entity.Section;
import com.orbix.engine.modules.admin.repository.SectionRepository;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.production.domain.dto.BomDto;
import com.orbix.engine.modules.production.domain.dto.CreateBomRequestDto;
import com.orbix.engine.modules.production.domain.dto.PatchBomRequestDto;
import com.orbix.engine.modules.production.domain.entity.Bom;
import com.orbix.engine.modules.production.domain.entity.BomLine;
import com.orbix.engine.modules.production.domain.enums.BomStatus;
import com.orbix.engine.modules.production.repository.BomLineRepository;
import com.orbix.engine.modules.production.repository.BomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BomServiceImpl implements BomService {

    private static final String AGG = "Bom";

    private final BomRepository boms;
    private final BomLineRepository bomLines;
    private final ItemRepository items;
    private final SectionRepository sections;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;

    // ---------------------------------------------------------------------
    // Create / patch
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = AGG)
    public BomDto create(CreateBomRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        Item outputItem = requireItem(request.outputItemId(), companyId);
        requireSection(request.sectionId());
        if (request.parentBomId() != null) {
            requireBomById(request.parentBomId());
        }

        int nextVersion = boms.findTopByOutputItemIdOrderByVersionDesc(outputItem.getId())
            .map(b -> b.getVersion() + 1)
            .orElse(1);

        Long outputUomId = request.outputUomId() != null
            ? request.outputUomId()
            : outputItem.getUomId();
        LocalDate validFrom = request.validFrom() != null ? request.validFrom() : LocalDate.now();

        Bom bom = boms.save(new Bom(
            companyId, request.sectionId(), request.parentBomId(),
            outputItem.getId(), request.outputQty(), outputUomId,
            nextVersion, validFrom, request.standardYieldPct(),
            request.notes(), actorId));
        List<BomLine> savedLines = saveLines(bom, request.lines(), companyId);

        events.publish("BomDefined.v1", AGG, String.valueOf(bom.getId()),
            Map.of("bomId", bom.getId(),
                "outputItemId", bom.getOutputItemId(),
                "version", bom.getVersion(),
                "sectionId", bom.getSectionId(),
                "lines", savedLines.size()));
        return BomDto.from(bom, savedLines);
    }

    @Override
    @Transactional
    @Auditable(action = "PATCH", entityType = AGG)
    public BomDto patch(String uid, PatchBomRequestDto request) {
        Bom bom = requireBomByUid(uid);
        if (bom.getStatus() != BomStatus.DRAFT) {
            throw new IllegalStateException(
                "BOM is only editable while DRAFT (was " + bom.getStatus() + ")");
        }
        bom.editHeader(request.outputQty(), request.outputUomId(),
            request.standardYieldPct(), request.notes(), request.validFrom(), context.userId());
        bomLines.deleteByBomId(bom.getId());
        List<BomLine> savedLines = saveLines(bom, request.lines(), bom.getCompanyId());

        events.publish("BomPatched.v1", AGG, String.valueOf(bom.getId()),
            Map.of("bomId", bom.getId(),
                "version", bom.getVersion(),
                "lines", savedLines.size()));
        return BomDto.from(bom, savedLines);
    }

    // ---------------------------------------------------------------------
    // Activate / retire / version
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    @Auditable(action = "ACTIVATE", entityType = AGG)
    public BomDto activate(String uid) {
        Bom bom = requireBomByUid(uid);
        List<BomLine> lines = bomLines.findByBomIdOrderByLineNoAsc(bom.getId());
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Cannot activate a BOM with no lines");
        }
        assertNoSubBomCycle(bom);

        // Auto-retire any prior ACTIVE version of the same output item at
        // validFrom - 1 day so reporting can date-slice cleanly.
        boms.findByCompanyIdAndOutputItemIdOrderByVersionDesc(bom.getCompanyId(), bom.getOutputItemId())
            .stream()
            .filter(prior -> !Objects.equals(prior.getId(), bom.getId()))
            .filter(prior -> prior.getStatus() == BomStatus.ACTIVE)
            .forEach(prior -> prior.retire(bom.getValidFrom().minusDays(1), context.userId()));

        bom.activate(context.userId());
        events.publish("BomActivated.v1", AGG, String.valueOf(bom.getId()),
            Map.of("bomId", bom.getId(),
                "outputItemId", bom.getOutputItemId(),
                "version", bom.getVersion(),
                "validFrom", bom.getValidFrom().toString()));
        return BomDto.from(bom, lines);
    }

    @Override
    @Transactional
    @Auditable(action = "RETIRE", entityType = AGG)
    public BomDto retire(String uid) {
        Bom bom = requireBomByUid(uid);
        bom.retire(LocalDate.now(), context.userId());
        events.publish("BomRetired.v1", AGG, String.valueOf(bom.getId()),
            Map.of("bomId", bom.getId(),
                "outputItemId", bom.getOutputItemId(),
                "version", bom.getVersion()));
        return BomDto.from(bom, bomLines.findByBomIdOrderByLineNoAsc(bom.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "VERSION", entityType = AGG)
    public BomDto version(String uid) {
        Bom source = requireBomByUid(uid);
        if (source.getStatus() != BomStatus.ACTIVE) {
            throw new IllegalStateException(
                "Only ACTIVE BOMs can be versioned (was " + source.getStatus() + ")");
        }
        int nextVersion = boms.findTopByOutputItemIdOrderByVersionDesc(source.getOutputItemId())
            .map(b -> b.getVersion() + 1)
            .orElse(source.getVersion() + 1);

        Bom next = boms.save(new Bom(
            source.getCompanyId(), source.getSectionId(), source.getParentBomId(),
            source.getOutputItemId(), source.getOutputQty(), source.getOutputUomId(),
            nextVersion, LocalDate.now(), source.getStandardYieldPct(),
            source.getNotes(), context.userId()));
        List<BomLine> sourceLines = bomLines.findByBomIdOrderByLineNoAsc(source.getId());
        List<BomLine> clonedLines = new ArrayList<>(sourceLines.size());
        for (BomLine src : sourceLines) {
            clonedLines.add(bomLines.save(new BomLine(
                next.getId(), src.getLineNo(),
                src.getInputItemId(), src.getSubBomId(),
                src.getQty(), src.getUomId(), src.getWastagePct(), src.getNotes())));
        }

        events.publish("BomVersioned.v1", AGG, String.valueOf(next.getId()),
            Map.of("bomId", next.getId(),
                "previousBomId", source.getId(),
                "outputItemId", next.getOutputItemId(),
                "version", next.getVersion()));
        return BomDto.from(next, clonedLines);
    }

    // ---------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public BomDto get(String uid) {
        Bom bom = requireBomByUid(uid);
        return BomDto.from(bom, bomLines.findByBomIdOrderByLineNoAsc(bom.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BomDto> list(Long sectionId, Long outputItemId, BomStatus status) {
        Long companyId = context.companyId();
        List<Bom> rows;
        if (outputItemId != null) {
            rows = boms.findByCompanyIdAndOutputItemIdOrderByVersionDesc(companyId, outputItemId);
        } else if (sectionId != null) {
            rows = boms.findByCompanyIdAndSectionIdOrderByIdDesc(companyId, sectionId);
        } else if (status != null) {
            rows = boms.findByCompanyIdAndStatusOrderByIdDesc(companyId, status);
        } else {
            rows = boms.findByCompanyIdOrderByIdDesc(companyId);
        }
        return rows.stream()
            .filter(b -> status == null || b.getStatus() == status)
            .filter(b -> sectionId == null || Objects.equals(b.getSectionId(), sectionId))
            .map(b -> BomDto.from(b, bomLines.findByBomIdOrderByLineNoAsc(b.getId())))
            .toList();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private List<BomLine> saveLines(Bom bom, List<CreateBomRequestDto.Line> input, Long companyId) {
        List<BomLine> saved = new ArrayList<>(input.size());
        int lineNo = 1;
        for (CreateBomRequestDto.Line in : input) {
            Long uomId = in.uomId();
            if (in.inputItemId() != null) {
                Item item = requireItem(in.inputItemId(), companyId);
                if (uomId == null) uomId = item.getUomId();
            } else if (in.subBomId() != null) {
                Bom sub = requireBomById(in.subBomId());
                if (uomId == null) uomId = sub.getOutputUomId();
            } else {
                throw new IllegalArgumentException(
                    "Line " + lineNo + " must reference exactly one of inputItemId / subBomId");
            }
            if (uomId == null) {
                throw new IllegalArgumentException(
                    "Line " + lineNo + " has no resolvable UoM");
            }
            saved.add(bomLines.save(new BomLine(
                bom.getId(), lineNo++,
                in.inputItemId(), in.subBomId(),
                in.qty(), uomId, in.wastagePct(), in.notes())));
        }
        return saved;
    }

    /**
     * BFS over the sub-BOM graph rooted at {@code root}. Throws if the
     * traversal re-encounters {@code root.id} — that would mean a cycle.
     */
    private void assertNoSubBomCycle(Bom root) {
        Set<Long> seen = new HashSet<>();
        Deque<Long> frontier = new ArrayDeque<>();
        for (BomLine line : bomLines.findByBomIdOrderByLineNoAsc(root.getId())) {
            if (line.getSubBomId() != null) frontier.add(line.getSubBomId());
        }
        while (!frontier.isEmpty()) {
            Long nextBomId = frontier.poll();
            if (Objects.equals(nextBomId, root.getId())) {
                throw new IllegalArgumentException(
                    "Circular sub-BOM reference detected — BOM " + root.getId()
                        + " transitively references itself");
            }
            if (!seen.add(nextBomId)) continue;
            for (BomLine child : bomLines.findByBomIdOrderByLineNoAsc(nextBomId)) {
                if (child.getSubBomId() != null) frontier.add(child.getSubBomId());
            }
        }
    }

    /** External entry-point lookup by {@code uid} (URL identifier). */
    private Bom requireBomByUid(String uid) {
        Bom bom = boms.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("BOM not found: " + uid));
        if (!Objects.equals(bom.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("BOM not found: " + uid);
        }
        return bom;
    }

    /** Internal lookup by numeric id — for body-level joins (parentBomId, subBomId). */
    private Bom requireBomById(Long id) {
        Bom bom = boms.findById(id)
            .orElseThrow(() -> new NoSuchElementException("BOM not found: " + id));
        if (!Objects.equals(bom.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("BOM not found: " + id);
        }
        return bom;
    }

    private Item requireItem(Long itemId, Long companyId) {
        Item item = items.findById(itemId)
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        if (!Objects.equals(item.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Item not found: " + itemId);
        }
        return item;
    }

    private Section requireSection(Long sectionId) {
        Section section = sections.findById(sectionId)
            .orElseThrow(() -> new NoSuchElementException("Section not found: " + sectionId));
        branchScope.requireAccess(section.getBranchId());
        return section;
    }
}
