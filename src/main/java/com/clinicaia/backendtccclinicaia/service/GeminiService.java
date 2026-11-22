package com.clinicaia.backendtccclinicaia.service;

import com.clinicaia.backendtccclinicaia.dto.ia.AnaliseIaResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Constrói o contrato clínico com a Gemini e valida a resposta antes de persisti-la.
 *
 * <p>O prompt define o papel consultivo da IA. A requisição usa baixa temperatura, resposta em JSON e JSON
 * Schema, e a leitura é feita em um DTO tipado. Isso reduz variações de estrutura e rejeita respostas
 * incompletas, mas não elimina o risco de conteúdo clínico incorreto, que segue sujeito à revisão do médico.</p>
 *
 * <p>Economia de tokens: o prompt pede respostas objetivas e a reprodução do diálogo com os falantes
 * identificados, que dobra o texto de saída, pode ser desligada por configuração
 * ({@code gemini.identificar-falas}).</p>
 *
 * <p>Privacidade: o prompt orienta a IA a não reproduzir identificadores (nomes, CPF, telefone, e-mail,
 * endereço) nos campos da resposta. É uma mitigação parcial e probabilística: o texto enviado à API continua
 * íntegro, e a anonimização antes do envio permanece como trabalho futuro.</p>
 */
@Service
public class GeminiService {

    /**
     * Versão das instruções do prompt. Compõe o hash de reaproveitamento de análises, de modo que uma
     * análise gerada com instruções antigas não seja reutilizada depois de uma mudança do prompt.
     * Incrementar a cada alteração relevante do texto do prompt.
     */
    public static final String VERSAO_DO_PROMPT = "3";

    /**
     * Dados do prontuário que a IA pode sugerir: somente informações que o próprio paciente relata de forma explícita.
     *
     * <p>Ficam de fora, e são sempre preenchidos pelo médico, os campos que dependem de julgamento clínico:
     * resumo clínico, condições ativas, diagnósticos confirmados, exames relevantes, tratamentos e procedimentos,
     * plano terapêutico e acompanhamento.</p>
     */
    private static final String[] CAMPOS_PRONTUARIO_IDENTIFICADOS = {
            "alergiasConhecidas", "medicamentosUsoContinuo", "historicoFamiliar",
            "antecedentesPessoais", "cirurgiasInternacoes", "imunizacoes", "habitosAspectosSociais"
    };

    private final GeminiApiClient apiClient;
    private final ObjectMapper objectMapper;

    /** Quando falso, o prompt e o schema omitem o diálogo com os falantes, o que reduz muito os tokens de saída. */
    private final boolean identificarFalas;

    /**
     * Cria o serviço com o cliente HTTP e o serializador JSON.
     *
     * @param apiClient cliente que chama a API Gemini
     * @param objectMapper constrói a requisição e lê a resposta
     * @param identificarFalas quando falso, a Gemini não reproduz a transcrição com os falantes,
     *                         o que reduz muito os tokens de saída
     */
    public GeminiService(
            GeminiApiClient apiClient,
            ObjectMapper objectMapper,
            @Value("${gemini.identificar-falas:true}") boolean identificarFalas
    ) {
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
        this.identificarFalas = identificarFalas;
    }

    /**
     * Envia a transcrição à Gemini e valida a resposta contra o contrato esperado.
     *
     * @param transcricao texto revisado ou digitado da consulta
     * @return SOAP e sugestões consultivas normalizadas, junto do consumo de tokens da chamada
     * @throws GeminiFalhaException quando a Gemini falha ou a resposta não cumpre o contrato
     */
    public ResultadoAnaliseIa analisar(String transcricao) {
        GeminiResposta resposta = apiClient.gerarConteudoEstruturado(criarRequisicao(transcricao));
        try {
            AnaliseIaResponse result = objectMapper.readValue(resposta.texto(), AnaliseIaResponse.class).normalizada();
            validarContrato(result, resposta);
            return new ResultadoAnaliseIa(result, resposta);
        } catch (GeminiFalhaException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GeminiFalhaException(
                    "Não foi possível obter uma análise SOAP válida da Gemini", resposta, exception
            );
        }
    }

    /**
     * Monta o corpo da chamada {@code generateContent}: o prompt, a baixa temperatura (0,1) para respostas
     * estáveis e o JSON Schema que obriga a resposta a seguir o contrato.
     *
     * @param transcricao texto-base
     * @return payload com prompt e configuração de geração
     */
    private ObjectNode criarRequisicao(String transcricao) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode parts = root.putArray("contents").addObject().putArray("parts");
        parts.addObject().put("text", prompt(transcricao));
        ObjectNode generation = root.putObject("generationConfig");
        generation.put("responseMimeType", "application/json");
        generation.put("temperature", 0.1);
        generation.set("responseJsonSchema", schema());
        return root;
    }

    /**
     * Define limites clínicos explícitos para o modelo: papel consultivo, sem diagnóstico definitivo, sem inventar
     * dados e sem reproduzir identificadores do paciente.
     *
     * @param transcricao texto que será delimitado no final da instrução
     * @return prompt completo em português
     */
    private String prompt(String transcricao) {
        String instrucoes = """
                Você é um assistente consultivo de documentação clínica. Analise somente as informações presentes na transcrição.
                Estruture o atendimento nos quatro eixos SOAP e gere sugestões para revisão de um médico.
                Não apresente diagnóstico como definitivo, não invente dados ausentes e use "Não informado" quando necessário.
                As sugestões de pré-diagnósticos, exames e prescrições são hipóteses consultivas, nunca ordens autônomas.
                Em dadosProntuarioIdentificados, extraia somente informações que o próprio paciente relatou de forma explícita: alergias, medicamentos de uso contínuo, histórico familiar, antecedentes pessoais (doenças e eventos de saúde anteriores, sem o problema atual), cirurgias e internações, imunizações e hábitos (tabagismo, álcool, atividade física e contexto social).
                Não registre nesses campos diagnósticos, condutas, exames nem o quadro atual, não invente datas, nomes de procedimentos ou detalhes que não foram ditos, e devolva texto vazio para o que não foi relatado.
                Seja objetivo e conciso: frases curtas, sem repetir o mesmo conteúdo em mais de um campo.
                Privacidade: em todos os campos da resposta, não reproduza dados que identifiquem o paciente ou terceiros, como nomes próprios, CPF, RG, telefone, e-mail, endereço e data de nascimento completa. Refira-se a "o paciente" e mantenha apenas as informações clínicas relevantes (sintomas, achados, histórico, medicamentos e condutas).
                """;
        if (identificarFalas) {
            instrucoes += """
                    No campo 'transcricaoIdentificada', você deve analisar a TRANSCRIÇÃO e identificar quem falou cada fala (médico ou paciente). Formate o resultado como um diálogo estruturado, identificando claramente cada orador (ex: '[Médico]: ...' e '[Paciente]: ...'). Nesse diálogo, substitua qualquer dado de identificação por marcadores como [nome], [CPF], [telefone], [e-mail] ou [endereço]. Caso identifique que é apenas o médico falando (por exemplo, um monólogo ou ditado clínico sem interação direta do paciente), adicione no início do campo a observação '[Apenas o Médico falando sobre a consulta]' e formate o relato a partir disso.
                    """;
        }
        return instrucoes + "TRANSCRIÇÃO:\n" + transcricao;
    }

    /**
     * Monta o JSON Schema que a resposta da Gemini deve obedecer.
     *
     * @return JSON Schema obrigatório para SOAP, sugestões e dados do prontuário relatados pelo paciente
     */
    private ObjectNode schema() {
        ObjectNode string = objectMapper.createObjectNode().put("type", "string");
        ObjectNode stringArray = objectMapper.createObjectNode().put("type", "array");
        stringArray.set("items", string.deepCopy());

        ObjectNode soap = objectMapper.createObjectNode().put("type", "object");
        ObjectNode soapProps = soap.putObject("properties");
        for (String eixo : new String[]{"subjetivo", "objetivo", "avaliacao", "plano"}) {
            soapProps.set(eixo, string.deepCopy());
        }
        soap.putArray("required").add("subjetivo").add("objetivo").add("avaliacao").add("plano");

        ObjectNode prontuario = objectMapper.createObjectNode().put("type", "object");
        ObjectNode prontuarioProps = prontuario.putObject("properties");
        ArrayNode prontuarioObrigatorios = prontuario.putArray("required");
        for (String campo : CAMPOS_PRONTUARIO_IDENTIFICADOS) {
            prontuarioProps.set(campo, string.deepCopy());
            prontuarioObrigatorios.add(campo);
        }

        ObjectNode root = objectMapper.createObjectNode().put("type", "object");
        ObjectNode properties = root.putObject("properties");
        properties.set("soap", soap);
        properties.set("preDiagnosticos", stringArray.deepCopy());
        properties.set("exames", stringArray.deepCopy());
        properties.set("prescricoes", stringArray.deepCopy());
        properties.set("dadosProntuarioIdentificados", prontuario);
        ArrayNode required = root.putArray("required");
        required.add("soap").add("preDiagnosticos").add("exames").add("prescricoes").add("dadosProntuarioIdentificados");
        if (identificarFalas) {
            properties.set("transcricaoIdentificada", string.deepCopy());
            required.add("transcricaoIdentificada");
        }
        return root;
    }

    /**
     * Rejeita respostas em que algum eixo do SOAP veio vazio.
     *
     * @param result resposta desserializada
     * @param resposta chamada que a originou, preservada para contabilizar os tokens já gastos
     * @throws GeminiFalhaException quando qualquer eixo SOAP está ausente
     */
    private void validarContrato(AnaliseIaResponse result, GeminiResposta resposta) {
        if (result.soap() == null
                || semTexto(result.soap().subjetivo())
                || semTexto(result.soap().objetivo())
                || semTexto(result.soap().avaliacao())
                || semTexto(result.soap().plano())) {
            throw new GeminiFalhaException("Gemini retornou um SOAP incompleto", resposta, null);
        }
    }

    /**
     * Verifica se o texto é nulo ou só tem espaços.
     *
     * @param value texto avaliado
     * @return se é nulo ou branco
     */
    private boolean semTexto(String value) {
        return value == null || value.isBlank();
    }
}
