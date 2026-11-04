package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.config.ParametrosSistema;
import br.com.goias.chamados.dominio.cadastro.CalendarioExpediente;
import br.com.goias.chamados.dominio.cadastro.CalendariosExpediente;
import br.com.goias.chamados.dominio.chamado.Chamado;
import br.com.goias.chamados.dominio.chamado.ChamadoInteracao;
import br.com.goias.chamados.dominio.chamado.ChamadoInteracaoRepository;
import br.com.goias.chamados.dominio.chamado.ChamadoRepository;
import br.com.goias.chamados.dominio.chamado.StatusChamado;
import br.com.goias.chamados.dominio.chamado.TipoInteracao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * Fecha sozinho o chamado RESOLVIDO há N dias úteis (Etapa 4).
 *
 * - Roda 1 minuto depois que o sistema sobe e depois a cada hora
 *   (chamados.fechamento-automatico.intervalo, padrão PT1H).
 * - N = parâmetro "Dias para fechamento automático" de /admin/configuracoes (Etapa 5); quando vazio,
 *   chamados.fechamento-automatico.dias-uteis do application.yml (padrão 5). 0 desliga.
 *   É lido a cada execução: mudar na tela vale sem reiniciar o sistema.
 * - Grava MUDANCA_STATUS RESOLVIDO → FECHADO sem autor (foi o sistema), com o texto
 *   "Fechado automaticamente após N dias úteis sem retorno.", e preenche data_fechamento.
 * - Cada chamado é fechado na sua própria transação: se alguém mexer no mesmo chamado
 *   ao mesmo tempo (ex.: reabriu), aquele é pulado e tenta de novo na próxima hora.
 */
@Slf4j
@Component
public class FechamentoAutomatico {

    private final ChamadoRepository chamadoRepository;
    private final ChamadoInteracaoRepository interacaoRepository;
    private final CalendariosExpediente calendarios;
    private final TransactionTemplate transacao;
    private final IntSupplier diasUteis;

    @Autowired
    public FechamentoAutomatico(ChamadoRepository chamadoRepository,
                                ChamadoInteracaoRepository interacaoRepository,
                                CalendariosExpediente calendarios,
                                PlatformTransactionManager transactionManager,
                                ParametrosSistema parametros) {
        this(chamadoRepository, interacaoRepository, calendarios, transactionManager,
                (IntSupplier) parametros::diasFechamentoAutomatico);
    }

    /** Com N fixo (usado nos testes). */
    public FechamentoAutomatico(ChamadoRepository chamadoRepository,
                                ChamadoInteracaoRepository interacaoRepository,
                                CalendariosExpediente calendarios,
                                PlatformTransactionManager transactionManager,
                                int diasUteis) {
        this(chamadoRepository, interacaoRepository, calendarios, transactionManager, (IntSupplier) () -> diasUteis);
    }

    private FechamentoAutomatico(ChamadoRepository chamadoRepository,
                                 ChamadoInteracaoRepository interacaoRepository,
                                 CalendariosExpediente calendarios,
                                 PlatformTransactionManager transactionManager,
                                 IntSupplier diasUteis) {
        this.chamadoRepository = chamadoRepository;
        this.interacaoRepository = interacaoRepository;
        this.calendarios = calendarios;
        this.transacao = new TransactionTemplate(transactionManager);
        this.diasUteis = diasUteis;
    }

    @Scheduled(initialDelayString = "${chamados.fechamento-automatico.primeira-execucao:PT1M}",
            fixedDelayString = "${chamados.fechamento-automatico.intervalo:PT1H}")
    public void executarAgendado() {
        try {
            executar(LocalDateTime.now());
        } catch (RuntimeException e) {
            log.error("Fechamento automático falhou; tenta de novo na próxima execução.", e);
        }
    }

    /** @return quantos chamados foram fechados */
    public int executar(LocalDateTime agora) {
        final int diasUteis = this.diasUteis.getAsInt();
        if (diasUteis <= 0) {
            log.debug("Fechamento automático desligado (dias-uteis = {}).", diasUteis);
            return 0;
        }
        List<Long> candidatos = transacao.execute(t -> {
            Map<Long, CalendarioExpediente> porArea = new HashMap<>();
            return chamadoRepository.findByStatus(StatusChamado.RESOLVIDO).stream()
                    .filter(c -> RegraFechamentoAutomatico.deveFechar(c.getStatus(), c.getDataResolucao(), agora,
                            porArea.computeIfAbsent(c.getArea().getId(), calendarios::daArea), diasUteis))
                    .map(Chamado::getId)
                    .toList();
        });
        int fechados = 0;
        for (Long id : candidatos) {
            try {
                Boolean ok = transacao.execute(t -> fechar(id, agora, diasUteis));
                if (Boolean.TRUE.equals(ok)) {
                    fechados++;
                }
            } catch (RuntimeException e) {
                log.warn("Não foi possível fechar automaticamente o chamado {}: {}", id, e.getMessage());
            }
        }
        if (fechados > 0) {
            log.info("Fechamento automático: {} chamado(s) fechado(s) após {} dia(s) útil(eis) resolvido(s).",
                    fechados, diasUteis);
        }
        return fechados;
    }

    private boolean fechar(Long id, LocalDateTime agora, int diasUteis) {
        Chamado chamado = chamadoRepository.findComDetalhesById(id).orElse(null);
        if (chamado == null || chamado.getStatus() != StatusChamado.RESOLVIDO) {
            return false; // mudou desde a seleção (ex.: reaberto)
        }
        chamado.setStatus(StatusChamado.FECHADO);
        chamado.setDataFechamento(agora);

        ChamadoInteracao interacao = new ChamadoInteracao();
        interacao.setChamado(chamado);
        interacao.setAutor(null);
        interacao.setTipo(TipoInteracao.MUDANCA_STATUS);
        interacao.setInterno(TipoInteracao.MUDANCA_STATUS.isInterno());
        interacao.setStatusAnterior(StatusChamado.RESOLVIDO);
        interacao.setStatusNovo(StatusChamado.FECHADO);
        interacao.setTexto(RegraFechamentoAutomatico.texto(diasUteis));
        interacao.setData(agora);
        interacaoRepository.save(interacao);
        chamadoRepository.saveAndFlush(chamado);
        return true;
    }
}
