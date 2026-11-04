package br.com.goias.chamados.atendimento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CaminhoAnexoTest {

    @TempDir
    Path base;

    @Test
    @DisplayName("Caminho relativo normal fica dentro da pasta base")
    void normal() {
        assertThat(CaminhoAnexo.resolver(base, "2026/11/3f2a.png"))
                .contains(base.toAbsolutePath().normalize().resolve("2026/11/3f2a.png"));
    }

    @Test
    @DisplayName("../ que sai da pasta base é recusado")
    void sobeDiretorio() {
        assertThat(CaminhoAnexo.resolver(base, "../../etc/passwd")).isEmpty();
        assertThat(CaminhoAnexo.resolver(base, "2026/../../fora.png")).isEmpty();
        assertThat(CaminhoAnexo.resolver(base, "2026/11/../../..")).isEmpty();
    }

    @Test
    @DisplayName("../ que continua dentro da pasta base é aceito (normalizado)")
    void sobeMasContinuaDentro() {
        assertThat(CaminhoAnexo.resolver(base, "2026/12/../11/a.png"))
                .contains(base.toAbsolutePath().normalize().resolve("2026/11/a.png"));
    }

    @Test
    @DisplayName("Caminho absoluto, vazio ou a própria pasta base é recusado")
    void invalidos() {
        assertThat(CaminhoAnexo.resolver(base, "/etc/passwd")).isEmpty();
        assertThat(CaminhoAnexo.resolver(base, "\\windows\\win.ini")).isEmpty();
        assertThat(CaminhoAnexo.resolver(base, "")).isEmpty();
        assertThat(CaminhoAnexo.resolver(base, null)).isEmpty();
        assertThat(CaminhoAnexo.resolver(base, ".")).isEmpty();
        assertThat(CaminhoAnexo.resolver(null, "2026/11/a.png")).isEmpty();
    }

    @Test
    @DisplayName("Pasta com nome parecido com a base não engana a verificação")
    void prefixoParecido() {
        String nomeBase = base.getFileName().toString();
        assertThat(CaminhoAnexo.resolver(base, "../" + nomeBase + "-outra/a.png")).isEmpty();
    }

    @Test
    @DisplayName("Nome do download sem pastas, aspas, quebras de linha e caracteres perigosos")
    void nomeSeguro() {
        assertThat(CaminhoAnexo.nomeSeguro("C:\\fotos\\impressora \"nova\".png")).isEqualTo("impressora nova.png");
        assertThat(CaminhoAnexo.nomeSeguro("../../etc/passwd")).isEqualTo("passwd");
        assertThat(CaminhoAnexo.nomeSeguro("relatorio\r\nSet-Cookie: x.pdf")).isEqualTo("relatorioSet-Cookie x.pdf");
        assertThat(CaminhoAnexo.nomeSeguro("orçamento ação.xlsx")).isEqualTo("orçamento ação.xlsx");
        assertThat(CaminhoAnexo.nomeSeguro(".htaccess")).isEqualTo("htaccess");
        assertThat(CaminhoAnexo.nomeSeguro("\"\"")).isEqualTo("anexo");
        assertThat(CaminhoAnexo.nomeSeguro(null)).isEqualTo("anexo");
        String longo = "a".repeat(300) + ".pdf";
        assertThat(CaminhoAnexo.nomeSeguro(longo)).hasSize(150).endsWith(".pdf");
    }
}
