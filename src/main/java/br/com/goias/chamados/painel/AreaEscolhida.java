package br.com.goias.chamados.painel;

import java.util.List;
import java.util.Map;

/**
 * Área mostrada no painel/relatórios.
 * GESTOR: sempre a dele (sem seletor). ADMIN: escolhe no seletor (padrão: TI).
 *
 * @param podeEscolher true para ADMIN (mostra o seletor de área)
 */
public record AreaEscolhida(Long id, String nome, String sigla, boolean podeEscolher, List<Opcao> opcoes) {

    public record Opcao(Long id, String nome) {
    }

    /** Parâmetro "area" que vai nos links (só para ADMIN; o gestor não precisa). */
    public Map<String, String> parametro() {
        return podeEscolher ? Map.of("area", String.valueOf(id)) : Map.of();
    }

    /** "da TI", "da Manutenção" (as áreas do clube são femininas). */
    public String preposicao() {
        return "da";
    }
}
