package br.com.goias.chamados.dominio.cadastro;

/**
 * Cadastro simples com nome e ativo (Setor e Local) — usado pelas telas de administração (Etapa 5),
 * que tratam os dois do mesmo jeito.
 */
public interface ItemNomeado {

    Long getId();

    String getNome();

    void setNome(String nome);

    boolean isAtivo();

    void setAtivo(boolean ativo);
}
