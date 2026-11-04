package br.com.goias.chamados.atendimento;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Proteções do download de anexo (sem banco, fácil de testar).
 *
 * - O arquivo é montado com pasta base (chamados.anexos.diretorio) + anexo.caminho (relativo).
 *   Se o resultado sair da pasta base (ex.: "../../windows/win.ini") ou o caminho for absoluto,
 *   o download é recusado.
 * - O nome mostrado no download é o nome original "limpo": sem pastas, aspas, quebras de linha
 *   e caracteres de controle.
 */
public final class CaminhoAnexo {

    static final String NOME_PADRAO = "anexo";
    private static final int TAMANHO_MAXIMO_NOME = 150;

    private CaminhoAnexo() {
    }

    /** Caminho final do arquivo, ou vazio se for inválido ou estiver fora da pasta base. */
    public static Optional<Path> resolver(Path pastaBase, String caminhoRelativo) {
        if (pastaBase == null || caminhoRelativo == null || caminhoRelativo.isBlank()) {
            return Optional.empty();
        }
        try {
            Path base = pastaBase.toAbsolutePath().normalize();
            String limpo = caminhoRelativo.strip();
            if (limpo.startsWith("/") || limpo.startsWith("\\")) {
                return Optional.empty();
            }
            Path relativo = Paths.get(limpo);
            if (relativo.isAbsolute() || relativo.getRoot() != null) {
                return Optional.empty();
            }
            Path finalResolvido = base.resolve(relativo).normalize();
            if (!finalResolvido.startsWith(base) || finalResolvido.equals(base)) {
                return Optional.empty();
            }
            return Optional.of(finalResolvido);
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /** "C:\\fotos\\impressora \"nova\".png" → "impressora nova.png". */
    public static String nomeSeguro(String nomeOriginal) {
        if (nomeOriginal == null) {
            return NOME_PADRAO;
        }
        String nome = nomeOriginal;
        int barra = Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\'));
        if (barra >= 0) {
            nome = nome.substring(barra + 1);
        }
        StringBuilder limpo = new StringBuilder();
        nome.codePoints()
                .filter(c -> !Character.isISOControl(c))
                .filter(c -> c != '"' && c != ';' && c != '<' && c != '>' && c != '|' && c != '*' && c != '?' && c != ':')
                .forEach(limpo::appendCodePoint);
        String resultado = limpo.toString().strip();
        while (resultado.startsWith(".")) {
            resultado = resultado.substring(1).strip();
        }
        if (resultado.isEmpty()) {
            return NOME_PADRAO;
        }
        if (resultado.length() > TAMANHO_MAXIMO_NOME) {
            int ponto = resultado.lastIndexOf('.');
            String extensao = ponto > 0 && resultado.length() - ponto <= 10 ? resultado.substring(ponto) : "";
            resultado = resultado.substring(0, TAMANHO_MAXIMO_NOME - extensao.length()) + extensao;
        }
        return resultado;
    }
}
