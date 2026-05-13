package com.orbix.engine.modules.common.service;

import com.orbix.engine.modules.common.domain.dto.ApiResponseDto;
import com.orbix.engine.modules.common.domain.enums.ResponseCode;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Wraps every {@code /api/v1/**} response in {@link ApiResponseDto} unless the
 * controller already returns one. Errors are wrapped separately by
 * {@link GlobalExceptionHandler}.
 */
@RestControllerAdvice
public class ApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> parameterType = returnType.getParameterType();
        return !ApiResponseDto.class.isAssignableFrom(parameterType);
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof ApiResponseDto<?>) {
            return body;
        }
        String path = request instanceof ServletServerHttpRequest sreq
            ? sreq.getServletRequest().getRequestURI()
            : "";
        if (!path.startsWith("/api/v1")) {
            return body;
        }
        int status = response instanceof ServletServerHttpResponse sresp
            ? sresp.getServletResponse().getStatus()
            : 200;
        String code = status == 201 ? ResponseCode.CREATED : ResponseCode.SUCCESS;
        String message = status == 201 ? "Created" : "OK";
        return new ApiResponseDto<>(true, status, code, message, java.util.List.of(), body);
    }
}
