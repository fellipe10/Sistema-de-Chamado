package br.com.goias.chamados.painel;

import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/** Monta links do painel/relatórios mantendo os parâmetros (período, área, filtros). */
final class Urls {

    private Urls() {
    }

    @SafeVarargs
    static String montar(String caminho, Map<String, String>... grupos) {
        Map<String, String> todos = new LinkedHashMap<>();
        for (Map<String, String> g : grupos) {
            g.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    todos.put(k, v);
                }
            });
        }
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(caminho);
        todos.forEach(b::queryParam);
        return b.encode().build().toUriString();
    }
}
