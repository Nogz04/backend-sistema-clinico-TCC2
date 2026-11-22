package com.clinicaia.backendtccclinicaia.dto.ia;

import com.clinicaia.backendtccclinicaia.dto.consulta.SoapDto;
import com.clinicaia.backendtccclinicaia.dto.prontuario.ConsolidacaoProntuarioRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Contrato tipado da resposta estruturada retornada pela Gemini.
 *
 * <p>Todo conteúdo é consultivo e permanece sujeito à revisão do médico.</p>
 *
 * @param soap proposta de estruturação SOAP
 * @param preDiagnosticos hipóteses diagnósticas preliminares
 * @param exames possíveis exames para avaliação do profissional
 * @param prescricoes possíveis condutas ou prescrições para avaliação
 * @param dadosProntuarioIdentificados dados longitudinais explicitamente citados na transcrição
 * @param transcricaoIdentificada diálogo com os falantes identificados (opcional)
 * @param uso consumo de tokens da chamada; preenchido pelo sistema, nunca pela Gemini
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnaliseIaResponse(
        SoapDto soap,
        List<String> preDiagnosticos,
        List<String> exames,
        List<String> prescricoes,
        ConsolidacaoProntuarioRequest dadosProntuarioIdentificados,
        String transcricaoIdentificada,
        UsoAnaliseDto uso
) {
    /**
     * Substitui listas ausentes por coleções imutáveis vazias.
     *
     * @return nova resposta segura para iteração e cópia nas entidades
     */
    public AnaliseIaResponse normalizada() {
        return new AnaliseIaResponse(
                soap,
                preDiagnosticos == null ? List.of() : List.copyOf(preDiagnosticos),
                exames == null ? List.of() : List.copyOf(exames),
                prescricoes == null ? List.of() : List.copyOf(prescricoes),
                dadosProntuarioIdentificados,
                transcricaoIdentificada,
                null
        );
    }

    /**
     * Anexa o consumo de tokens, que é medido pelo sistema e não vem da Gemini.
     *
     * @param consumo tokens e modelo da chamada
     * @return cópia com o consumo informado
     */
    public AnaliseIaResponse comUso(UsoAnaliseDto consumo) {
        return new AnaliseIaResponse(soap, preDiagnosticos, exames, prescricoes,
                dadosProntuarioIdentificados, transcricaoIdentificada, consumo);
    }
}
