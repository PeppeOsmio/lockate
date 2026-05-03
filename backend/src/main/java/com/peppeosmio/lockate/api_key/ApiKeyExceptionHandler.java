package com.peppeosmio.lockate.api_key;

import com.peppeosmio.lockate.anonymous_group.exceptions.Base64Exception;
import com.peppeosmio.lockate.common.dto.ErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ApiKeyExceptionHandler {
    @ExceptionHandler(Base64Exception.class)
    public ResponseEntity<ErrorResponseDto> handleBase64(
            Base64Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponseDto("invalid_base64"));
    }
}
