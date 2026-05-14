package com.orbix.engine.modules.iam.service;

import com.orbix.engine.modules.iam.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchAccessGuardTest {

    @Mock private UserRoleRepository userRoles;

    @InjectMocks private BranchAccessGuard guard;

    @Test
    void allowsWhenAGrantCoversTheBranch() {
        when(userRoles.hasBranchAccess(1L, 2L, 3L)).thenReturn(true);

        assertThatNoException().isThrownBy(() -> guard.verify(1L, 2L, 3L));
    }

    @Test
    void deniesWhenNoGrantCoversTheBranch() {
        when(userRoles.hasBranchAccess(1L, 2L, 3L)).thenReturn(false);

        assertThatThrownBy(() -> guard.verify(1L, 2L, 3L))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("branch 3");
    }

    @Test
    void deniesWhenContextIsIncomplete() {
        assertThatThrownBy(() -> guard.verify(null, 2L, 3L))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guard.verify(1L, null, 3L))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guard.verify(1L, 2L, null))
            .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(userRoles);
    }
}
