package com.clinicaia.backendtccclinicaia.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Clínica, fronteira de acesso do sistema: médicos, pacientes e consumo de IA são agrupados por ela.
 */
@Entity
@Table(name = "clinicas")
@Getter
@Setter
@NoArgsConstructor
public class Clinica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    /**
     * Cria uma clínica com o nome informado.
     *
     * @param nome nome da clínica
     */
    public Clinica(String nome) {
        this.nome = nome;
    }
}
