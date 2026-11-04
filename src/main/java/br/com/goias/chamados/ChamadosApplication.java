package br.com.goias.chamados;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class ChamadosApplication {

    /** Todas as datas do sistema são gravadas e exibidas no horário de Brasília. */
    public static final String FUSO_HORARIO = "America/Sao_Paulo";

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(FUSO_HORARIO));
        SpringApplication.run(ChamadosApplication.class, args);
    }
}
