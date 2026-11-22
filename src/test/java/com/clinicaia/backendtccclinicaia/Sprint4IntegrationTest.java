package com.clinicaia.backendtccclinicaia;

import com.clinicaia.backendtccclinicaia.model.Clinica;
import com.clinicaia.backendtccclinicaia.model.Medico;
import com.clinicaia.backendtccclinicaia.repository.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class Sprint4IntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired PasswordEncoder encoder;
    @Autowired ClinicaRepository clinicas;
    @Autowired MedicoRepository medicos;
    @Autowired ConviteMedicoRepository convites;
    @Autowired AtualizacaoProntuarioRepository atualizacoes;
    @Autowired ConsultaRepository consultas;
    @Autowired ProntuarioRepository prontuarios;
    @Autowired PacienteRepository pacientes;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        convites.deleteAll(); atualizacoes.deleteAll(); consultas.deleteAll(); prontuarios.deleteAll();
        jdbc.update("DELETE FROM medico_paciente");
        pacientes.deleteAll(); medicos.deleteAll(); clinicas.deleteAll();
    }

    @Test
    void cadastraMedicoNaMesmaClinicaEAtivaSenhaPorConvite() throws Exception {
        Clinica clinica = clinicas.save(new Clinica("Clínica Horizonte"));
        criarMedico(clinica, "Gestor", "gestor@clinica.com", "CRM-1", "52998224725", "senha123");
        String tokenGestor = login("gestor@clinica.com", "senha123");

        String response = mockMvc.perform(post("/api/medicos")
                        .header("Authorization", bearer(tokenGestor)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto":"Dra. Ana Lima","email":"ana@clinica.com","crm":"CRM-2",
                                 "cpf":"11144477735","dataNascimento":"1990-04-12","especialidades":["Cardiologia"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.medico.clinicaAtuacao").value("Clínica Horizonte"))
                .andExpect(jsonPath("$.conviteEnviadoPorEmail").value(false))
                .andExpect(jsonPath("$.urlConviteDesenvolvimento").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String url = JsonPath.read(response, "$.urlConviteDesenvolvimento");
        String inviteToken = url.substring(url.indexOf("token=") + 6);
        mockMvc.perform(get("/api/auth/convite").param("token", inviteToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("ana@clinica.com"));
        mockMvc.perform(post("/api/auth/definir-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + inviteToken + "\",\"novaSenha\":\"SenhaNova123\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/definir-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + inviteToken + "\",\"novaSenha\":\"OutraSenha123\"}"))
                .andExpect(status().isBadRequest());
        login("ana@clinica.com", "SenhaNova123");
    }

    @Test
    void medicosDaMesmaClinicaVeemPacientesMasOutraClinicaNao() throws Exception {
        Clinica a = clinicas.save(new Clinica("Clínica A"));
        Clinica b = clinicas.save(new Clinica("Clínica B"));
        criarMedico(a, "Médico A1", "a1@clinica.com", "CRM-A1", "52998224725", "senha123");
        criarMedico(a, "Médico A2", "a2@clinica.com", "CRM-A2", "11144477735", "senha123");
        criarMedico(b, "Médico B", "b@clinica.com", "CRM-B", "12345678909", "senha123");
        String a1 = login("a1@clinica.com", "senha123");
        String a2 = login("a2@clinica.com", "senha123");
        String bToken = login("b@clinica.com", "senha123");

        mockMvc.perform(post("/api/pacientes").header("Authorization", bearer(a1))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nomeCompleto":"Paciente da Clínica A","cpf":"98765432100","dataNascimento":"1985-08-20"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/pacientes").header("Authorization", bearer(a2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nomeCompleto").value("Paciente da Clínica A"));
        mockMvc.perform(get("/api/pacientes").header("Authorization", bearer(bToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    private void criarMedico(Clinica clinica, String nome, String email, String crm, String cpf, String senha) {
        Medico medico = new Medico(); medico.setClinica(clinica); medico.setNomeCompleto(nome); medico.setEmail(email);
        medico.setCrm(crm); medico.setCpf(cpf); medico.setDataNascimento(LocalDate.of(1980, 1, 1));
        medico.setSenha(encoder.encode(senha)); medico.setSenhaDefinida(true); medicos.save(medico);
    }

    private String login(String email, String senha) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private String bearer(String token) { return "Bearer " + token; }
}
