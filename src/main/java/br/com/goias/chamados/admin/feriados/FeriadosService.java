package br.com.goias.chamados.admin.feriados;

import br.com.goias.chamados.admin.RegistroNaoEncontradoException;
import br.com.goias.chamados.admin.comum.RegrasNome;
import br.com.goias.chamados.dominio.area.Area;
import br.com.goias.chamados.dominio.area.AreaRepository;
import br.com.goias.chamados.dominio.cadastro.Feriado;
import br.com.goias.chamados.dominio.cadastro.FeriadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Feriados (/admin/feriados) — Etapa 5.
 *
 * - Lista por ano; novo, editar e EXCLUIR de verdade (feriado não é usado diretamente por chamado:
 *   ele só entra no cálculo do prazo).
 * - Área opcional: vazia = vale para todas as áreas.
 * - Mesma data não pode repetir no mesmo alcance (índice uk_feriado_data_area da V4).
 * - Aviso quando o próximo ano não tem nenhum feriado ({@link AvisoFeriados}).
 * - Não mexe em prazos já calculados: para isso existe o botão Recalcular.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeriadosService {

    public static final int DESCRICAO_MAXIMO = 100;
    private static final Locale PT_BR = new Locale("pt", "BR");

    private final FeriadoRepository feriadoRepository;
    private final AreaRepository areaRepository;
    private final AnoReferencia anoReferencia;

    public record Linha(Long id, LocalDate data, String descricao, String areaNome, boolean passado) {

        public String dia() {
            return String.format("%02d", data.getDayOfMonth());
        }

        public String mes() {
            return data.getMonth().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "");
        }

        public String diaSemana() {
            String s = data.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR);
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }

        /** Cai em sábado ou domingo (para a área com expediente seg–sex, não muda nada no prazo). */
        public boolean fimDeSemana() {
            return data.getDayOfWeek().getValue() >= 6;
        }
    }

    public record Ano(int ano, long quantidade, boolean atual) {
    }

    public record OpcaoArea(Long id, String nome, boolean ativo) {
        public String rotulo() {
            return ativo ? nome : nome + " (inativa)";
        }
    }

    public record Edicao(Long id, String data, String descricao, Long areaId) {
    }

    public record Resultado(Long id, LocalDate data, Map<String, String> erros) {
        public boolean sucesso() {
            return erros.isEmpty();
        }
    }

    public int anoAtual() {
        return anoReferencia.anoAtual();
    }

    @Transactional(readOnly = true)
    public List<Linha> doAno(int ano) {
        LocalDate hoje = LocalDate.now();
        return feriadoRepository.doPeriodo(LocalDate.of(ano, 1, 1), LocalDate.of(ano, 12, 31)).stream()
                .map(f -> new Linha(f.getId(), f.getData(), f.getDescricao(),
                        f.getArea() == null ? null : f.getArea().getNome(), f.getData().isBefore(hoje)))
                .toList();
    }

    /** Abas: anos com feriado + ano atual + próximo (+ o escolhido), em ordem. */
    @Transactional(readOnly = true)
    public List<Ano> anos(int escolhido) {
        int atual = anoAtual();
        TreeSet<Integer> anos = new TreeSet<>(feriadoRepository.anosComFeriado());
        anos.add(atual);
        anos.add(atual + 1);
        anos.add(escolhido);
        return anos.stream()
                .map(a -> new Ano(a, feriadoRepository.doPeriodo(LocalDate.of(a, 1, 1), LocalDate.of(a, 12, 31)).size(),
                        a == escolhido))
                .toList();
    }

    @Transactional(readOnly = true)
    public String aviso() {
        Integer ano = AvisoFeriados.anoSemFeriado(anoAtual(), feriadoRepository.anosComFeriado());
        return ano == null ? null : AvisoFeriados.mensagem(ano);
    }

    @Transactional(readOnly = true)
    public Integer anoSemFeriado() {
        return AvisoFeriados.anoSemFeriado(anoAtual(), feriadoRepository.anosComFeriado());
    }

    @Transactional(readOnly = true)
    public List<OpcaoArea> areas() {
        return areaRepository.findAll().stream()
                .sorted(Comparator.comparing((Area a) -> !a.isAtivo()).thenComparing(a -> RegrasNome.chave(a.getNome())))
                .map(a -> new OpcaoArea(a.getId(), a.getNome(), a.isAtivo()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Edicao edicao(Long id) {
        Feriado f = buscar(id);
        return new Edicao(f.getId(), f.getData().toString(), f.getDescricao(), f.getArea() == null ? null : f.getArea().getId());
    }

    @Transactional
    public Resultado salvar(Long id, String dataTexto, String descricao, Long areaId) {
        Feriado feriado = id == null ? null : buscar(id);
        Map<String, String> erros = new LinkedHashMap<>();

        LocalDate data = null;
        if (dataTexto == null || dataTexto.isBlank()) {
            erros.put("data", "Informe a data.");
        } else {
            try {
                data = LocalDate.parse(dataTexto.strip());
                if (data.getYear() < 2000 || data.getYear() > 2100) {
                    erros.put("data", "Informe uma data entre 2000 e 2100.");
                }
            } catch (DateTimeParseException e) {
                erros.put("data", "Data inválida.");
            }
        }
        String desc = RegrasNome.normalizar(descricao);
        if (desc == null || desc.isEmpty()) {
            erros.put("descricao", "Informe a descrição (ex.: Carnaval).");
        } else if (desc.length() > DESCRICAO_MAXIMO) {
            erros.put("descricao", "A descrição pode ter no máximo " + DESCRICAO_MAXIMO + " caracteres.");
        }
        Area area = null;
        if (areaId != null) {
            area = areaRepository.findById(areaId).orElse(null);
            if (area == null) {
                erros.put("areaId", "Escolha uma área da lista.");
            }
        }
        if (!erros.containsKey("data") && !erros.containsKey("areaId")
                && feriadoRepository.contarNaData(data, areaId, id == null ? -1L : id) > 0) {
            erros.put("data", area == null
                    ? "Já existe um feriado para todas as áreas nesta data."
                    : "Já existe um feriado da área " + area.getNome() + " nesta data.");
        }
        if (!erros.isEmpty()) {
            return new Resultado(null, null, erros);
        }

        Feriado gravar = feriado != null ? feriado : new Feriado();
        gravar.setData(data);
        gravar.setDescricao(desc);
        gravar.setArea(area);
        feriadoRepository.saveAndFlush(gravar);
        log.info("Feriado gravado: {} {} ({}).", data, desc, area == null ? "todas as áreas" : area.getSigla());
        return new Resultado(gravar.getId(), data, Map.of());
    }

    /** Exclui de verdade. @return a data do feriado excluído (para voltar ao ano certo). */
    @Transactional
    public LocalDate excluir(Long id) {
        Feriado f = buscar(id);
        LocalDate data = f.getData();
        feriadoRepository.delete(f);
        log.info("Feriado excluído: {} {}.", data, f.getDescricao());
        return data;
    }

    @Transactional(readOnly = true)
    public Feriado buscar(Long id) {
        return feriadoRepository.findById(id).orElseThrow(() -> new RegistroNaoEncontradoException("Feriado", id));
    }
}
