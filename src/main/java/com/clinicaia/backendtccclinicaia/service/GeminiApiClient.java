package com.clinicaia.backendtccclinicaia.service;

import com.clinicaia.backendtccclinicaia.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/**
 * Cliente HTTP da API Gemini.
 *
 * <p>O provedor responde às vezes com 503 (sobrecarga) ou 429 (limite de taxa). Por isso cada chamada é
 * tentada mais de uma vez, com espera crescente, e a segunda metade das tentativas usa o modelo reserva.
 * Erros definitivos (chave inválida, requisição malformada) não são repetidos.</p>
 */
@Component
public class GeminiApiClient {

    private static final Logger LOG = LoggerFactory.getLogger(GeminiApiClient.class);
    /** Status HTTP de falha passageira, para os quais vale tentar de novo. */
    private static final Set<Integer> TRANSITORIOS = Set.of(408, 429, 500, 502, 503, 504);

    private final String apiKey;
    private final String modeloPrincipal;
    private final String modeloReserva;
    private final Duration timeoutPorTentativa;
    private final int maxTentativas;
    private final Duration esperaInicial;
    private final int maxTokensSaida;
    private final int orcamentoRaciocinio;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * Cria o cliente com as propriedades {@code gemini.*}.
     *
     * @param apiKey chave da API, enviada no cabeçalho {@code x-goog-api-key}; vazia desativa a integração
     * @param model modelo principal
     * @param fallbackModel modelo das últimas tentativas; vazio mantém o principal
     * @param connectTimeout tempo limite para abrir a conexão
     * @param requestTimeout tempo limite de cada tentativa
     * @param maxAttempts total de tentativas, somando principal e reserva
     * @param retryDelay espera antes da 2ª tentativa; dobra a cada falha
     * @param maxOutputTokens teto de tokens de saída (0 desativa)
     * @param thinkingBudget raciocínio interno do modelo: 0 desliga, negativo não envia o parâmetro
     * @param baseUrl endereço base da API, terminado em barra
     * @param objectMapper serializador JSON
     */
    public GeminiApiClient(
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.model}") String model,
            @Value("${gemini.fallback-model:}") String fallbackModel,
            @Value("${gemini.connect-timeout}") Duration connectTimeout,
            @Value("${gemini.request-timeout}") Duration requestTimeout,
            @Value("${gemini.max-attempts:4}") int maxAttempts,
            @Value("${gemini.retry-delay:PT1S}") Duration retryDelay,
            @Value("${gemini.max-output-tokens:8192}") int maxOutputTokens,
            @Value("${gemini.thinking-budget:0}") int thinkingBudget,
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta/models/}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.modeloPrincipal = model;
        this.modeloReserva = fallbackModel.isBlank() ? model : fallbackModel.trim();
        this.timeoutPorTentativa = requestTimeout;
        this.maxTentativas = Math.max(1, maxAttempts);
        this.esperaInicial = retryDelay;
        this.maxTokensSaida = maxOutputTokens;
        this.orcamentoRaciocinio = thinkingBudget;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    /**
     * Envia a requisição à Gemini, repetindo e trocando para o modelo reserva em falhas passageiras.
     *
     * @param payload corpo compatível com {@code generateContent}
     * @return texto JSON do modelo e tokens contabilizados pelo provedor
     * @throws ApiException com HTTP 503 quando a chave não está configurada
     * @throws GeminiFalhaException quando as tentativas se esgotam ou a resposta é inutilizável
     */
    public GeminiResposta gerarConteudoEstruturado(ObjectNode payload) {
        if (apiKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Integração Gemini não configurada; defina GEMINI_API_KEY ou finalize a consulta manualmente"
            );
        }
        String corpo = objectMapper.writeValueAsString(comLimites(payload));
        long inicio = System.nanoTime();
        String motivo = "sem resposta";
        String modelo = modeloPrincipal;
        Throwable causa = null;

        for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {
            modelo = modeloDaTentativa(tentativa);
            try {
                HttpResponse<String> http = enviar(modelo, corpo);
                int status = http.statusCode();
                if (status / 100 == 2) {
                    GeminiResposta resposta = interpretar(http.body(), modelo, tentativa, inicio);
                    if (resposta != null) {
                        return resposta;
                    }
                    motivo = "resposta vazia";
                } else if (TRANSITORIOS.contains(status)) {
                    motivo = "HTTP " + status;
                } else {
                    throw new GeminiFalhaException(
                            "Gemini respondeu com HTTP " + status, modelo, tentativa, duracaoMs(inicio), null
                    );
                }
            } catch (IOException erroDeRede) {
                motivo = "falha de rede ou tempo limite";
                causa = erroDeRede;
            } catch (InterruptedException interrompida) {
                Thread.currentThread().interrupt();
                throw new GeminiFalhaException(
                        "A análise da Gemini foi interrompida", modelo, tentativa, duracaoMs(inicio), interrompida
                );
            }
            LOG.warn("Gemini: tentativa {}/{} no modelo {} falhou ({})", tentativa, maxTentativas, modelo, motivo);
            if (tentativa < maxTentativas) {
                esperar(tentativa);
            }
        }
        throw new GeminiFalhaException(
                "A Gemini está sobrecarregada ou indisponível (" + motivo
                        + "). Tente novamente em instantes ou finalize a consulta manualmente",
                modelo, maxTentativas, duracaoMs(inicio), causa
        );
    }

    /**
     * Escolhe o modelo da tentativa: a primeira metade das tentativas usa o modelo principal e a segunda metade
     * usa o modelo reserva. Sem modelo reserva configurado, o principal é usado em todas.
     *
     * @param tentativa número da tentativa, a partir de 1
     * @return nome do modelo a chamar
     */
    private String modeloDaTentativa(int tentativa) {
        return tentativa <= (maxTentativas + 1) / 2 ? modeloPrincipal : modeloReserva;
    }

    /**
     * Envia a requisição {@code generateContent} ao modelo indicado, com o tempo limite de uma tentativa.
     *
     * @param modelo modelo da Gemini a chamar
     * @param corpo JSON da requisição
     * @return resposta HTTP, de qualquer status
     * @throws IOException em falha de rede ou tempo limite esgotado
     * @throws InterruptedException se a thread for interrompida durante a chamada
     */
    private HttpResponse<String> enviar(String modelo, String corpo) throws IOException, InterruptedException {
        HttpRequest requisicao = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + modelo + ":generateContent"))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .timeout(timeoutPorTentativa)
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build();
        return httpClient.send(requisicao, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Lê a resposta HTTP 2xx, extrai o texto visível e contabiliza os tokens informados pelo provedor.
     *
     * @param corpo corpo JSON da resposta
     * @param modelo modelo que respondeu
     * @param tentativa número da tentativa, a partir de 1
     * @param inicio instante inicial da chamada, de {@code System.nanoTime()}
     * @return a resposta, ou {@code null} se o texto veio vazio (vale tentar de novo)
     * @throws GeminiFalhaException se o corpo for ilegível ou a resposta tiver sido cortada por limite de tokens
     */
    private GeminiResposta interpretar(String corpo, String modelo, int tentativa, long inicio) {
        JsonNode raiz;
        try {
            raiz = objectMapper.readTree(corpo);
        } catch (RuntimeException erro) {
            throw new GeminiFalhaException(
                    "A Gemini devolveu um corpo ilegível", modelo, tentativa, duracaoMs(inicio), erro
            );
        }
        JsonNode candidato = raiz.path("candidates").path(0);
        JsonNode uso = raiz.path("usageMetadata");
        long entrada = uso.path("promptTokenCount").asLong(0);
        long saida = uso.path("candidatesTokenCount").asLong(0);
        long raciocinio = uso.path("thoughtsTokenCount").asLong(0);
        String texto = primeiroTextoVisivel(candidato);
        GeminiResposta resposta = new GeminiResposta(texto, modelo, tentativa, entrada, saida, raciocinio,
                uso.path("totalTokenCount").asLong(entrada + saida + raciocinio), duracaoMs(inicio));

        if ("MAX_TOKENS".equals(candidato.path("finishReason").asText())) {
            throw new GeminiFalhaException(
                    "A resposta da Gemini foi cortada ao atingir o limite de tokens de saída", resposta, null
            );
        }
        return texto == null || texto.isBlank() ? null : resposta;
    }

    /**
     * Ignora os trechos de raciocínio interno ({@code thought}) e devolve só o texto da resposta.
     *
     * @param candidato primeiro candidato da resposta
     * @return primeiro trecho de texto que não seja raciocínio interno, ou {@code null} se não houver
     */
    private String primeiroTextoVisivel(JsonNode candidato) {
        for (JsonNode parte : candidato.path("content").path("parts")) {
            if (!parte.path("thought").asBoolean(false) && parte.path("text").isTextual()) {
                return parte.path("text").asText();
            }
        }
        return null;
    }

    /**
     * Copia o payload aplicando o teto de saída e o orçamento de raciocínio, que controlam o custo da chamada.
     *
     * @param payload corpo original, que não é alterado
     * @return cópia com {@code maxOutputTokens} e {@code thinkingBudget}
     */
    private ObjectNode comLimites(ObjectNode payload) {
        ObjectNode copia = payload.deepCopy();
        ObjectNode geracao = copia.has("generationConfig")
                ? (ObjectNode) copia.get("generationConfig")
                : copia.putObject("generationConfig");
        if (maxTokensSaida > 0) {
            geracao.put("maxOutputTokens", maxTokensSaida);
        }
        if (orcamentoRaciocinio >= 0) {
            geracao.putObject("thinkingConfig").put("thinkingBudget", orcamentoRaciocinio);
        }
        return copia;
    }

    /**
     * Aguarda antes da próxima tentativa, dobrando a espera a cada falha (limite de 16 vezes a espera inicial).
     *
     * @param tentativaQueFalhou número da tentativa que acabou de falhar, a partir de 1
     */
    private void esperar(int tentativaQueFalhou) {
        try {
            Thread.sleep(esperaInicial.toMillis() * (1L << Math.min(tentativaQueFalhou - 1, 4)));
        } catch (InterruptedException interrompida) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Calcula o tempo decorrido desde o início da chamada.
     *
     * @param inicioNanos instante inicial, obtido de {@code System.nanoTime()}
     * @return tempo decorrido em milissegundos
     */
    private static long duracaoMs(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }
}
