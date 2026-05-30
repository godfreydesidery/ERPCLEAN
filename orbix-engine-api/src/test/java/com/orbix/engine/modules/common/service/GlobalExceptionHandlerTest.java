package com.orbix.engine.modules.common.service;

import com.orbix.engine.modules.common.domain.enums.ResponseCode;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit-tests for {@link GlobalExceptionHandler} — each test verifies one
 * exception-to-HTTP-status mapping via standaloneSetup MockMvc.
 * No Spring context or database required.
 *
 * <p>Covers:
 * <ul>
 *   <li>ISSUE-CAT-001 / ISSUE-PARTY-001 / ISSUE-NFR-001 / ISSUE-NEG-ENUM-500-01:
 *       invalid enum → 400</li>
 *   <li>ISSUE-VALIDATION-01: malformed JSON → 400;
 *       ConstraintViolationException (@ValidUlid family) → 400</li>
 *   <li>ISSUE-DAY-002 / ISSUE-GC-002 / ISSUE-POS-002:
 *       BusinessPreconditionException → 422 with typed responseCode</li>
 *   <li>ISSUE-PROC-002 / bare IllegalStateException → 422</li>
 *   <li>ISSUE-DAY-001: ResourceConflictException → 409</li>
 *   <li>Existing: IllegalArgumentException → 400</li>
 *   <li>Existing: NoSuchElementException → 404</li>
 *   <li>Existing: MethodArgumentNotValidException (bean-val) → 422</li>
 * </ul>
 */
class GlobalExceptionHandlerTest {

    // ---- minimal stub controller -------------------------------------------

    enum TestEnum { ALPHA, BETA }

    record TestBodyDto(@NotBlank String name, TestEnum kind) {}

    @RestController
    @RequestMapping("/test")
    static class StubController {

        @GetMapping("/nse")
        String notFound() {
            throw new NoSuchElementException("entity not found");
        }

        @GetMapping("/illegal-arg")
        String illegalArg() {
            throw new IllegalArgumentException("bad argument");
        }

        @GetMapping("/illegal-state")
        String illegalState() {
            throw new IllegalStateException("wrong lifecycle state");
        }

        @GetMapping("/precondition")
        String precondition() {
            throw new BusinessPreconditionException(
                ResponseCode.BUSINESS_DAY_CLOSED, "No open business day for branch 2");
        }

        @GetMapping("/conflict")
        String conflict() {
            throw new ResourceConflictException("Branch already has a non-closed business day");
        }

        @GetMapping("/cve")
        String constraintViolation() {
            // Simulate what Spring's @Validated + @ValidUlid raises when a
            // path variable fails constraint validation (ISSUE-VALIDATION-01).
            // An empty violation set is enough to exercise the handler mapping.
            throw new ConstraintViolationException("must be a 26-character Crockford ULID", Set.of());
        }

        @PostMapping("/body")
        String body(@RequestBody @Valid TestBodyDto dto) {
            return dto.name();
        }
    }

    // -----------------------------------------------------------------------

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
            .standaloneSetup(new StubController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    // ISSUE-VALIDATION-01: ConstraintViolationException (@ValidUlid family) → 400
    @Test
    void constraintViolation_returns400() throws Exception {
        mvc.perform(get("/test/cve"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value(400))
            .andExpect(jsonPath("$.responseCode").value(ResponseCode.BAD_REQUEST));
    }

    // ISSUE-CAT-001 / ISSUE-PARTY-001 / ISSUE-NFR-001 / ISSUE-NEG-ENUM-500-01:
    // invalid enum value in request body → 400 (not 500)
    @Test
    void invalidEnumInBody_returns400() throws Exception {
        String json = "{\"name\":\"test\",\"kind\":\"NOT_A_REAL_VALUE\"}";
        mvc.perform(post("/test/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value(400))
            .andExpect(jsonPath("$.responseCode").value(ResponseCode.BAD_REQUEST))
            // must mention the bad value so the client knows what to fix
            .andExpect(jsonPath("$.message", containsString("NOT_A_REAL_VALUE")));
    }

    // ISSUE-VALIDATION-01: truncated / malformed JSON body → 400 (not 500)
    @Test
    void malformedJsonBody_returns400() throws Exception {
        String truncated = "{\"name\":\"test\"";   // no closing brace
        mvc.perform(post("/test/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content(truncated))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value(400))
            .andExpect(jsonPath("$.responseCode").value(ResponseCode.BAD_REQUEST));
    }

    // Existing: bean-validation (missing required field) → 422
    @Test
    void beanValidationFailure_returns422() throws Exception {
        String json = "{\"name\":\"\",\"kind\":\"ALPHA\"}"; // blank name fails @NotBlank
        mvc.perform(post("/test/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.statusCode").value(422))
            .andExpect(jsonPath("$.responseCode").value(ResponseCode.VALIDATION_FAILED))
            .andExpect(jsonPath("$.errors", hasSize(greaterThan(0))));
    }

    // Existing: IllegalArgumentException → 400
    @Test
    void illegalArgumentException_returns400() throws Exception {
        mvc.perform(get("/test/illegal-arg"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value(400))
            .andExpect(jsonPath("$.responseCode").value(ResponseCode.BAD_REQUEST))
            .andExpect(jsonPath("$.message").value("bad argument"));
    }

    // ISSUE-DAY-002 / ISSUE-PROC-002: bare IllegalStateException → 422 (not 500)
    @Test
    void illegalStateException_returns422() throws Exception {
        mvc.perform(get("/test/illegal-state"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.statusCode").value(422))
            .andExpect(jsonPath("$.responseCode").value(ResponseCode.PRECONDITION_FAILED))
            .andExpect(jsonPath("$.message").value("wrong lifecycle state"));
    }

    // ISSUE-DAY-002 / ISSUE-GC-002 / ISSUE-POS-002:
    // BusinessPreconditionException → 422 with typed responseCode
    @Test
    void businessPreconditionException_returns422WithTypedCode() throws Exception {
        mvc.perform(get("/test/precondition"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.statusCode").value(422))
            .andExpect(jsonPath("$.responseCode").value(ResponseCode.BUSINESS_DAY_CLOSED))
            .andExpect(jsonPath("$.message", containsString("branch 2")));
    }

    // ISSUE-DAY-001: ResourceConflictException → 409 (not 400)
    @Test
    void resourceConflictException_returns409() throws Exception {
        mvc.perform(get("/test/conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.statusCode").value(409))
            .andExpect(jsonPath("$.responseCode").value(ResponseCode.CONFLICT))
            .andExpect(jsonPath("$.message", containsString("non-closed business day")));
    }

    // Existing: NoSuchElementException → 404
    @Test
    void noSuchElementException_returns404() throws Exception {
        mvc.perform(get("/test/nse"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value(404))
            .andExpect(jsonPath("$.responseCode").value(ResponseCode.NOT_FOUND))
            .andExpect(jsonPath("$.message").value("entity not found"));
    }

    // All error responses must carry the full ApiResponse envelope shape.
    @Test
    void errorResponse_alwaysHasEnvelopeShape() throws Exception {
        mvc.perform(get("/test/illegal-state"))
            .andExpect(jsonPath("$.status").value(false))
            .andExpect(jsonPath("$.statusCode").exists())
            .andExpect(jsonPath("$.responseCode").exists())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.errors").isArray());
    }
}
