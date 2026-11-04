package br.com.goias.chamados.admin.configuracoes;

import br.com.goias.chamados.config.ParametrosSistema;
import br.com.goias.chamados.dominio.cadastro.ParametroSistema;
import br.com.goias.chamados.dominio.cadastro.ParametroSistemaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configurações gerais (/admin/configuracoes) — Etapa 5.
 * Grava na tabela parametro_sistema e renova o cache de {@link ParametrosSistema} depois do commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfiguracoesService {

    public static final int RAMAL_MAXIMO = 20;
    public static final int RODAPE_MAXIMO = 300;
    public static final int DIAS_MAXIMO = 60;

    private final ParametroSistemaRepository repository;
    private final ParametrosSistema parametros;

    /** Valores da tela (texto, como a pessoa digitou). */
    public record Form(String ramalTi, String ramalManutencao, String rodape, String diasFechamento) {
    }

    @Transactional(readOnly = true)
    public Form atual() {
        return new Form(valor(ParametroSistema.RAMAL_TI), valor(ParametroSistema.RAMAL_MANUTENCAO),
                valor(ParametroSistema.RODAPE_PUBLICO), valor(ParametroSistema.FECHAMENTO_DIAS_UTEIS));
    }

    /** Regras dos campos (sem banco). */
    public static Map<String, String> validar(Form f) {
        Map<String, String> erros = new LinkedHashMap<>();
        if (limpo(f.ramalTi()).length() > RAMAL_MAXIMO) {
            erros.put("ramalTi", "No máximo " + RAMAL_MAXIMO + " caracteres.");
        }
        if (limpo(f.ramalManutencao()).length() > RAMAL_MAXIMO) {
            erros.put("ramalManutencao", "No máximo " + RAMAL_MAXIMO + " caracteres.");
        }
        if (limpo(f.rodape()).length() > RODAPE_MAXIMO) {
            erros.put("rodape", "No máximo " + RODAPE_MAXIMO + " caracteres.");
        }
        String dias = limpo(f.diasFechamento());
        if (!dias.isEmpty()) {
            if (!dias.matches("\\d{1,3}") || Integer.parseInt(dias) > DIAS_MAXIMO) {
                erros.put("diasFechamento", "Use um número de 0 a " + DIAS_MAXIMO + " (ou deixe vazio para usar o padrão).");
            }
        }
        return erros;
    }

    @Transactional
    public Map<String, String> salvar(Form f) {
        Map<String, String> erros = validar(f);
        if (!erros.isEmpty()) {
            return erros;
        }
        gravar(ParametroSistema.RAMAL_TI, f.ramalTi());
        gravar(ParametroSistema.RAMAL_MANUTENCAO, f.ramalManutencao());
        gravar(ParametroSistema.RODAPE_PUBLICO, f.rodape() == null ? null : f.rodape().strip().replaceAll("\\s+", " "));
        gravar(ParametroSistema.FECHAMENTO_DIAS_UTEIS, f.diasFechamento());
        repository.flush();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    parametros.recarregar();
                }
            });
        } else {
            parametros.recarregar();
        }
        log.info("Configurações gerais atualizadas.");
        return Map.of();
    }

    private void gravar(String chave, String valor) {
        String v = limpo(valor);
        ParametroSistema p = repository.findById(chave).orElseGet(() -> new ParametroSistema(chave, null));
        p.setValor(v.isEmpty() ? null : v);
        p.setAtualizadoEm(LocalDateTime.now());
        repository.save(p);
    }

    private String valor(String chave) {
        return repository.findById(chave).map(ParametroSistema::getValor).orElse("");
    }

    private static String limpo(String texto) {
        return texto == null ? "" : texto.strip();
    }
}
