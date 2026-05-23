package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.CompanyDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateCompanyRequestDto;

/** Read + edit the caller's company profile (US-COMP-002). */
public interface CompanyService {

    CompanyDto getCurrent();

    CompanyDto updateCurrent(UpdateCompanyRequestDto request);
}
