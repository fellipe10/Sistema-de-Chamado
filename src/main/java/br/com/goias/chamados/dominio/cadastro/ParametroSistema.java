package br.com.goias.chamados.dominio.cadastro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Parâmetro geral do sistema (V7): ramais, texto do rodapé público, dias do fechamento automático.
 * Valor nulo ou vazio = "não preenchido" (o sistema usa o padrão).
 * Lido por config/ParametrosSistema (com cache); editado em /admin/configuracoes.
 */
@Entity
@Table(name = "parametro_sistema")
@Getter
@Setter
@NoArgsConstructor
public class ParametroSistema {

    public static final String RAMAL_TI = "ramal_ti";
    public static final String RAMAL_MANUTENCAO = "ramal_manutencao";
    public static final String RODAPE_PUBLICO = "rodape_publico";
    public static final String FECHAMENTO_DIAS_UTEIS = "fechamento_dias_uteis";

    @Id
    @Column(length = 60)
    private String chave;

    @Column(length = 500)
    private String valor;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    public ParametroSistema(String chave, String valor) {
        this.chave = chave;
        this.valor = valor;
    }
}
