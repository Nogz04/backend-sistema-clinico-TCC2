package com.clinicaia.backendtccclinicaia;

import com.clinicaia.backendtccclinicaia.model.Clinica;
import com.clinicaia.backendtccclinicaia.model.StatusConsulta;
import com.clinicaia.backendtccclinicaia.model.Medico;
import com.clinicaia.backendtccclinicaia.repository.ClinicaRepository;
import com.clinicaia.backendtccclinicaia.repository.AtualizacaoProntuarioRepository;
import com.clinicaia.backendtccclinicaia.repository.ConsultaRepository;
import com.clinicaia.backendtccclinicaia.repository.MedicoRepository;
import com.clinicaia.backendtccclinicaia.repository.PacienteRepository;
import com.clinicaia.backendtccclinicaia.repository.ProntuarioRepository;
import com.clinicaia.backendtccclinicaia.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Sprint3IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ConsultaRepository consultaRepository;
    @Autowired AtualizacaoProntuarioRepository atualizacaoProntuarioRepository;
    @Autowired ProntuarioRepository prontuarioRepository;
    @Autowired PacienteRepository pacienteRepository;
    @Autowired MedicoRepository medicoRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ClinicaRepository clinicaRepository;

    @BeforeEach
    void clean() {
        atualizacaoProntuarioRepository.deleteAll();
        consultaRepository.deleteAll();
        prontuarioRepository.deleteAll();
        medicoRepository.deleteAll();
        pacienteRepository.deleteAll();
        usuarioRepository.deleteAll();
        clinicaRepository.deleteAll();
    }

    @Test
    void loginEProtecaoDeRotasComMedicoPreviamenteProvisionado() throws Exception {
        criarMedicoInicialNoBanco();

        mockMvc.perform(get("/api/pacientes"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"medico@clinica.com","senha":"senha-errada"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/medicos/primeiro-acesso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(medicoJson("outro@clinica.com", "22233344405", "CRM-2")))
                .andExpect(status().isUnauthorized());

        String token = login("medico@clinica.com", "senha123");
        mockMvc.perform(get("/api/medicos").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("medico@clinica.com"));
    }

    @Test
    void prontuarioEhUnicoEFinalizacaoAtualizaHistoricoSemDependerDaIa() throws Exception {
        criarMedicoInicialNoBanco();
        String token = login("medico@clinica.com", "senha123");

        long pacienteId = id(mockMvc.perform(post("/api/pacientes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nomeCompleto":"Paciente Teste",
                                  "cpf":"52998224725",
                                  "dataNascimento":"1990-05-20",
                                  "telefone":"55999999999",
                                  "email":"paciente@example.com",
                                  "prontuarioInicial":{"alergiasConhecidas":"Dipirona"}
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(prontuarioRepository.count()).isEqualTo(1);
        long consultaId = id(mockMvc.perform(post("/api/consultas")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"transcricaoOriginal":"Paciente relata cefaleia há dois dias"}
                                """.formatted(pacienteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/consultas/{id}/finalizar", consultaId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "soap": {
                                    "subjetivo":"Cefaleia relatada há dois dias",
                                    "objetivo":"Paciente orientado, sem sinais de alarme relatados",
                                    "avaliacao":"Cefaleia a esclarecer",
                                    "plano":"Orientações e avaliação clínica conforme evolução"
                                  },
                                  "dadosGeraisConsulta":"Registro revisado pelo médico",
                                  "consolidacaoProntuario": {
                                    "alergiasConhecidas":"Dipirona",
                                    "medicamentosUsoContinuo":"Nenhum",
                                    "historicoFamiliar":"Hipertensão",
                                    "resumoClinico":"Paciente em investigação de cefaleia",
                                    "condicoesAtivas":"Cefaleia",
                                    "diagnosticosConfirmados":"Nenhum diagnóstico confirmado",
                                    "planoTerapeuticoAtual":"Acompanhamento clínico"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZADA"));

        mockMvc.perform(put("/api/consultas/{id}", consultaId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/pacientes/{id}/prontuario", pacienteId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alergiasConhecidas").value("Dipirona"))
                .andExpect(jsonPath("$.medicamentosUsoContinuo").value("Nenhum"))
                .andExpect(jsonPath("$.resumoClinico").value("Paciente em investigação de cefaleia"))
                .andExpect(jsonPath("$.condicoesAtivas").value("Cefaleia"))
                .andExpect(jsonPath("$.historicoAtualizacoes.length()").value(6))
                .andExpect(jsonPath("$.totalConsultasFinalizadas").value(1));

        mockMvc.perform(get("/api/pacientes/{id}/consultas", pacienteId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].soap.subjetivo").value("Cefaleia relatada há dois dias"));

        assertThat(prontuarioRepository.count()).isEqualTo(1);
        assertThat(consultaRepository.countByProntuarioIdAndStatus(
                prontuarioRepository.findByPacienteId(pacienteId).orElseThrow().getId(), StatusConsulta.FINALIZADA)).isEqualTo(1);
    }

    @Test
    void iaSemChaveRetorna503SemImpedirFluxoManual() throws Exception {
        criarMedicoInicialNoBanco();
        String token = login("medico@clinica.com", "senha123");
        long pacienteId = id(mockMvc.perform(post("/api/pacientes")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeCompleto\":\"Paciente IA\",\"cpf\":\"52998224725\",\"dataNascimento\":\"1985-03-10\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long consultaId = id(mockMvc.perform(post("/api/consultas")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d,\"transcricaoOriginal\":\"transcrição válida\"}".formatted(pacienteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/consultas/{id}/analise-ia", consultaId).header("Authorization", bearer(token)))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(post("/api/consultas/{id}/finalizar", consultaId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"soap":{"subjetivo":"S","objetivo":"O","avaliacao":"A","plano":"P"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analisadaPorIa").value(false));
    }

    private void criarMedicoInicialNoBanco() {
        Clinica clinica = new Clinica("Clínica Escola");
        clinica = clinicaRepository.save(clinica);

        Medico medico = new Medico();
        medico.setNomeCompleto("Médico Teste");
        medico.setEmail("medico@clinica.com");
        medico.setSenha(passwordEncoder.encode("senha123"));
        medico.setCrm("CRM-1");
        medico.setCpf("11144477735");
        medico.setDataNascimento(LocalDate.of(1980, 1, 15));
        medico.setClinica(clinica);
        medico.setEspecialidades(new LinkedHashSet<>(java.util.Set.of("Clínica Geral")));
        medicoRepository.saveAndFlush(medico);
    }

    private String login(String email, String senha) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("token").asText();
    }

    private long id(String body) throws Exception {
        return objectMapper.readTree(body).path("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String medicoJson(String email, String cpf, String crm) {
        return """
                {
                  "nomeCompleto":"Médico Teste",
                  "email":"%s",
                  "senha":"senha123",
                  "crm":"%s",
                  "cpf":"%s",
                  "dataNascimento":"1980-01-15",
                  "clinicaAtuacao":"Clínica Escola",
                  "especialidades":["Clínica Geral"]
                }
                """.formatted(email, crm, cpf);
    }
}
