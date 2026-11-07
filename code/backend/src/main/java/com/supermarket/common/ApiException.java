package com.supermarket.common;

import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ trả về cho client dưới dạng {"message": "..."} với mã HTTP tương ứng. */
public class ApiException extends RuntimeException {
    public final HttpStatus status;
    public ApiException(HttpStatus status, String message) { super(message); this.status = status; }

    public static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    public static ApiException notFound(String message) { return new ApiException(HttpStatus.NOT_FOUND, message); }
    public static ApiException forbidden(String message) { return new ApiException(HttpStatus.FORBIDDEN, message); }
    public static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }
}