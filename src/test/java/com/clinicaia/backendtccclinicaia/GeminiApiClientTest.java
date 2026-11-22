package com.clinicaia.backendtccclinicaia;

import com.clinicaia.backendtccclinicaia.service.GeminiApiClient;
import com.clinicaia.backendtccclinicaia.service.GeminiFalhaException;
import com.clinicaia.backendtccclinicaia.service.GeminiResposta;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testa repetição, troca de modelo e leitura de tokens contra um servidor HTTP local que imita a Gemini. */
class GeminiApiClientTest {

    private static final String OK = """
            {"candidates":[{"content":{"parts":[{"text":"{\\"ok\\":true}"}]},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":300,"candidatesTokenCount":500,"thoughtsTokenCount":40,"totalTokenCount":840}}
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer servidor;
    private final List<Integer> respostas = new ArrayList<>();
    private final List<String> caminhos = new CopyOnWriteArrayList<>();
    private final List<String> corpos = new CopyOnWriteArrayList<>();
    private String bodyDaUltima = OK;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1beta/models/", troca -> {
            caminhos.add(troca.getRequestURI().getPath());
            corpos.add(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int indice = caminhos.size() - 1;
            int status = indice < respostas.size() ? respostas.get(indice) : 200;
            byte[] corpo = (status == 200 ? bodyDaUltima : "{\"error\":{}}").getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, corpo.length);
            troca.getResponseBody().write(corpo);
            troca.close();
        });
        servidor.start();
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private GeminiApiClient cliente(String chave) {
        return new GeminiApiClient(chave, "principal", "reserva", Duration.ofSeconds(1), Duration.ofSeconds(3),
                4, Duration.ZERO, 8192, 0,
                "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1beta/models/", mapper);
    }

    private ObjectNode payload() {
        ObjectNode raiz = mapper.createObjectNode();
        raiz.putObject("generationConfig").put("temperature", 0.1);
        return raiz;
    }

    @Test
    void repeteNoPrincipalDepoisUsaOReservaELeOsTokens() {
        respostas.addAll(List.of(503, 503, 503));

        GeminiResposta resposta = cliente("chave").gerarConteudoEstruturado(payload());

        assertThat(caminhos).containsExactly(
                "/v1beta/models/principal:generateContent", "/v1beta/models/principal:generateContent",
                "/v1beta/models/reserva:generateContent", "/v1beta/models/reserva:generateContent");
        assertThat(resposta.modelo()).isEqualTo("reserva");
        assertThat(resposta.tentativas()).isEqualTo(4);
        assertThat(resposta.tokensEntrada()).isEqualTo(300);
        assertThat(resposta.tokensSaida()).isEqualTo(500);
        assertThat(resposta.tokensRaciocinio()).isEqualTo(40);
        assertThat(resposta.tokensTotal()).isEqualTo(840);
        assertThat(resposta.texto()).contains("ok");
    }

    @Test
    void enviaTetoDeSaidaEOrcamentoDeRaciocinio() {
        cliente("chave").gerarConteudoEstruturado(payload());

        assertThat(corpos.get(0)).contains("\"maxOutputTokens\":8192").contains("\"thinkingBudget\":0").contains("\"temperature\":0.1");
    }

    @Test
    void naoRepeteErroDefinitivo() {
        respostas.add(400);

        assertThatThrownBy(() -> cliente("chave").gerarConteudoEstruturado(payload()))
                .isInstanceOf(GeminiFalhaException.class)
                .hasMessageContaining("HTTP 400");
        assertThat(caminhos).hasSize(1);
    }

    @Test
    void esgotaAsTentativasEInformaOTotal() {
        respostas.addAll(List.of(503, 503, 503, 503));

        assertThatThrownBy(() -> cliente("chave").gerarConteudoEstruturado(payload()))
                .isInstanceOfSatisfying(GeminiFalhaException.class, falha -> {
                    assertThat(falha.getTentativas()).isEqualTo(4);
                    assertThat(falha.getRespostaRecebida()).isNull();
                });
        assertThat(caminhos).hasSize(4);
    }

    @Test
    void respostaCortadaPorLimiteDeTokensPreservaOGastoCobrado() {
        bodyDaUltima = """
                {"candidates":[{"content":{"parts":[{"text":"{\\"a\\":"}]},"finishReason":"MAX_TOKENS"}],
                 "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":8192,"totalTokenCount":8202}}
                """;

        assertThatThrownBy(() -> cliente("chave").gerarConteudoEstruturado(payload()))
                .isInstanceOfSatisfying(GeminiFalhaException.class, falha ->
                        assertThat(falha.getRespostaRecebida().tokensTotal()).isEqualTo(8202));
        assertThat(caminhos).hasSize(1);
    }

    @Test
    void semChaveNaoChamaAApi() {
        assertThatThrownBy(() -> cliente("").gerarConteudoEstruturado(payload()))
                .hasMessageContaining("não configurada");
        assertThat(caminhos).isEmpty();
    }
}
