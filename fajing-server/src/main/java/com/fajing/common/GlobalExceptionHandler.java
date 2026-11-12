package com.fajing.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getMessage());
    }

    /** 需要真实 HTTP 状态码（如越权返回 404）时用 ResponseStatusException */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleStatus(ResponseStatusException e) {
        String msg = e.getReason() == null ? "请求失败" : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(Result.fail(msg));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValid(Exception e) {
        String msg = "参数不合法";
        if (e instanceof MethodArgumentNotValidException ex && ex.getBindingResult().getFieldError() != null) {
            msg = ex.getBindingResult().getFieldError().getDefaultMessage();
        }
        return Result.fail(msg);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadSize() {
        return Result.fail("文件超过 10MB 限制");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("服务器异常", e);
        return Result.fail("服务器开小差了，请稍后重试");
    }
}
