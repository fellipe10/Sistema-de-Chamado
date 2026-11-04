package br.com.goias.chamados.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite simples anti-abuso, em memória: no máximo N ações por IP dentro de uma janela de tempo.
 * Usado nas telas públicas (abertura e consulta), cada uma com a sua instância e os seus números.
 *
 * Por ser em memória, zera quando a aplicação reinicia — suficiente para uma intranet.
 */
public class LimitadorPorIp {

    /** De quantas em quantas chamadas a limpeza de IPs antigos é feita. */
    private static final int INTERVALO_LIMPEZA = 200;

    private final int maximo;
    private final Duration janela;
    private final Clock relogio;
    private final Map<String, Deque<Instant>> registros = new ConcurrentHashMap<>();
    private final AtomicInteger chamadas = new AtomicInteger();

    public LimitadorPorIp(int maximo, Duration janela) {
        this(maximo, janela, Clock.systemDefaultZone());
    }

    public LimitadorPorIp(int maximo, Duration janela, Clock relogio) {
        this.maximo = maximo;
        this.janela = janela;
        this.relogio = relogio;
    }

    /** Só verifica se ainda há "vaga" para este IP, sem contar uma nova ação. */
    public boolean podeRegistrar(String ip) {
        limparDeVezEmQuando();
        Deque<Instant> fila = registros.get(chave(ip));
        if (fila == null) {
            return true;
        }
        synchronized (fila) {
            descartarAntigos(fila, relogio.instant());
            return fila.size() < maximo;
        }
    }

    /** Conta uma ação deste IP (chamar depois que a ação deu certo). */
    public void registrar(String ip) {
        Instant agora = relogio.instant();
        Deque<Instant> fila = registros.computeIfAbsent(chave(ip), k -> new ArrayDeque<>());
        synchronized (fila) {
            descartarAntigos(fila, agora);
            fila.addLast(agora);
        }
    }

    /** Verifica e já conta a ação, de uma vez só. Retorna false se passou do limite. */
    public boolean tentar(String ip) {
        limparDeVezEmQuando();
        Instant agora = relogio.instant();
        Deque<Instant> fila = registros.computeIfAbsent(chave(ip), k -> new ArrayDeque<>());
        synchronized (fila) {
            descartarAntigos(fila, agora);
            if (fila.size() >= maximo) {
                return false;
            }
            fila.addLast(agora);
            return true;
        }
    }

    private void descartarAntigos(Deque<Instant> fila, Instant agora) {
        Instant limite = agora.minus(janela);
        while (!fila.isEmpty() && !fila.peekFirst().isAfter(limite)) {
            fila.pollFirst();
        }
    }

    /** Remove IPs sem ações recentes para o mapa não crescer para sempre. */
    private void limparDeVezEmQuando() {
        if (chamadas.incrementAndGet() % INTERVALO_LIMPEZA != 0) {
            return;
        }
        Instant agora = relogio.instant();
        registros.forEach((ip, fila) -> {
            synchronized (fila) {
                descartarAntigos(fila, agora);
                if (fila.isEmpty()) {
                    registros.remove(ip, fila);
                }
            }
        });
    }

    private static String chave(String ip) {
        return ip == null ? "desconhecido" : ip;
    }
}
