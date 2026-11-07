package com.supermarket;

import com.supermarket.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, String>> authentication() {
        return ResponseEntity.status(401).body(Map.of("message", "Tên tài khoản hoặc mật khẩu không đúng, hoặc tài khoản đã bị khóa."));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> validation(HttpServletRequest request) {
        String message = request.getRequestURI().startsWith("/api/auth/login")
                ? "Thông tin đăng nhập không hợp lệ." : "Dữ liệu gửi lên không hợp lệ. Vui lòng kiểm tra lại.";
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> business(ApiException e) {
        return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage()));
    }
    // Hai giao dịch cùng sửa một bản ghi (ví dụ cùng trừ tồn kho): bên thua nhận 409 và thử lại.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Map<String, String>> conflict() {
        return ResponseEntity.status(409).body(Map.of("message", "Dữ liệu vừa được người khác cập nhật. Vui lòng thử lại."));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> integrity() {
        return ResponseEntity.status(409).body(Map.of("message", "Dữ liệu bị trùng hoặc đang được sử dụng ở nơi khác."));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge() {
        return ResponseEntity.status(413).body(Map.of("message", "Ảnh quá lớn (tối đa 5MB)."));
    }
}