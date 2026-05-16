package com.orbix.engine.modules.day.service;

import java.util.List;

/**
 * Raised by {@code BusinessDayService.startClosing} when one or more
 * {@link EodGuard} implementations report blockers. Carries the full list so
 * the controller can surface every blocker in a single 422 — the operator
 * resolves them in one pass instead of repeating the call per blocker.
 */
public class EodBlockedException extends RuntimeException {

    private final transient List<EodBlockerDto> blockers;

    public EodBlockedException(List<EodBlockerDto> blockers) {
        super(buildMessage(blockers));
        this.blockers = List.copyOf(blockers);
    }

    public List<EodBlockerDto> blockers() {
        return blockers;
    }

    private static String buildMessage(List<EodBlockerDto> blockers) {
        return "Cannot close day — " + blockers.size() + " blocker(s): "
            + blockers.stream().map(EodBlockerDto::message).toList();
    }
}
