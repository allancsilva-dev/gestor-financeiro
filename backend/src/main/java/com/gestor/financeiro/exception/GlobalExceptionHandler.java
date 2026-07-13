package com.gestor.financeiro.exception;

import com.gestor.financeiro.config.RequestIdFilter;
import com.gestor.financeiro.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.CannotAcquireLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> details = new HashMap<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ApiError apiError = buildError("VALIDATION_ERROR", "Dados de entrada inválidos", details, request);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiError);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ApiError apiError = buildError("NOT_FOUND", ex.getMessage(), null, request);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(apiError);
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ApiError> handleForbidden(UnauthorizedAccessException ex, HttpServletRequest request) {
        ApiError apiError = buildError("FORBIDDEN", ex.getMessage(), null, request);

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(apiError);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ApiError apiError = buildError("BUSINESS_ERROR", ex.getMessage(), null, request);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(apiError);
    }

    @ExceptionHandler(CardParcelDeprecatedException.class)
    public ResponseEntity<ApiError> handleCardParcelDeprecated(CardParcelDeprecatedException ex, HttpServletRequest request) {
        Map<String, String> details = Map.of("successor",
                "/api/v1/transacoes/" + ex.getTransacaoId() + "/cronograma");
        ApiError apiError = buildError("CARD_PARCEL_DEPRECATED", ex.getMessage(), details, request);
        return ResponseEntity.status(HttpStatus.GONE).body(apiError);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        ApiError apiError = buildError("INVALID_REQUEST", "JSON inválido ou malformado", null, request);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiError);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ApiError apiError = buildError("ACCESS_DENIED", "Acesso negado", null, request);

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(apiError);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        ApiError apiError = buildError("UNAUTHORIZED", "Não autenticado", null, request);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(apiError);
    }

    @ExceptionHandler(TokenReuseDetectedException.class)
    public ResponseEntity<ApiError> handleTokenReuse(TokenReuseDetectedException ex, HttpServletRequest request) {
        ApiError apiError = buildError("TOKEN_REUSE_DETECTED", ex.getMessage(), null, request);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(apiError);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        ApiError apiError = buildError("CONFLICT", "Registro foi alterado por outra operação. Tente novamente.", null, request);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(apiError);
    }

    @ExceptionHandler({
            FinancialConflictException.class,
            PessimisticLockingFailureException.class,
            CannotAcquireLockException.class
    })
    public ResponseEntity<ApiError> handleFinancialConflict(Exception ex, HttpServletRequest request) {
        ApiError apiError = buildError("FINANCIAL_CONFLICT", "Operação financeira concorrente. Tente novamente.", null, request);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(apiError);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiError> handleAccountLocked(AccountLockedException ex, HttpServletRequest request) {
        ApiError apiError = buildError("ACCOUNT_LOCKED", ex.getMessage(), null, request);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(apiError);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Erro interno não tratado. requestId={}", extractRequestId(request), ex);

        ApiError apiError = buildError("INTERNAL_ERROR", "Erro interno do servidor", null, request);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiError);
    }

    private ApiError buildError(String code, String message, Map<String, String> details, HttpServletRequest request) {
        return new ApiError(code, message, Instant.now(), extractRequestId(request), details);
    }

    private String extractRequestId(HttpServletRequest request) {
        Object attr = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return attr != null ? attr.toString() : null;
    }
}
