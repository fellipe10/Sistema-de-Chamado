package br.com.goias.chamados.config;

import br.com.goias.chamados.dominio.cadastro.ParametroSistema;
import br.com.goias.chamados.dominio.cadastro.ParametroSistemaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Leitura dos parâmetros gerais (tabela parametro_sistema, V7) — Etapa 5.
 *
 * - Guarda os valores em memória; {@link #recarregar()} é chamado depois que o admin salva.
 * - Se o banco não responder (ex.: página de erro com o banco fora do ar), devolve os padrões
 *   em vez de quebrar a tela.
 * - Usado pelo layout público (rodapé: ${@parametrosSistema.rodapePublico()}) e pelo fechamento automático.
 */
@Slf4j
@Component
public class ParametrosSistema {

    /** Texto usado quando um ramal citado no rodapé ainda não foi preenchido (o mesmo das etapas anteriores). */
    public static final String RODAPE_GENERICO = "Urgente de verdade? Ligue para o ramal da TI ou da Manutenção.";

    private final ParametroSistemaRepository repository;
    private final int diasFechamentoYml;
    private volatile Map<String, String> cache;

    public ParametrosSistema(ParametroSistemaRepository repository,
                             @Value("${chamados.fechamento-automatico.dias-uteis:5}") int diasFechamentoYml) {
        this.repository = repository;
        this.diasFechamentoYml = diasFechamentoYml;
    }

    public String ramalTi() {
        return valor(ParametroSistema.RAMAL_TI);
    }

    public String ramalManutencao() {
        return valor(ParametroSistema.RAMAL_MANUTENCAO);
    }

    public String textoRodape() {
        return valor(ParametroSistema.RODAPE_PUBLICO);
    }

    /** Rodapé pronto para as telas públicas. */
    public String rodapePublico() {
        return montarRodape(textoRodape(), ramalTi(), ramalManutencao());
    }

    /** Dias do fechamento automático: o da tela, ou o do application.yml quando vazio. */
    public int diasFechamentoAutomatico() {
        return resolverDiasFechamento(valor(ParametroSistema.FECHAMENTO_DIAS_UTEIS), diasFechamentoYml);
    }

    /** Valor do application.yml (mostrado na tela como "padrão"). */
    public int diasFechamentoPadrao() {
        return diasFechamentoYml;
    }

    /** Esquece o cache (chamar depois de gravar). */
    public void recarregar() {
        cache = null;
    }

    // =================================================================
    // Regras puras (testadas em ParametrosSistemaTest)
    // =================================================================

    /**
     * Troca {ramal_ti} e {ramal_manutencao} pelos ramais.
     * Texto vazio, ou que cita um ramal ainda vazio → texto genérico (nunca mostra "{ramal_ti}" nem um buraco).
     */
    public static String montarRodape(String texto, String ramalTi, String ramalManutencao) {
        if (texto == null || texto.isBlank()) {
            return RODAPE_GENERICO;
        }
        boolean usaTi = texto.contains("{ramal_ti}");
        boolean usaMan = texto.contains("{ramal_manutencao}");
        if ((usaTi && vazio(ramalTi)) || (usaMan && vazio(ramalManutencao))) {
            return RODAPE_GENERICO;
        }
        return texto.strip()
                .replace("{ramal_ti}", usaTi ? ramalTi.strip() : "")
                .replace("{ramal_manutencao}", usaMan ? ramalManutencao.strip() : "");
    }

    /**
     * Valor gravado → dias. Vazio = padrão do application.yml; número ≥ 0 = ele (0 desliga);
     * qualquer outra coisa = padrão (com aviso no log).
     */
    public static int resolverDiasFechamento(String valorGravado, int padraoYml) {
        if (vazio(valorGravado)) {
            return padraoYml;
        }
        try {
            int dias = Integer.parseInt(valorGravado.strip());
            if (dias >= 0) {
                return dias;
            }
        } catch (NumberFormatException e) {
            // cai no padrão abaixo
        }
        log.warn("Parâmetro fechamento_dias_uteis inválido (\"{}\"); usando o padrão {}.", valorGravado, padraoYml);
        return padraoYml;
    }

    // =================================================================

    private String valor(String chave) {
        Map<String, String> atual = cache;
        if (atual == null) {
            try {
                Map<String, String> lido = new HashMap<>();
                repository.findAll().forEach(p -> lido.put(p.getChave(), p.getValor()));
                cache = lido;
                atual = lido;
            } catch (RuntimeException e) {
                log.warn("Não foi possível ler os parâmetros do sistema ({}); usando os padrões.", e.getMessage());
                return null;
            }
        }
        return atual.get(chave);
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }
}
