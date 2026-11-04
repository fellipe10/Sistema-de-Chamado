package br.com.goias.chamados.login;

import br.com.goias.chamados.dominio.usuario.Perfil;
import br.com.goias.chamados.dominio.usuario.Usuario;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Dados do usuário guardados na sessão depois do login.
 * Nas telas: ${#authentication.principal.nome}, .perfil, .areaNome, .iniciais
 *
 * Depois de trocar a senha, o objeto da sessão é substituído por um novo
 * (ver conta/ContaController), porque este é imutável.
 */
@Getter
public class UsuarioLogado implements UserDetails {

    private final Long id;
    private final String nome;
    private final String email;
    private final String senhaHash;
    private final Perfil perfil;
    private final Long areaId;
    private final String areaNome;
    private final String areaSigla;
    private final boolean ativo;
    /** Precisa criar uma senha nova antes de usar as telas internas. */
    private final boolean trocarSenha;

    public UsuarioLogado(Usuario usuario) {
        this.id = usuario.getId();
        this.nome = usuario.getNome();
        this.email = usuario.getEmail();
        this.senhaHash = usuario.getSenhaHash();
        this.perfil = usuario.getPerfil();
        this.ativo = usuario.isAtivo();
        this.trocarSenha = usuario.isTrocarSenha();
        if (usuario.getArea() != null) {
            this.areaId = usuario.getArea().getId();
            this.areaNome = usuario.getArea().getNome();
            this.areaSigla = usuario.getArea().getSigla();
        } else {
            this.areaId = null;
            this.areaNome = null;
            this.areaSigla = null;
        }
    }

    /** Duas letras para o avatar do menu (ex.: "Carlos Andrade" → "CA"). */
    public String getIniciais() {
        List<String> partes = Arrays.stream(nome.trim().split("\\s+"))
                .filter(p -> !p.isBlank())
                .toList();
        if (partes.isEmpty()) {
            return "?";
        }
        if (partes.size() == 1) {
            return partes.get(0).substring(0, Math.min(2, partes.get(0).length())).toUpperCase(Locale.ROOT);
        }
        String primeira = partes.get(0).substring(0, 1);
        String ultima = partes.get(partes.size() - 1).substring(0, 1);
        return (primeira + ultima).toUpperCase(Locale.ROOT);
    }

    /** "Carla Mendes" → "Carla" (usado nas mensagens da tela). */
    public String getPrimeiroNome() {
        String[] partes = nome.trim().split("\\s+");
        return partes.length == 0 ? nome : partes[0];
    }

    public boolean isAdmin() {
        return perfil == Perfil.ADMIN;
    }

    /** Texto do rodapé do menu (ex.: "Gestor · TI", "Administrador"). */
    public String getDescricaoPerfil() {
        return areaNome == null ? perfil.getDescricao() : perfil.getDescricao() + " · " + areaNome;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(perfil.getAutoridade()));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return ativo;
    }
}
