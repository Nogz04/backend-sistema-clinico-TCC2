package com.clinicaia.backendtccclinicaia.service;

import com.clinicaia.backendtccclinicaia.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Falha depois de a Gemini ter sido acionada. Carrega o necessário para o painel de consumo:
 * modelo, tentativas e, se a resposta chegou (e foi cobrada) mas era inutilizável, os tokens gastos.
 */
public class GeminiFalhaException extends ApiException {

    private final String modelo;
    private final int tentativas;
    private final long duracaoMs;
    private final GeminiResposta respostaRecebida;

    /**
     * Falha de rede ou HTTP, sem resposta aproveitável.
     *
     * @param mensagem explicação exibida ao cliente
     * @param modelo último modelo chamado
     * @param tentativas chamadas realizadas
     * @param duracaoMs tempo total, incluindo esperas entre tentativas
     * @param causa erro original, se houver
     */
    public GeminiFalhaException(String mensagem, String modelo, int tentativas, long duracaoMs, Throwable causa) {
        super(HttpStatus.SERVICE_UNAVAILABLE, mensagem);
        this.modelo = modelo;
        this.tentativas = tentativas;
        this.duracaoMs = duracaoMs;
        this.respostaRecebida = null;
        initCause(causa);
    }

    /**
     * Resposta recebida, porém fora do contrato esperado; os tokens dela já foram cobrados pelo provedor.
     *
     * @param mensagem explicação exibida ao cliente
     * @param resposta resposta recebida, usada para contabilizar os tokens gastos
     * @param causa erro original, se houver
     */
    public GeminiFalhaException(String mensagem, GeminiResposta resposta, Throwable causa) {
        super(HttpStatus.SERVICE_UNAVAILABLE, mensagem);
        this.modelo = resposta.modelo();
        this.tentativas = resposta.tentativas();
        this.duracaoMs = resposta.duracaoMs();
        this.respostaRecebida = resposta;
        initCause(causa);
    }

    /**
     * Informa o último modelo chamado.
     *
     * @return nome do modelo
     */
    public String getModelo() {
        return modelo;
    }

    /**
     * Informa quantas chamadas HTTP foram feitas.
     *
     * @return número de tentativas
     */
    public int getTentativas() {
        return tentativas;
    }

    /**
     * Informa o tempo total gasto, incluindo as esperas entre tentativas.
     *
     * @return duração em milissegundos
     */
    public long getDuracaoMs() {
        return duracaoMs;
    }

    /**
     * Devolve a resposta recebida, se houver, para que o consumo seja registrado mesmo na falha.
     *
     * @return resposta cobrada pelo provedor, ou {@code null} se nada foi recebido
     */
    public GeminiResposta getRespostaRecebida() {
        return respostaRecebida;
    }
}
