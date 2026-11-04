package br.com.goias.chamados.abertura;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Confere os anexos do formulário público:
 * - no máximo 3 arquivos, cada um com até 10 MB;
 * - extensão permitida (jpg, jpeg, png, pdf, docx, xlsx);
 * - o CONTEÚDO do arquivo é mesmo do tipo da extensão (assinatura dos primeiros bytes;
 *   para docx/xlsx abre o ZIP e procura a estrutura do Word/Excel).
 *
 * O tipo MIME gravado no banco vem desta verificação, não do que o navegador informou.
 */
@Component
public class ValidadorAnexo {

    public static final int MAX_ARQUIVOS = 3;
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private static final Map<String, String> MIME_POR_EXTENSAO = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private static final byte[] ASSINATURA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] ASSINATURA_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] ASSINATURA_PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ASSINATURA_ZIP = {'P', 'K', 0x03, 0x04};

    /** Arquivo aprovado, com o nome limpo e o tipo detectado. */
    public record AnexoValidado(MultipartFile arquivo, String nomeOriginal, String extensao,
                                String tipoMime, long tamanho) {
    }

    /** Anexo recusado; a mensagem é mostrada embaixo do campo. */
    public static class AnexoInvalidoException extends Exception {
        public AnexoInvalidoException(String mensagem) {
            super(mensagem);
        }
    }

    public List<AnexoValidado> validar(List<MultipartFile> arquivos) throws AnexoInvalidoException {
        if (arquivos == null || arquivos.isEmpty()) {
            return List.of();
        }
        if (arquivos.size() > MAX_ARQUIVOS) {
            throw new AnexoInvalidoException("Envie no máximo " + MAX_ARQUIVOS + " arquivos.");
        }
        List<AnexoValidado> aprovados = new ArrayList<>();
        for (MultipartFile arquivo : arquivos) {
            aprovados.add(validarUm(arquivo));
        }
        return aprovados;
    }

    private AnexoValidado validarUm(MultipartFile arquivo) throws AnexoInvalidoException {
        String nome = limparNome(arquivo.getOriginalFilename());
        String extensao = extensao(nome);

        if (!MIME_POR_EXTENSAO.containsKey(extensao)) {
            throw new AnexoInvalidoException("O arquivo \"" + nome
                    + "\" não é de um tipo aceito. Use JPG, PNG, PDF, DOCX ou XLSX.");
        }
        if (arquivo.isEmpty()) {
            throw new AnexoInvalidoException("O arquivo \"" + nome + "\" está vazio.");
        }
        if (arquivo.getSize() > MAX_BYTES) {
            throw new AnexoInvalidoException("O arquivo \"" + nome + "\" passa de 10 MB.");
        }
        if (!conteudoConfere(arquivo, extensao)) {
            throw new AnexoInvalidoException("O conteúdo do arquivo \"" + nome
                    + "\" não corresponde a um ." + extensao + " de verdade.");
        }
        return new AnexoValidado(arquivo, nome, extensao, MIME_POR_EXTENSAO.get(extensao), arquivo.getSize());
    }

    private boolean conteudoConfere(MultipartFile arquivo, String extensao) throws AnexoInvalidoException {
        byte[] inicio;
        try (InputStream in = arquivo.getInputStream()) {
            inicio = in.readNBytes(8);
        } catch (IOException e) {
            throw new AnexoInvalidoException("Não foi possível ler o arquivo \"" + limparNome(arquivo.getOriginalFilename()) + "\".");
        }
        return switch (extensao) {
            case "jpg", "jpeg" -> comecaCom(inicio, ASSINATURA_JPEG);
            case "png" -> comecaCom(inicio, ASSINATURA_PNG);
            case "pdf" -> comecaCom(inicio, ASSINATURA_PDF);
            case "docx" -> comecaCom(inicio, ASSINATURA_ZIP) && zipDoOffice(arquivo, "word/");
            case "xlsx" -> comecaCom(inicio, ASSINATURA_ZIP) && zipDoOffice(arquivo, "xl/");
            default -> false;
        };
    }

    /**
     * docx/xlsx são arquivos ZIP. Abre pelo índice central (sem descompactar o conteúdo)
     * e confere se existe "[Content_Types].xml" e a pasta própria do Word ("word/") ou do Excel ("xl/").
     */
    private boolean zipDoOffice(MultipartFile arquivo, String pastaEsperada) throws AnexoInvalidoException {
        Path temporario = null;
        try {
            temporario = Files.createTempFile("chamados-anexo-", ".zip");
            try (InputStream in = arquivo.getInputStream()) {
                Files.copy(in, temporario, StandardCopyOption.REPLACE_EXISTING);
            }
            try (ZipFile zip = new ZipFile(temporario.toFile())) {
                if (zip.getEntry("[Content_Types].xml") == null) {
                    return false;
                }
                Enumeration<? extends ZipEntry> entradas = zip.entries();
                while (entradas.hasMoreElements()) {
                    if (entradas.nextElement().getName().startsWith(pastaEsperada)) {
                        return true;
                    }
                }
                return false;
            }
        } catch (IOException e) {
            // ZIP corrompido ou não é ZIP
            return false;
        } finally {
            if (temporario != null) {
                try {
                    Files.deleteIfExists(temporario);
                } catch (IOException ignorado) {
                    temporario.toFile().deleteOnExit();
                }
            }
        }
    }

    private static boolean comecaCom(byte[] dados, byte[] assinatura) {
        return dados.length >= assinatura.length
                && Arrays.equals(Arrays.copyOf(dados, assinatura.length), assinatura);
    }

    /** Tira caminho (alguns navegadores mandam "C:\...\foto.jpg"), caracteres de controle e limita a 255. */
    static String limparNome(String nomeOriginal) {
        if (nomeOriginal == null || nomeOriginal.isBlank()) {
            return "arquivo";
        }
        String nome = nomeOriginal.replace('\\', '/');
        nome = nome.substring(nome.lastIndexOf('/') + 1);
        nome = nome.replaceAll("\\p{Cntrl}", "").trim();
        if (nome.isEmpty()) {
            return "arquivo";
        }
        return nome.length() > 255 ? nome.substring(nome.length() - 255) : nome;
    }

    static String extensao(String nome) {
        int ponto = nome.lastIndexOf('.');
        return ponto < 0 ? "" : nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
    }
}
