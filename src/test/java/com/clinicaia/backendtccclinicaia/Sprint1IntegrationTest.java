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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Sprint1IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProntuarioRepository prontuarioRepository;
    @Autowired PacienteRepository pacienteRepository;
    @Autowired MedicoRepository medicoRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ClinicaRepository clinicaRepository;
    @Autowired ConsultaRepository consultaRepository;
    @Autowired AtualizacaoProntuarioRepository atualizacaoProntuarioRepository;

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

        String token = login("medico@clinica.com", "senha123");
        mockMvc.perform(get("/api/pacientes").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void prontuarioEhUnicoECriadoJuntoComCadastroDoPaciente() throws Exception {
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
        assertThat(pacienteRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/api/pacientes/{id}/prontuario", pacienteId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alergiasConhecidas").value("Dipirona"));
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
}
