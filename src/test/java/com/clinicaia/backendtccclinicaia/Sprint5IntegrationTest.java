package com.clinicaia.backendtccclinicaia;

import com.clinicaia.backendtccclinicaia.model.*;
import com.clinicaia.backendtccclinicaia.repository.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Valida recuperação de senha, neutralidade da resposta e uso único do token. */
@SpringBootTest @AutoConfigureMockMvc
class Sprint5IntegrationTest {
    @Autowired MockMvc mockMvc; @Autowired PasswordEncoder encoder;
    @Autowired ClinicaRepository clinicas; @Autowired MedicoRepository medicos;
    @Autowired RecuperacaoSenhaRepository recuperacoes;
    @Autowired ConviteMedicoRepository convites; @Autowired AtualizacaoProntuarioRepository atualizacoes;
    @Autowired ConsultaRepository consultas; @Autowired ProntuarioRepository prontuarios;
    @Autowired PacienteRepository pacientes; @Autowired JdbcTemplate jdbc;

    @BeforeEach void clean(){ recuperacoes.deleteAll(); convites.deleteAll(); atualizacoes.deleteAll(); consultas.deleteAll();
        prontuarios.deleteAll(); jdbc.update("DELETE FROM medico_paciente"); pacientes.deleteAll(); medicos.deleteAll(); clinicas.deleteAll(); }

    @Test void redefineSenhaComTokenDeUsoUnicoSemEnumerarContas() throws Exception {
        Clinica clinica=clinicas.save(new Clinica("Clínica Segura"));
        Medico medico=new Medico(); medico.setClinica(clinica); medico.setNomeCompleto("Dra. Ana"); medico.setEmail("ana@clinica.com");
        medico.setCrm("CRM-55"); medico.setCpf("52998224725"); medico.setDataNascimento(LocalDate.of(1990,1,1));
        medico.setSenha(encoder.encode("SenhaAntiga123")); medico.setSenhaDefinida(true); medicos.save(medico);

        mockMvc.perform(post("/api/auth/solicitar-recuperacao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"inexistente@clinica.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mensagem").exists());
        String body=mockMvc.perform(post("/api/auth/solicitar-recuperacao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@clinica.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.urlDesenvolvimento").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String url=JsonPath.read(body,"$.urlDesenvolvimento"); String token=url.substring(url.indexOf("token=")+6);
        mockMvc.perform(post("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\""+token+"\",\"novaSenha\":\"SenhaNova123\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\""+token+"\",\"novaSenha\":\"OutraSenha123\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@clinica.com\",\"senha\":\"SenhaNova123\"}"))
                .andExpect(status().isOk());
    }
}
