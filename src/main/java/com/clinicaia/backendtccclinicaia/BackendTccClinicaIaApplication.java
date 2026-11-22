package com.clinicaia.backendtccclinicaia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicação do sistema clínico.
 *
 * <p>Inicializa o contexto Spring Boot, que expõe a API REST consumida pelo frontend.</p>
 */
@SpringBootApplication
public class BackendTccClinicaIaApplication {

    /**
     * Inicia a aplicação.
     *
     * @param args argumentos de linha de comando repassados ao Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(BackendTccClinicaIaApplication.class, args);
    }

}
