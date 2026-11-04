package br.com.goias.chamados.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga as tarefas agendadas (@Scheduled).
 * Hoje só existe uma: o fechamento automático de chamados resolvidos (atendimento/FechamentoAutomatico).
 */
@Configuration
@EnableScheduling
public class AgendamentoConfig {
}
