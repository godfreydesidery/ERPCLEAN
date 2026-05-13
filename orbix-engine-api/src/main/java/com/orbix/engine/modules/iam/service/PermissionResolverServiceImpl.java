package com.orbix.engine.modules.iam.service;

import com.orbix.engine.modules.iam.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionResolverServiceImpl implements PermissionResolverService {

    private final UserRoleRepository userRoles;

    @Override
    @Transactional(readOnly = true)
    public Set<String> resolve(Long userId, Long companyId, Long branchId) {
        if (userId == null || companyId == null) return Set.of();
        return userRoles.findActivePermissionCodes(userId, companyId, branchId);
    }
}
