package br.com.goias.chamados.abertura;

import br.com.goias.chamados.dominio.chamado.Urgencia;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PeloMenosUmContatoTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void criarValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void fecharValidador() {
        fabrica.close();
    }

    @Test
    @DisplayName("Regra pura: precisa de e-mail OU telefone")
    void regraPura() {
        assertThat(PeloMenosUmContato.Validador.temContato("maria@goiasec.com.br", null)).isTrue();
        assertThat(PeloMenosUmContato.Validador.temContato(null, "ramal 214")).isTrue();
        assertThat(PeloMenosUmContato.Validador.temContato("maria@goiasec.com.br", "ramal 214")).isTrue();
        assertThat(PeloMenosUmContato.Validador.temContato(null, null)).isFalse();
        assertThat(PeloMenosUmContato.Validador.temContato("", "")).isFalse();
        assertThat(PeloMenosUmContato.Validador.temContato("   ", " ")).isFalse();
    }

    @Test
    @DisplayName("Formulário sem nenhum contato: erro no campo e-mail")
    void semContato() {
        List<ConstraintViolation<AberturaForm>> erros = errosDeContato(formularioValido(null, null));

        assertThat(erros).hasSize(1);
        assertThat(erros.get(0).getPropertyPath().toString()).isEqualTo("email");
        assertThat(erros.get(0).getMessage()).isEqualTo(PeloMenosUmContato.MENSAGEM);
    }

    @Test
    @DisplayName("Formulário com só e-mail, só telefone ou os dois: sem erro de contato")
    void comContato() {
        assertThat(validador.validate(formularioValido("maria@goiasec.com.br", null))).isEmpty();
        assertThat(validador.validate(formularioValido(null, "(62) 90000-0000"))).isEmpty();
        assertThat(validador.validate(formularioValido("maria@goiasec.com.br", "ramal 214"))).isEmpty();
    }

    @Test
    @DisplayName("Espaços em branco não contam como contato")
    void espacosNaoContam() {
        assertThat(errosDeContato(formularioValido("   ", "  "))).hasSize(1);
    }

    private List<ConstraintViolation<AberturaForm>> errosDeContato(AberturaForm form) {
        return validador.validate(form).stream()
                .filter(v -> v.getConstraintDescriptor().getAnnotation() instanceof PeloMenosUmContato)
                .toList();
    }

    /** Formulário com todos os outros campos certos, para isolar a regra do contato. */
    private static AberturaForm formularioValido(String email, String telefone) {
        AberturaForm form = new AberturaForm();
        form.setNome("Maria da Silva");
        form.setSetorId(1L);
        form.setEmail(email);
        form.setTelefone(telefone);
        form.setAreaId(1L);
        form.setCategoriaId(1L);
        form.setLocalId(1L);
        form.setTitulo("Impressora não imprime");
        form.setDescricao("Desde ontem.");
        form.setUrgencia(Urgencia.MEDIA);
        form.setConsentimento(true);
        return form;
    }
}
