package com.clinicaia.backendtccclinicaia;

import com.clinicaia.backendtccclinicaia.model.Clinica;
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
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testes de integração do incremento vertical entregue na Sprint 2. */
@SpringBootTest
@AutoConfigureMockMvc
class Sprint2IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ConsultaRepository consultaRepository;
    @Autowired AtualizacaoProntuarioRepository atualizacaoProntuarioRepository;
    @Autowired ProntuarioRepository prontuarioRepository;
    @Autowired PacienteRepository pacienteRepository;
    @Autowired MedicoRepository medicoRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClinicaRepository clinicaRepository;
    @Autowired PasswordEncoder passwordEncoder;

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
    void criaAtualizaEListaRascunhoSemAlterarProntuario() throws Exception {
        criarMedico("medico@clinica.com", "CRM-1", "11144477735");
        String token = login("medico@clinica.com", "senha123");
        long pacienteId = criarPaciente(token);
        long prontuarioId = prontuarioRepository.findByPacienteId(pacienteId).orElseThrow().getId();

        String resposta = mockMvc.perform(post("/api/consultas")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pacienteId":%d,
                                  "transcricaoOriginal":"Paciente relata cefaleia há dois dias.",
                                  "soap":{"subjetivo":"Cefaleia há dois dias."}
                                }
                                """.formatted(pacienteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.prontuarioId").value(prontuarioId))
                .andReturn().getResponse().getContentAsString();

        long consultaId = objectMapper.readTree(resposta).path("id").asLong();
        mockMvc.perform(put("/api/consultas/{id}", consultaId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transcricaoOriginal":"Relato revisado pelo médico.",
                                  "dadosGeraisConsulta":"Sem sinais de alarme.",
                                  "soap":{"subjetivo":"Cefaleia revisada.","objetivo":"PA 120/80"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcricaoOriginal").value("Relato revisado pelo médico."))
                .andExpect(jsonPath("$.soap.objetivo").value("PA 120/80"));

        mockMvc.perform(get("/api/consultas/rascunhos").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(consultaId));

        assertThat(consultaRepository.count()).isEqualTo(1);
        assertThat(prontuarioRepository.findById(prontuarioId).orElseThrow().getAlergiasConhecidas())
                .isEqualTo("Dipirona");

        mockMvc.perform(delete("/api/consultas/{id}", consultaId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/consultas/rascunhos").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        assertThat(consultaRepository.existsById(consultaId)).isFalse();
    }

    @Test
    void impedeOutroMedicoDeLerOuAlterarRascunho() throws Exception {
        criarMedico("autor@clinica.com", "CRM-1", "11144477735");
        criarMedico("outro@clinica.com", "CRM-2", "52998224725");
        String tokenAutor = login("autor@clinica.com", "senha123");
        String tokenOutro = login("outro@clinica.com", "senha123");
        long pacienteId = criarPaciente(tokenAutor);

        String resposta = mockMvc.perform(post("/api/consultas")
                        .header("Authorization", bearer(tokenAutor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d,\"transcricaoOriginal\":\"Conteúdo privado\"}".formatted(pacienteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long consultaId = objectMapper.readTree(resposta).path("id").asLong();

        mockMvc.perform(get("/api/consultas/{id}", consultaId).header("Authorization", bearer(tokenOutro)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/consultas/{id}", consultaId)
                        .header("Authorization", bearer(tokenOutro))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcricaoOriginal\":\"Tentativa indevida\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/consultas/{id}", consultaId)
                        .header("Authorization", bearer(tokenOutro)))
                .andExpect(status().isForbidden());
        assertThat(consultaRepository.existsById(consultaId)).isTrue();
    }

    /** @return médico persistido para autenticação dos cenários */
    private Medico criarMedico(String email, String crm, String cpf) {
        Clinica clinica = clinicaRepository.findAll().stream().findFirst()
                .orElseGet(() -> clinicaRepository.save(new Clinica("Clínica Escola")));
        Medico medico = new Medico();
        medico.setNomeCompleto("Médico " + crm);
        medico.setEmail(email);
        medico.setSenha(passwordEncoder.encode("senha123"));
        medico.setCrm(crm);
        medico.setCpf(cpf);
        medico.setDataNascimento(LocalDate.of(1980, 1, 15));
        medico.setClinica(clinica);
        medico.setEspecialidades(new LinkedHashSet<>(java.util.Set.of("Clínica Geral")));
        return medicoRepository.saveAndFlush(medico);
    }

    /** @return identificador do paciente e de seu prontuário único */
    private long criarPaciente(String token) throws Exception {
        String body = mockMvc.perform(post("/api/pacientes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nomeCompleto":"Roberta Almeida",
                                  "cpf":"39053344705",
                                  "dataNascimento":"1992-04-12",
                                  "prontuarioInicial":{"alergiasConhecidas":"Dipirona"}
                                }
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("id").asLong();
    }

    /** @return JWT emitido após autenticação válida */
    private String login(String email, String senha) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("token").asText();
    }

    /** @return valor do cabeçalho Authorization */
    private String bearer(String token) {
        return "Bearer " + token;
    }
}
