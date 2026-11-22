package com.clinicaia.backendtccclinicaia;

import com.clinicaia.backendtccclinicaia.model.Clinica;
import com.clinicaia.backendtccclinicaia.model.Medico;
import com.clinicaia.backendtccclinicaia.repository.AtualizacaoProntuarioRepository;
import com.clinicaia.backendtccclinicaia.repository.ClinicaRepository;
import com.clinicaia.backendtccclinicaia.repository.ConsultaRepository;
import com.clinicaia.backendtccclinicaia.repository.MedicoRepository;
import com.clinicaia.backendtccclinicaia.repository.PacienteRepository;
import com.clinicaia.backendtccclinicaia.repository.ProntuarioRepository;
import com.clinicaia.backendtccclinicaia.repository.UsoIaRepository;
import com.clinicaia.backendtccclinicaia.repository.UsuarioRepository;
import com.clinicaia.backendtccclinicaia.service.GeminiApiClient;
import com.clinicaia.backendtccclinicaia.service.GeminiFalhaException;
import com.clinicaia.backendtccclinicaia.service.GeminiResposta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Consumo de tokens, reaproveitamento de análises, falhas contabilizadas e painel por médico e clínica. */
@SpringBootTest
@AutoConfigureMockMvc
class Sprint6IntegrationTest {

    private static final String ANALISE = """
            {"soap":{"subjetivo":"S","objetivo":"O","avaliacao":"A","plano":"P"},
             "preDiagnosticos":["Hipótese"],"exames":["Exame"],"prescricoes":["Conduta"],
             "dadosProntuarioIdentificados":{"alergiasConhecidas":"Dipirona","medicamentosUsoContinuo":"","historicoFamiliar":""},
             "transcricaoIdentificada":"[Paciente]: tosse"}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UsoIaRepository usos;
    @Autowired ConsultaRepository consultas;
    @Autowired AtualizacaoProntuarioRepository atualizacoes;
    @Autowired ProntuarioRepository prontuarios;
    @Autowired PacienteRepository pacientes;
    @Autowired MedicoRepository medicos;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClinicaRepository clinicas;
    @Autowired PasswordEncoder encoder;
    @MockitoBean GeminiApiClient geminiApiClient;

    @BeforeEach
    @AfterEach
    void limpar() {
        usos.deleteAll(); atualizacoes.deleteAll(); consultas.deleteAll(); prontuarios.deleteAll();
        medicos.deleteAll(); pacientes.deleteAll(); usuarios.deleteAll(); clinicas.deleteAll();
    }

    @Test
    void contabilizaTokensReaproveitaTextoIgualERegistraFalhas() throws Exception {
        Clinica clinica = clinicas.save(new Clinica("Clínica Tokens"));
        criarMedico(clinica, "Dra. Ana", "ana@clinica.com", "CRM-1", "52998224725");
        criarMedico(clinica, "Dr. Beto", "beto@clinica.com", "CRM-2", "11144477735");
        String token = login("ana@clinica.com");
        long pacienteId = criarPaciente(token);
        long consultaId = criarConsulta(token, pacienteId, "Paciente relata tosse seca há cinco dias");

        when(geminiApiClient.gerarConteudoEstruturado(any()))
                .thenReturn(new GeminiResposta(ANALISE, "modelo-teste", 2, 300, 500, 200, 1000, 900));

        // 1ª solicitação: chama a IA e gasta 1000 tokens
        mockMvc.perform(post("/api/consultas/{id}/analise-ia", consultaId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uso.tokensTotal").value(1000))
                .andExpect(jsonPath("$.uso.reaproveitada").value(false))
                .andExpect(jsonPath("$.uso.modelo").value("modelo-teste"));

        // texto igual: nada é enviado de novo
        mockMvc.perform(post("/api/consultas/{id}/analise-ia", consultaId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uso.reaproveitada").value(true))
                .andExpect(jsonPath("$.uso.tokensTotal").value(0))
                .andExpect(jsonPath("$.soap.avaliacao").value("A"));
        verify(geminiApiClient, times(1)).gerarConteudoEstruturado(any());

        // o relato original permanece intacto e a versão com falantes fica separada
        mockMvc.perform(get("/api/consultas/{id}", consultaId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.transcricaoOriginal").value("Paciente relata tosse seca há cinco dias"))
                .andExpect(jsonPath("$.transcricaoIdentificada").value("[Paciente]: tosse"))
                .andExpect(jsonPath("$.modeloIa").value("modelo-teste"));

        // texto alterado: nova chamada
        mockMvc.perform(put("/api/consultas/{id}", consultaId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcricaoOriginal\":\"Paciente relata tosse e febre\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/consultas/{id}/analise-ia", consultaId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uso.reaproveitada").value(false));
        verify(geminiApiClient, times(2)).gerarConteudoEstruturado(any());

        // falha da IA: contabilizada sem tokens e sem apagar a análise anterior
        mockMvc.perform(put("/api/consultas/{id}", consultaId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcricaoOriginal\":\"Paciente relata tosse, febre e dor\"}"))
                .andExpect(status().isOk());
        when(geminiApiClient.gerarConteudoEstruturado(any()))
                .thenThrow(new GeminiFalhaException("Gemini indisponível", "modelo-reserva", 4, 5000, null));
        mockMvc.perform(post("/api/consultas/{id}/analise-ia", consultaId).header("Authorization", bearer(token)))
                .andExpect(status().isServiceUnavailable());

        // painel: Ana gastou 2000 tokens (2 análises), Beto nada; a clínica soma o mesmo
        mockMvc.perform(get("/api/ia/uso").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuracao.modeloPrincipal").value("modelo-principal-teste"))
                .andExpect(jsonPath("$.configuracao.modeloReserva").value("modelo-reserva-teste"))
                .andExpect(jsonPath("$.meu.total.tokensTotal").value(2000))
                .andExpect(jsonPath("$.meu.total.tokensEntrada").value(600))
                .andExpect(jsonPath("$.meu.total.solicitacoes").value(4))
                .andExpect(jsonPath("$.meu.total.reaproveitadas").value(1))
                .andExpect(jsonPath("$.meu.total.falhas").value(1))
                .andExpect(jsonPath("$.meu.hoje.tokensTotal").value(2000))
                .andExpect(jsonPath("$.clinica.total.tokensTotal").value(2000))
                .andExpect(jsonPath("$.medicos.length()").value(2))
                .andExpect(jsonPath("$.medicos[0].nome").value("Dr. Beto"))
                .andExpect(jsonPath("$.medicos[0].tokensTotal").value(0))
                .andExpect(jsonPath("$.medicos[1].nome").value("Dra. Ana"))
                .andExpect(jsonPath("$.medicos[1].tokensTotal").value(2000))
                .andExpect(jsonPath("$.medicos[1].voce").value(true))
                .andExpect(jsonPath("$.ultimosDias.length()").value(14))
                .andExpect(jsonPath("$.ultimosDias[13].meusTokens").value(2000));

        // o outro médico enxerga o total da clínica, mas o seu consumo pessoal é zero
        String tokenBeto = login("beto@clinica.com");
        mockMvc.perform(get("/api/ia/uso").header("Authorization", bearer(tokenBeto)))
                .andExpect(jsonPath("$.meu.total.tokensTotal").value(0))
                .andExpect(jsonPath("$.clinica.total.tokensTotal").value(2000));
    }

    @Test
    void rascunhoAnalisadoDevolveOsDadosDoProntuarioIdentificadosPelaIa() throws Exception {
        Clinica clinica = clinicas.save(new Clinica("Clínica Painel"));
        criarMedico(clinica, "Dra. Ana", "ana@clinica.com", "CRM-1", "52998224725");
        String token = login("ana@clinica.com");
        long consultaId = criarConsulta(token, criarPaciente(token), "Paciente alérgico a dipirona");
        when(geminiApiClient.gerarConteudoEstruturado(any()))
                .thenReturn(new GeminiResposta(ANALISE, "modelo-teste", 1, 100, 100, 0, 200, 300));

        // antes da análise não há nada identificado
        mockMvc.perform(get("/api/consultas/{id}", consultaId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.dadosProntuarioIdentificados").value(nullValue()));

        // depois da análise, reabrir o rascunho devolve o que a IA identificou
        mockMvc.perform(post("/api/consultas/{id}/analise-ia", consultaId).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/consultas/{id}", consultaId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dadosProntuarioIdentificados.alergiasConhecidas").value("Dipirona"));

        // consulta finalizada expõe somente o que o médico confirmou
        mockMvc.perform(post("/api/consultas/{id}/finalizar", consultaId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soap\":{\"subjetivo\":\"S\",\"objetivo\":\"O\",\"avaliacao\":\"A\",\"plano\":\"P\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dadosProntuarioIdentificados").value(nullValue()));
    }

    @Test
    void rejeitaTranscricaoAcimaDoLimiteSemChamarAIa() throws Exception {
        Clinica clinica = clinicas.save(new Clinica("Clínica Limite"));
        criarMedico(clinica, "Dra. Ana", "ana@clinica.com", "CRM-1", "52998224725");
        String token = login("ana@clinica.com");
        long consultaId = criarConsulta(token, criarPaciente(token), "x".repeat(2500));

        mockMvc.perform(post("/api/consultas/{id}/analise-ia", consultaId).header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        verify(geminiApiClient, times(0)).gerarConteudoEstruturado(any());
    }

    @Test
    void clinicasDiferentesNaoVeemOConsumoUmaDaOutra() throws Exception {
        Clinica a = clinicas.save(new Clinica("Clínica A"));
        Clinica b = clinicas.save(new Clinica("Clínica B"));
        criarMedico(a, "Dra. Ana", "ana@clinica.com", "CRM-1", "52998224725");
        criarMedico(b, "Dr. Caio", "caio@clinica.com", "CRM-3", "11144477735");
        String token = login("ana@clinica.com");
        long consultaId = criarConsulta(token, criarPaciente(token), "Paciente com cefaleia");
        when(geminiApiClient.gerarConteudoEstruturado(any()))
                .thenReturn(new GeminiResposta(ANALISE, "modelo-teste", 1, 100, 100, 0, 200, 300));
        mockMvc.perform(post("/api/consultas/{id}/analise-ia", consultaId).header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/ia/uso").header("Authorization", bearer(login("caio@clinica.com"))))
                .andExpect(jsonPath("$.clinica.total.tokensTotal").value(0))
                .andExpect(jsonPath("$.medicos.length()").value(1));
    }

    private void criarMedico(Clinica clinica, String nome, String email, String crm, String cpf) {
        Medico medico = new Medico();
        medico.setNomeCompleto(nome);
        medico.setEmail(email);
        medico.setSenha(encoder.encode("senha123"));
        medico.setCrm(crm);
        medico.setCpf(cpf);
        medico.setDataNascimento(LocalDate.of(1980, 1, 15));
        medico.setClinica(clinica);
        medico.setEspecialidades(new LinkedHashSet<>(Set.of("Clínica Geral")));
        medicos.saveAndFlush(medico);
    }

    private long criarPaciente(String token) throws Exception {
        String corpo = mockMvc.perform(post("/api/pacientes").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeCompleto\":\"Paciente Teste\",\"cpf\":\"" + cpfUnico() + "\",\"dataNascimento\":\"1990-05-20\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("id").asLong();
    }

    private static int sequencia = 0;

    private static String cpfUnico() {
        String[] validos = {"52998224725", "11144477735", "39053344705", "86288366757"};
        return validos[sequencia++ % validos.length];
    }

    private long criarConsulta(String token, long pacienteId, String transcricao) throws Exception {
        String corpo = mockMvc.perform(post("/api/consultas").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d,\"transcricaoOriginal\":\"%s\"}".formatted(pacienteId, transcricao)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("id").asLong();
    }

    private String login(String email) throws Exception {
        String corpo = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"senha123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("token").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
