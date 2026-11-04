package br.com.goias.chamados.dominio.usuario;

import br.com.goias.chamados.dominio.area.Area;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Locale;

/**
 * Usuário com login: atendente, gestor ou admin.
 * Atendente e gestor sempre têm área; admin pode não ter.
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    /**
     * Usuário para entrar no sistema (V8): nome.sobrenome, sempre minúsculo e único.
     * Formato em {@link RegrasLogin}; o banco tem o mesmo CHECK.
     */
    @Column(nullable = false, length = RegrasLogin.MAXIMO)
    private String login;

    /** E-mail de contato, OPCIONAL (V8). Se preenchido: minúsculo e único. Vazio é gravado como null. */
    @Column(length = 150)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private Area area;

    @Column(nullable = false)
    private boolean ativo = true;

    /**
     * true = no próximo acesso o usuário é obrigado a criar uma senha nova
     * antes de usar qualquer tela interna (V3; o admin inicial começa com true).
     */
    @Column(name = "trocar_senha", nullable = false)
    private boolean trocarSenha = true;

    @PrePersist
    @PreUpdate
    void normalizar() {
        if (login != null) {
            login = RegrasLogin.normalizar(login);
        }
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
            if (email.isEmpty()) {
                email = null;
            }
        }
    }
}
