package com.clinicaia.backendtccclinicaia;

import com.clinicaia.backendtccclinicaia.exception.ApiException;
import com.clinicaia.backendtccclinicaia.service.GeminiApiClient;
import com.clinicaia.backendtccclinicaia.service.GeminiFalhaException;
import com.clinicaia.backendtccclinicaia.service.GeminiResposta;
import com.clinicaia.backendtccclinicaia.service.GeminiService;
import com.clinicaia.backendtccclinicaia.service.ResultadoAnaliseIa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    @Mock GeminiApiClient apiClient;
    GeminiService service;

    @BeforeEach
    void setup() {
        service = new GeminiService(apiClient, new ObjectMapper(), true);
    }

    private GeminiResposta resposta(String texto) {
        return new GeminiResposta(texto, "modelo-teste", 1, 300, 500, 0, 800, 1200);
    }

    @Test
    void converteRespostaEstruturadaValida() {
        when(apiClient.gerarConteudoEstruturado(any())).thenReturn(resposta("""
                {
                  "soap": {"subjetivo":"S","objetivo":"O","avaliacao":"A","plano":"P"},
                  "preDiagnosticos":["Hipótese"],
                  "exames":["Exame"],
                  "prescricoes":[],
                  "dadosProntuarioIdentificados": {
                    "alergiasConhecidas":"Não informado",
                    "medicamentosUsoContinuo":"Não informado",
                    "historicoFamiliar":"Não informado"
                  },
                  "transcricaoIdentificada": "[Apenas o Médico falando sobre a consulta]\\nPaciente relata cefaleia há dois dias"
                }
                """));

        ResultadoAnaliseIa resultado = service.analisar("transcrição");

        assertThat(resultado.analise().soap().avaliacao()).isEqualTo("A");
        assertThat(resultado.analise().preDiagnosticos()).containsExactly("Hipótese");
        assertThat(resultado.analise().exames()).containsExactly("Exame");
        assertThat(resultado.resposta().tokensTotal()).isEqualTo(800);
    }

    @Test
    void rejeitaJsonQueNaoCumpreContratoSoapPreservandoOsTokensJaCobrados() {
        when(apiClient.gerarConteudoEstruturado(any())).thenReturn(resposta("""
                {"soap":{"subjetivo":"S"},"preDiagnosticos":[],"exames":[],"prescricoes":[]}
                """));

        assertThatThrownBy(() -> service.analisar("transcrição"))
                .isInstanceOf(GeminiFalhaException.class)
                .satisfies(erro -> {
                    GeminiFalhaException falha = (GeminiFalhaException) erro;
                    assertThat(falha.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(falha.getRespostaRecebida().tokensTotal()).isEqualTo(800);
                });
    }

    @Test
    void promptOrientaAIaANaoReproduzirDadosDeIdentificacao() {
        when(apiClient.gerarConteudoEstruturado(any())).thenReturn(resposta("""
                {
                  "soap": {"subjetivo":"S","objetivo":"O","avaliacao":"A","plano":"P"},
                  "preDiagnosticos":[], "exames":[], "prescricoes":[],
                  "dadosProntuarioIdentificados": {
                    "alergiasConhecidas":"Não informado",
                    "medicamentosUsoContinuo":"Não informado",
                    "historicoFamiliar":"Não informado"
                  },
                  "transcricaoIdentificada": "[Paciente]: sinto dor"
                }
                """));

        service.analisar("Paciente João, CPF 123.456.789-09, relata dor de cabeça");

        ArgumentCaptor<ObjectNode> enviado = ArgumentCaptor.forClass(ObjectNode.class);
        verify(apiClient).gerarConteudoEstruturado(enviado.capture());
        String prompt = enviado.getValue().path("contents").path(0).path("parts").path(0).path("text").asText();

        assertThat(prompt).contains("não reproduza dados que identifiquem o paciente ou terceiros");
        assertThat(prompt).contains("[nome], [CPF], [telefone], [e-mail] ou [endereço]");
        assertThat(prompt).contains("relata dor de cabeça");
    }

    @Test
    void contratoPedeSomenteDadosRelatadosPeloPacienteEExcluiOsDeJulgamentoClinico() {
        when(apiClient.gerarConteudoEstruturado(any())).thenReturn(resposta("""
                {
                  "soap": {"subjetivo":"S","objetivo":"O","avaliacao":"A","plano":"P"},
                  "preDiagnosticos":[], "exames":[], "prescricoes":[],
                  "dadosProntuarioIdentificados": {
                    "alergiasConhecidas":"Penicilina",
                    "antecedentesPessoais":"Pneumonia em 2021",
                    "cirurgiasInternacoes":"Joelho direito (2018)",
                    "imunizacoes":"Gripe e covid",
                    "habitosAspectosSociais":"Tabagismo"
                  },
                  "transcricaoIdentificada": "[Paciente]: sou alérgico"
                }
                """));

        ResultadoAnaliseIa resultado = service.analisar("Sou alérgico a penicilina e operei a vesícula em 2019");

        // a resposta da IA com os campos novos chega íntegra até a tela
        assertThat(resultado.analise().dadosProntuarioIdentificados().antecedentesPessoais()).isEqualTo("Pneumonia em 2021");
        assertThat(resultado.analise().dadosProntuarioIdentificados().cirurgiasInternacoes()).isEqualTo("Joelho direito (2018)");
        assertThat(resultado.analise().dadosProntuarioIdentificados().imunizacoes()).isEqualTo("Gripe e covid");
        assertThat(resultado.analise().dadosProntuarioIdentificados().habitosAspectosSociais()).isEqualTo("Tabagismo");

        ArgumentCaptor<ObjectNode> enviado = ArgumentCaptor.forClass(ObjectNode.class);
        verify(apiClient).gerarConteudoEstruturado(enviado.capture());
        JsonNode campos = enviado.getValue().path("generationConfig").path("responseJsonSchema")
                .path("properties").path("dadosProntuarioIdentificados").path("properties");

        for (String relatado : new String[]{"alergiasConhecidas", "medicamentosUsoContinuo", "historicoFamiliar",
                "antecedentesPessoais", "cirurgiasInternacoes", "imunizacoes", "habitosAspectosSociais"}) {
            assertThat(campos.has(relatado)).as("campo relatado pelo paciente: " + relatado).isTrue();
        }
        for (String julgamento : new String[]{"resumoClinico", "condicoesAtivas", "diagnosticosConfirmados",
                "examesRelevantes", "tratamentosProcedimentos", "planoTerapeuticoAtual", "acompanhamentoPendencias"}) {
            assertThat(campos.has(julgamento)).as("campo de julgamento clínico: " + julgamento).isFalse();
        }
    }

    @Test
    void propagaIndisponibilidadeDoClienteSemConverterEmRegistro() {
        ApiException timeout = new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Tempo limite da Gemini excedido");
        when(apiClient.gerarConteudoEstruturado(any())).thenThrow(timeout);

        assertThatThrownBy(() -> service.analisar("transcrição"))
                .isSameAs(timeout);
    }
}
