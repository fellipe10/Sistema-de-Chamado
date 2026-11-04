package br.com.goias.chamados.dominio.chamado;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * Por que o chamado está em AGUARDANDO (decisão oficial da Etapa 4).
 *
 * - SOLICITANTE: depende de uma resposta de quem abriu → o prazo de SLA fica PAUSADO.
 * - TERCEIRO: depende de peça, fornecedor ou terceiro → o prazo continua correndo.
 *
 * descricao     = texto para a equipe (tela do chamado, botão de escolha)
 * textoPublico  = texto para o solicitante (consulta pública e histórico)
 */
@Getter
@RequiredArgsConstructor
public enum MotivoAguardando {

    SOLICITANTE("Aguardando o solicitante", "Aguardando seu retorno", true,
            "O prazo fica pausado até o atendimento ser retomado."),
    TERCEIRO("Aguardando peça/fornecedor/terceiro", "Aguardando peça ou terceiro", false,
            "O prazo continua correndo.");

    private final String descricao;
    private final String textoPublico;
    private final boolean pausaPrazo;
    private final String ajuda;

    /** Acha o motivo pelo texto gravado na interação MUDANCA_STATUS (o texto público). */
    public static Optional<MotivoAguardando> doTextoPublico(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(m -> m.textoPublico.equalsIgnoreCase(texto.strip())).findFirst();
    }
}
