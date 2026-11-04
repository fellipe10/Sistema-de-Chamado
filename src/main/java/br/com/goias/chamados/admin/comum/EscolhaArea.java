package br.com.goias.chamados.admin.comum;

import br.com.goias.chamados.dominio.area.Area;
import br.com.goias.chamados.dominio.area.AreaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Seletor de área das telas de SLA e expediente (abas em pílula no topo).
 * Mostra todas as áreas (inativas por último, marcadas). Padrão: TI, ou a primeira.
 */
@Component
@RequiredArgsConstructor
public class EscolhaArea {

    private final AreaRepository areaRepository;

    public record OpcaoArea(Long id, String nome, String sigla, boolean ativo) {
        public String rotulo() {
            return ativo ? nome : nome + " (inativa)";
        }
    }

    public record Escolha(OpcaoArea atual, List<OpcaoArea> opcoes) {
    }

    @Transactional(readOnly = true)
    public Escolha escolher(String parametro) {
        List<OpcaoArea> opcoes = areaRepository.findAll().stream()
                .sorted(Comparator.comparing((Area a) -> !a.isAtivo())
                        .thenComparing(a -> !"TI".equals(a.getSigla()))
                        .thenComparing(a -> RegrasNome.chave(a.getNome())))
                .map(a -> new OpcaoArea(a.getId(), a.getNome(), a.getSigla(), a.isAtivo()))
                .toList();
        if (opcoes.isEmpty()) {
            throw new IllegalStateException("Nenhuma área cadastrada");
        }
        Long id = Parametros.numero(parametro);
        OpcaoArea atual = opcoes.stream().filter(o -> o.id().equals(id)).findFirst().orElse(opcoes.get(0));
        return new Escolha(atual, opcoes);
    }
}
