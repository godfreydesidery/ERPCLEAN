package com.orbix.engine.modules.common.repository;

import com.orbix.engine.modules.common.domain.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
