package com.clinicaia.backendtccclinicaia.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converte exceções em respostas {@link ApiErrorResponse}, sem expor detalhes internos ao cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Erros de negócio: usa o status e a mensagem da própria exceção.
     */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApi(ApiException exception, HttpServletRequest request) {
        return response(exception.getStatus(), exception.getMessage(), request, Map.of());
    }

    /**
     * Falha de validação do corpo da requisição: HTTP 400 com a primeira mensagem de cada campo inválido.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "Dados de entrada inválidos", request, fields);
    }

    /**
     * Corpo ausente ou com JSON malformado: HTTP 400.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido", request, Map.of());
    }

    /**
     * Violação de unicidade ou de relacionamento no banco: HTTP 409.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "O registro viola uma restrição de unicidade ou relacionamento", request, Map.of());
    }

    /**
     * Qualquer outra falha: HTTP 500 com mensagem genérica, preservando o sigilo dos detalhes internos.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado", request, Map.of());
    }

    /**
     * Monta a resposta de erro padronizada.
     */
    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> fields
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI(), fields
        );
        return ResponseEntity.status(status).body(body);
    }
}
