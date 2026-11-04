package br.com.goias.chamados.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca os controllers das telas internas (layout com menu lateral).
 * Para eles, o model recebe automaticamente o "totalFila" (contador do menu),
 * calculado em atendimento/ContadorFilaAdvice.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TelaInterna {
}
