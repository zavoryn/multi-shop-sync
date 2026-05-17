package com.github.multiplatform.sync.controller;

import com.github.multiplatform.sync.common.exception.ChannelAuthException;
import com.github.multiplatform.sync.common.exception.ChannelBusinessException;
import com.github.multiplatform.sync.common.exception.ChannelException;
import com.github.multiplatform.sync.common.exception.ChannelNetworkException;
import com.github.multiplatform.sync.common.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 全局异常处理。把内部异常映射成对前端友好的 ApiResponse + HTTP 状态码。
 *
 * 映射规则：
 * - ChannelAuthException     → 401 鉴权失败（上层可触发 token 刷新）
 * - ChannelBusinessException → 422 业务参数 / 平台业务规则错误
 * - ChannelNetworkException  → 503 上游不可用（已经过 @Retryable 重试仍失败）
 * - ChannelException (其他)  → 400 渠道相关错误
 * - IllegalStateException    → 409 状态机非法转移
 * - MethodArgumentNotValidException → 400 校验失败
 * - Exception (兜底)         → 500 + 随机 traceId（便于排查）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ChannelAuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(ChannelAuthException e) {
        log.warn("渠道鉴权失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, e.getMessage()));
    }

    @ExceptionHandler(ChannelBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(ChannelBusinessException e) {
        log.warn("渠道业务错误: code={}, msg={}", e.getPlatformErrorCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(422, e.getMessage()));
    }

    @ExceptionHandler(ChannelNetworkException.class)
    public ResponseEntity<ApiResponse<Void>> handleNetwork(ChannelNetworkException e) {
        log.error("渠道网络错误（已重试）: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(503, e.getMessage()));
    }

    @ExceptionHandler(ChannelException.class)
    public ResponseEntity<ApiResponse<Void>> handleChannel(ChannelException e) {
        log.warn("渠道错误: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        log.warn("非法状态转移: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "参数校验失败: " + detail));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception e) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.error("内部错误 traceId={}", traceId, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "internal error, traceId=" + traceId));
    }
}
