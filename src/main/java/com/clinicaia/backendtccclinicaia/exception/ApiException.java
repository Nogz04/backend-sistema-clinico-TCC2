package com.clinicaia.backendtccclinicaia.exception;

import org.springframework.http.HttpStatus;

/**
 * Erro de regra de negócio ou de acesso, convertido em resposta HTTP com o status informado.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    /**
     * Cria o erro com o status e a mensagem que serão devolvidos ao cliente.
     *
     * @param status status HTTP da resposta
     * @param message explicação exibida ao cliente
     */
    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Informa o status HTTP da resposta de erro.
     *
     * @return status HTTP que deve acompanhar a resposta de erro
     */
    public HttpStatus getStatus() {
        return status;
    }
}
