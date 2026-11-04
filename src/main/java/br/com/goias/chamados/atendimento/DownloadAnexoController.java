package br.com.goias.chamados.atendimento;

import br.com.goias.chamados.dominio.chamado.Anexo;
import br.com.goias.chamados.login.UsuarioLogado;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Download de anexo pela equipe: GET /atendimento/{id}/anexos/{anexoId}
 *
 * - Usuário de outra área ou anexo de outro chamado → 404.
 * - Arquivo = chamados.anexos.diretorio + anexo.caminho; se sair da pasta base ("../"), 404.
 * - Sempre como download (attachment), com o nome original limpo, o Content-Type gravado no
 *   banco e X-Content-Type-Options: nosniff (o navegador não "adivinha" o tipo).
 */
@Slf4j
@Controller
public class DownloadAnexoController {

    private final AtendimentoService atendimentoService;
    private final Path pastaBase;

    public DownloadAnexoController(AtendimentoService atendimentoService,
                                   @Value("${chamados.anexos.diretorio}") String diretorio) {
        this.atendimentoService = atendimentoService;
        this.pastaBase = Paths.get(diretorio).toAbsolutePath().normalize();
    }

    @GetMapping("/atendimento/{id}/anexos/{anexoId}")
    public ResponseEntity<Resource> baixar(@PathVariable Long id, @PathVariable Long anexoId,
                                           @AuthenticationPrincipal UsuarioLogado usuario) {
        Anexo anexo = atendimentoService.anexoParaDownload(id, anexoId, usuario);

        Path arquivo = CaminhoAnexo.resolver(pastaBase, anexo.getCaminho()).orElseThrow(() -> {
            log.warn("Anexo {} com caminho fora da pasta base: {}", anexo.getId(), anexo.getCaminho());
            return new ResponseStatusException(HttpStatus.NOT_FOUND);
        });
        if (!Files.isRegularFile(arquivo) || !Files.isReadable(arquivo)) {
            log.warn("Arquivo do anexo {} não encontrado em disco: {}", anexo.getId(), arquivo);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        long tamanho;
        try {
            tamanho = Files.size(arquivo);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(CaminhoAnexo.nomeSeguro(anexo.getNomeOriginal()), StandardCharsets.UTF_8)
                .build());
        headers.setContentType(tipo(anexo.getTipoMime()));
        headers.setContentLength(tamanho);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl("private, no-store");
        return new ResponseEntity<>(new FileSystemResource(arquivo), headers, HttpStatus.OK);
    }

    private static MediaType tipo(String tipoMime) {
        try {
            return tipoMime == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(tipoMime);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
