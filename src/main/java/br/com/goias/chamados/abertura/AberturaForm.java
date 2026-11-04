package br.com.goias.chamados.abertura;

import br.com.goias.chamados.dominio.chamado.Urgencia;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dados do formulário público "Abrir chamado" (as 3 etapas vão num único POST).
 * Strings chegam já sem espaços nas pontas; campo vazio chega como null (ver AberturaController).
 */
@Getter
@Setter
@NoArgsConstructor
@PeloMenosUmContato
public class AberturaForm {

    public static final int MAX_TITULO = 120;
    public static final int MAX_DESCRICAO = 4000;

    // ---------- Etapa 1: Seus dados ----------

    @NotBlank(message = "Informe seu nome completo.")
    @Size(max = 120, message = "O nome pode ter no máximo 120 caracteres.")
    private String nome;

    @NotNull(message = "Selecione seu setor.")
    private Long setorId;

    @Size(max = 30, message = "A matrícula pode ter no máximo 30 caracteres.")
    private String matricula;

    @Email(message = "Informe um e-mail válido.")
    @Size(max = 150, message = "O e-mail pode ter no máximo 150 caracteres.")
    private String email;

    @Size(max = 20, message = "O telefone pode ter no máximo 20 caracteres.")
    private String telefone;

    // ---------- Etapa 2: O problema ----------

    @NotNull(message = "Escolha a área: TI ou Manutenção.")
    private Long areaId;

    @NotNull(message = "Selecione a categoria.")
    private Long categoriaId;

    @NotNull(message = "Selecione o local.")
    private Long localId;

    @NotBlank(message = "Informe um título.")
    @Size(max = MAX_TITULO, message = "O título pode ter no máximo 120 caracteres.")
    private String titulo;

    @NotBlank(message = "Descreva o problema.")
    @Size(max = MAX_DESCRICAO, message = "A descrição pode ter no máximo 4000 caracteres.")
    private String descricao;

    @NotNull(message = "Escolha a urgência.")
    private Urgencia urgencia = Urgencia.MEDIA;

    /** Opcional. Até 3 arquivos (regras em ValidadorAnexo). */
    private List<MultipartFile> anexos = new ArrayList<>();

    // ---------- Etapa 3: Revisão ----------

    @AssertTrue(message = "Para enviar, marque que concorda com o uso dos seus dados.")
    private boolean consentimento;

    /** Campo de cada etapa — usado para voltar à etapa do primeiro erro. */
    private static final Map<String, Integer> ETAPA_DO_CAMPO = Map.ofEntries(
            Map.entry("nome", 1), Map.entry("setorId", 1), Map.entry("matricula", 1),
            Map.entry("email", 1), Map.entry("telefone", 1),
            Map.entry("areaId", 2), Map.entry("categoriaId", 2), Map.entry("localId", 2),
            Map.entry("titulo", 2), Map.entry("descricao", 2), Map.entry("urgencia", 2),
            Map.entry("anexos", 2),
            Map.entry("consentimento", 3));

    public static int etapaDoCampo(String campo) {
        return ETAPA_DO_CAMPO.getOrDefault(campo, 1);
    }

    /** Arquivos realmente escolhidos (o navegador manda uma parte vazia quando não há anexo). */
    public List<MultipartFile> getAnexosEnviados() {
        if (anexos == null) {
            return List.of();
        }
        return anexos.stream()
                .filter(a -> a != null
                        && (!a.isEmpty() || (a.getOriginalFilename() != null && !a.getOriginalFilename().isBlank())))
                .toList();
    }

    public boolean isTemAnexos() {
        return !getAnexosEnviados().isEmpty();
    }
}
