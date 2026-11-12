package cn.edu.achievement;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import java.time.DateTimeException;
import java.io.IOException;
import java.util.Map;

public class ApiException extends RuntimeException {
    final int status;
    public ApiException(int status,String message) { super(message); this.status=status; }
    static ApiException bad(String message) { return new ApiException(400,message); }
    static ApiException forbidden() { return new ApiException(403,"您没有操作此记录的权限"); }
    static ApiException missing() { return new ApiException(404,"记录不存在"); }
}

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ApiException.class) ResponseEntity<?> api(ApiException e) { return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage())); }
    @ExceptionHandler(AuthenticationException.class) ResponseEntity<?> auth(AuthenticationException e) { return ResponseEntity.status(401).body(Map.of("message","账号或密码错误，或账号已停用")); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(Exception e) { return ResponseEntity.status(409).body(Map.of("message","账号、指标编码或关联数据冲突，请检查后重试")); }
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> tooLarge(Exception e) { return ResponseEntity.status(413).body(Map.of("message","单个文件不能超过20MB")); }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,IllegalArgumentException.class,DateTimeException.class,MissingServletRequestPartException.class}) ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(Map.of("message","请求格式错误，请检查日期、编号和必填字段")); }
    @ExceptionHandler(IOException.class) ResponseEntity<?> io(IOException e) { org.slf4j.LoggerFactory.getLogger(ApiErrors.class).error("Private file operation failed",e);return ResponseEntity.internalServerError().body(Map.of("message","材料读写失败，请联系管理员")); }
}
