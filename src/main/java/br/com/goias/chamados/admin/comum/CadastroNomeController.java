package br.com.goias.chamados.admin.comum;

import br.com.goias.chamados.dominio.cadastro.ItemNomeado;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Telas dos cadastros "só nome + ativo" (setores e locais) — Etapa 5.
 * Lista com busca e filtro de situação + card de novo/editar na mesma página.
 *
 * GET  {rota}?busca=&situacao=&editar={id}
 * POST {rota}                  → salvar (id vazio = novo)
 * POST {rota}/{id}/desativar | /reativar
 *
 * A subclasse informa a rota e os textos ({@link Textos}) e põe @Controller/@RequestMapping.
 */
public abstract class CadastroNomeController<T extends ItemNomeado> {

    /**
     * Textos da tela.
     *
     * @param rota       ex.: "/admin/setores"
     * @param titulo     ex.: "Setores"
     * @param descricao  frase embaixo do título
     * @param singular   ex.: "setor"
     * @param novo       ex.: "Novo setor"
     * @param icone      ícone do Bootstrap Icons
     * @param exemplo    placeholder do campo nome
     */
    public record Textos(String rota, String titulo, String descricao, String singular, String novo,
                         String icone, String exemplo) {
    }

    protected abstract CadastroNomeService<T> service();

    protected abstract Textos textos();

    @GetMapping
    public String lista(@RequestParam(required = false) String busca,
                        @RequestParam(required = false) String situacao,
                        @RequestParam(required = false) String editar,
                        Model model) {
        Long editarId = Parametros.numero(editar);
        String nome = "";
        if (editarId != null) {
            nome = service().buscar(editarId).getNome();
        }
        preparar(model, Parametros.busca(busca), FiltroSituacao.de(situacao), editarId, nome, Map.of());
        return "admin/cadastro-nome";
    }

    @PostMapping
    public String salvar(@RequestParam(required = false) String id,
                         @RequestParam(required = false) String nome,
                         @RequestParam(required = false) String busca,
                         @RequestParam(required = false) String situacao,
                         Model model, RedirectAttributes redirect) {
        Long idEditar = Parametros.numero(id);
        CadastroNomeService.Resultado r = service().salvar(idEditar, nome);
        if (!r.sucesso()) {
            preparar(model, Parametros.busca(busca), FiltroSituacao.de(situacao), idEditar, nome, r.erros());
            return "admin/cadastro-nome";
        }
        redirect.addFlashAttribute("sucesso", idEditar == null
                ? capitalizar(textos().singular()) + " \"" + RegrasNome.normalizar(nome) + "\" cadastrado."
                : "Alterações salvas.");
        redirect.addFlashAttribute("destaque", r.id());
        return "redirect:" + urlLista(busca, situacao);
    }

    @PostMapping("/{id}/desativar")
    public String desativar(@PathVariable Long id,
                            @RequestParam(required = false) String busca,
                            @RequestParam(required = false) String situacao,
                            RedirectAttributes redirect) {
        String erro = service().alterarSituacao(id, false);
        mensagem(redirect, erro, "\"" + service().buscar(id).getNome()
                + "\" desativado. Não aparece mais no formulário de abertura; os chamados antigos continuam iguais.");
        return "redirect:" + urlLista(busca, situacao);
    }

    @PostMapping("/{id}/reativar")
    public String reativar(@PathVariable Long id,
                           @RequestParam(required = false) String busca,
                           @RequestParam(required = false) String situacao,
                           RedirectAttributes redirect) {
        String erro = service().alterarSituacao(id, true);
        mensagem(redirect, erro, "\"" + service().buscar(id).getNome() + "\" reativado.");
        return "redirect:" + urlLista(busca, situacao);
    }

    private void preparar(Model model, String busca, FiltroSituacao situacao, Long editarId, String nome,
                          Map<String, String> erros) {
        model.addAttribute("t", textos());
        model.addAttribute("itens", service().listar(busca, situacao));
        model.addAttribute("busca", busca);
        model.addAttribute("situacao", situacao);
        model.addAttribute("situacoes", FiltroSituacao.values());
        model.addAttribute("editarId", editarId);
        model.addAttribute("nome", nome);
        model.addAttribute("erros", erros);
        model.addAttribute("menuAtivo", "configuracoes");
    }

    private void mensagem(RedirectAttributes redirect, String erro, String sucesso) {
        if (erro != null) {
            redirect.addFlashAttribute("erro", erro);
        } else {
            redirect.addFlashAttribute("sucesso", sucesso);
        }
    }

    private String urlLista(String busca, String situacao) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(textos().rota());
        String bb = Parametros.busca(busca);
        if (bb != null) {
            b.queryParam("busca", bb);
        }
        FiltroSituacao s = FiltroSituacao.de(situacao);
        if (s != FiltroSituacao.ATIVOS) {
            b.queryParam("situacao", s.getParametro());
        }
        return b.encode().build().toUriString();
    }

    private static String capitalizar(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
