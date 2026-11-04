package br.com.goias.chamados.admin.feriados;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * "Ano atual" usado no aviso de feriados.
 *
 * Normalmente é o ano de hoje. Para testar o aviso sem esperar a virada do ano, dá para fingir
 * outro ano em chamados.feriados.ano-de-referencia (ex.: 2027 → avisa se 2028 não tiver feriados).
 * Em produção, deixar vazio.
 */
@Component
public class AnoReferencia {

    private final Integer anoFixo;

    public AnoReferencia(@Value("${chamados.feriados.ano-de-referencia:}") String anoFixo) {
        Integer ano = null;
        if (anoFixo != null && !anoFixo.isBlank()) {
            try {
                ano = Integer.parseInt(anoFixo.strip());
            } catch (NumberFormatException e) {
                ano = null;
            }
        }
        this.anoFixo = ano;
    }

    public int anoAtual() {
        return anoFixo != null ? anoFixo : LocalDate.now().getYear();
    }
}
