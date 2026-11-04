package br.com.goias.chamados.dominio.chamado;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Pausa do prazo de SLA no status AGUARDANDO (decisão oficial da Etapa 4).
 *
 * - Entrar em AGUARDANDO grava o motivo. Se o motivo pausa o prazo ("Aguardando o solicitante"),
 *   grava também pausa_inicio = agora.
 * - Sair de AGUARDANDO (retomar, resolver ou devolver para a fila) encerra a pausa:
 *   mede os minutos ÚTEIS parados, empurra prazo_resolucao (e prazo_primeira_resposta, se ainda
 *   não houve 1ª resposta) por esses minutos e soma em minutos_pausados.
 * - "Aguardando peça/fornecedor/terceiro" não pausa: sair dele não muda os prazos.
 */
public final class PausaSla {

    private PausaSla() {
    }

    /** Chamado entrando em AGUARDANDO. */
    public static void iniciar(Chamado chamado, MotivoAguardando motivo, LocalDateTime agora) {
        Objects.requireNonNull(motivo, "motivo");
        chamado.setAguardandoMotivo(motivo);
        chamado.setPausaInicio(motivo.isPausaPrazo() ? agora : null);
    }

    /**
     * Chamado saindo de AGUARDANDO. Limpa o motivo e a pausa.
     *
     * @return minutos úteis que o prazo foi empurrado (0 se não estava pausado)
     */
    public static long encerrar(Chamado chamado, LocalDateTime agora, CalculadoraPrazo calculadora) {
        long minutos = 0;
        LocalDateTime inicio = chamado.getPausaInicio();
        if (inicio != null) {
            Long areaId = chamado.getArea().getId();
            minutos = calculadora.minutosUteisEntre(areaId, inicio, agora);
            if (minutos > 0) {
                if (chamado.getPrazoResolucao() != null) {
                    chamado.setPrazoResolucao(
                            calculadora.somarMinutosUteis(areaId, chamado.getPrazoResolucao(), minutos));
                }
                if (chamado.getDataPrimeiraResposta() == null && chamado.getPrazoPrimeiraResposta() != null) {
                    chamado.setPrazoPrimeiraResposta(
                            calculadora.somarMinutosUteis(areaId, chamado.getPrazoPrimeiraResposta(), minutos));
                }
                chamado.setMinutosPausados(valor(chamado.getMinutosPausados()) + minutos);
            }
        }
        chamado.setPausaInicio(null);
        chamado.setAguardandoMotivo(null);
        return minutos;
    }

    /** Prazos refeitos a partir da abertura com a prioridade informada, somando as pausas já encerradas. */
    public static CalculadoraPrazo.Prazos recalcular(Chamado chamado, Prioridade prioridade,
                                                     CalculadoraPrazo calculadora) {
        return calculadora.recalcular(chamado.getArea().getId(), prioridade, chamado.getDataAbertura(),
                valor(chamado.getMinutosPausados()), chamado.getDataPrimeiraResposta() != null);
    }

    private static long valor(Long minutos) {
        return minutos == null ? 0 : minutos;
    }
}
