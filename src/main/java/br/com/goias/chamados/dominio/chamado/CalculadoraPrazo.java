package br.com.goias.chamados.dominio.chamado;

import java.time.LocalDateTime;

/**
 * Calcula os prazos de SLA (1ª resposta e resolução) e mede tempo útil.
 *
 * Os minutos de cada prazo vêm da tabela sla_config (por área e prioridade) e são contados
 * só no horário de expediente da área, pulando fins de semana sem expediente e feriados
 * (Etapa 4: {@link CalculadoraPrazoExpediente}; a versão provisória em tempo corrido foi apagada).
 */
public interface CalculadoraPrazo {

    /**
     * Prazos de um chamado novo.
     *
     * @param areaId     área do chamado
     * @param prioridade prioridade atual do chamado
     * @param inicio     momento a partir do qual o prazo conta (data de abertura)
     * @return os dois prazos; campos podem vir nulos se não houver SLA configurado
     */
    default Prazos calcular(Long areaId, Prioridade prioridade, LocalDateTime inicio) {
        return recalcular(areaId, prioridade, inicio, 0, false);
    }

    /**
     * Prazos refeitos a partir da abertura (mudança de prioridade e recálculo dos chamados antigos).
     *
     * @param minutosPausados         minutos úteis já pausados ("Aguardando o solicitante") — empurram os prazos
     * @param jaTevePrimeiraResposta  se true, a pausa não empurra o prazo da 1ª resposta
     *                                (a pausa só empurra a 1ª resposta enquanto ela ainda não aconteceu)
     */
    Prazos recalcular(Long areaId, Prioridade prioridade, LocalDateTime abertura,
                      long minutosPausados, boolean jaTevePrimeiraResposta);

    /** Soma minutos úteis a partir de um momento, no expediente da área. */
    LocalDateTime somarMinutosUteis(Long areaId, LocalDateTime inicio, long minutos);

    /** Minutos úteis entre dois momentos, no expediente da área (0 se "ate" não for depois de "de"). */
    long minutosUteisEntre(Long areaId, LocalDateTime de, LocalDateTime ate);

    /** Prazo limite para a 1ª resposta e para a resolução. */
    record Prazos(LocalDateTime primeiraResposta, LocalDateTime resolucao) {

        public static Prazos semPrazo() {
            return new Prazos(null, null);
        }
    }
}
