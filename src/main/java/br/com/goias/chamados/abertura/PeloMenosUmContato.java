package br.com.goias.chamados.abertura;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Regra do formulário público: a pessoa precisa informar pelo menos um contato
 * (e-mail OU telefone/WhatsApp/ramal). O erro aparece embaixo do campo e-mail.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PeloMenosUmContato.Validador.class)
public @interface PeloMenosUmContato {

    String MENSAGEM = "Informe pelo menos um contato: e-mail ou telefone.";

    String message() default MENSAGEM;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<PeloMenosUmContato, AberturaForm> {

        @Override
        public boolean isValid(AberturaForm form, ConstraintValidatorContext contexto) {
            if (form == null || temContato(form.getEmail(), form.getTelefone())) {
                return true;
            }
            contexto.disableDefaultConstraintViolation();
            contexto.buildConstraintViolationWithTemplate(contexto.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("email")
                    .addConstraintViolation();
            return false;
        }

        /** Regra pura, sem depender do formulário (facilita o teste). */
        public static boolean temContato(String email, String telefone) {
            return naoVazio(email) || naoVazio(telefone);
        }

        private static boolean naoVazio(String valor) {
            return valor != null && !valor.isBlank();
        }
    }
}
