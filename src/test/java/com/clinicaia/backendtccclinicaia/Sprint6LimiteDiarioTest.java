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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cota diária de tokens por médico: ao atingir o limite, novas análises são recusadas com HTTP 429. */
@SpringBootTest(properties = "gemini.daily-token-limit-per-doctor=500")
@AutoConfigureMockMvc
class Sprint6LimiteDiarioTest {

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
    void recusaNovaAnaliseDepoisDeAtingirACotaDiaria() throws Exception {
        Clinica clinica = clinicas.save(new Clinica("Clínica Cota"));
        Medico medico = new Medico();
        medico.setNomeCompleto("Dra. Ana");
        medico.setEmail("ana@clinica.com");
        medico.setSenha(encoder.encode("senha123"));
        medico.setCrm("CRM-1");
        medico.setCpf("52998224725");
        medico.setDataNascimento(LocalDate.of(1980, 1, 15));
        medico.setClinica(clinica);
        medico.setEspecialidades(new LinkedHashSet<>(Set.of("Clínica Geral")));
        medicos.saveAndFlush(medico);

        String token = objectMapper.readTree(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@clinica.com\",\"senha\":\"senha123\"}"))
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        long pacienteId = objectMapper.readTree(mockMvc.perform(post("/api/pacientes").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeCompleto\":\"Paciente\",\"cpf\":\"11144477735\",\"dataNascimento\":\"1990-05-20\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long consultaId = objectMapper.readTree(mockMvc.perform(post("/api/consultas").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d,\"transcricaoOriginal\":\"primeiro relato\"}".formatted(pacienteId)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        when(geminiApiClient.gerarConteudoEstruturado(any())).thenReturn(new GeminiResposta("""
                {"soap":{"subjetivo":"S","objetivo":"O","avaliacao":"A","plano":"P"},"preDiagnosticos":[],"exames":[],
                 "prescricoes":[],"dadosProntuarioIdentificados":{"alergiasConhecidas":"","medicamentosUsoContinuo":"","historicoFamiliar":""}}
                """, "modelo-teste", 1, 400, 400, 200, 1000, 500));

        mockMvc.perform(post("/api/consultas/{id}/analise-ia", consultaId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/consultas/{id}", consultaId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"transcricaoOriginal\":\"segundo relato diferente\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/consultas/{id}/analise-ia", consultaId).header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests());
        verify(geminiApiClient, times(1)).gerarConteudoEstruturado(any());
    }
}
