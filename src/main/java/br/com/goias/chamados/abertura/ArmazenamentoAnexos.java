package br.com.goias.chamados.abertura;

import br.com.goias.chamados.abertura.ValidadorAnexo.AnexoValidado;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Grava os anexos em disco, numa pasta FORA do projeto (chamados.anexos.diretorio).
 *
 * Organização: {diretorio}/AAAA/MM/{uuid}.{extensao}
 * No banco (anexo.caminho) fica o caminho RELATIVO à pasta base, ex.: "2026/11/3f2a...e1.png",
 * para a pasta poder mudar de lugar sem precisar atualizar o banco.
 */
@Slf4j
@Component
public class ArmazenamentoAnexos {

    private final Path base;

    public ArmazenamentoAnexos(@Value("${chamados.anexos.diretorio}") String diretorio) {
        this.base = Paths.get(diretorio).toAbsolutePath().normalize();
    }

    /** Arquivo já gravado: caminho relativo (vai para o banco) e absoluto (para apagar se der erro). */
    public record ArquivoSalvo(AnexoValidado anexo, String caminhoRelativo, Path caminhoAbsoluto) {
    }

    @PostConstruct
    void prepararPasta() {
        try {
            Files.createDirectories(base);
            log.info("Anexos serão gravados em {}", base);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar a pasta de anexos " + base
                    + ". Confira a propriedade chamados.anexos.diretorio.", e);
        }
    }

    public ArquivoSalvo salvar(AnexoValidado anexo) {
        LocalDate hoje = LocalDate.now();
        String subpasta = String.format("%04d/%02d", hoje.getYear(), hoje.getMonthValue());
        String nomeGerado = UUID.randomUUID() + "." + anexo.extensao();
        Path pasta = base.resolve(subpasta);
        Path destino = pasta.resolve(nomeGerado).normalize();
        if (!destino.startsWith(base)) {
            throw new IllegalStateException("Caminho de anexo fora da pasta base: " + destino);
        }
        try {
            Files.createDirectories(pasta);
            try (InputStream in = anexo.arquivo().getInputStream()) {
                Files.copy(in, destino); // falha se já existir (UUID repetido = praticamente impossível)
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar o anexo " + anexo.nomeOriginal(), e);
        }
        return new ArquivoSalvo(anexo, subpasta + "/" + nomeGerado, destino);
    }

    /** Apaga um arquivo gravado (usado quando a transação do chamado falha). Nunca lança exceção. */
    public void apagar(ArquivoSalvo arquivo) {
        try {
            Files.deleteIfExists(arquivo.caminhoAbsoluto());
        } catch (IOException e) {
            log.error("Não foi possível apagar o anexo órfão {}", arquivo.caminhoAbsoluto(), e);
        }
    }
}
