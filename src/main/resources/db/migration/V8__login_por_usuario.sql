-- ============================================================
-- V8 — Login por usuário (nome.sobrenome) em vez do e-mail
-- ============================================================
-- 1. Nova coluna usuario.login (obrigatória, única, sempre minúscula, formato nome.sobrenome).
-- 2. Os usuários que já existem ganham o login a partir da parte antes do @ do e-mail,
--    ajustada ao formato. Duplicados recebem sufixo -2, -3...
-- 3. O administrador inicial (admin@chamados.local) vira login "admin" e e-mail "ti@goiasec.com.br".
-- 4. O e-mail passa a ser OPCIONAL (só contato). Se preenchido, continua único e minúsculo.
--
-- Formato do login (o mesmo de br.com.goias.chamados.dominio.usuario.RegrasLogin):
--   - de 3 a 30 caracteres;
--   - só letras minúsculas sem acento, números, ponto e hífen;
--   - começa com letra;
--   - sem ponto/hífen no fim nem dois seguidos (ex.: "rafael..nunes", "rafael.-nunes", "rafael.").
-- ============================================================

ALTER TABLE usuario ADD COLUMN login VARCHAR(30);

-- ------------------------------------------------------------
-- Administrador inicial (criado na V2)
-- ------------------------------------------------------------
UPDATE usuario SET login = 'admin' WHERE email = 'admin@chamados.local';

-- Só troca o e-mail se nenhum outro usuário já usar ti@goiasec.com.br (o e-mail é único).
UPDATE usuario
   SET email = 'ti@goiasec.com.br'
 WHERE email = 'admin@chamados.local'
   AND NOT EXISTS (SELECT 1 FROM usuario outro WHERE outro.email = 'ti@goiasec.com.br');

-- ------------------------------------------------------------
-- Demais usuários: login a partir do e-mail
-- Ex.: "Rafael.Nunes@goiasec.com.br" → "rafael.nunes"
--      "joao_silva+ti@..."           → "joao.silva.ti"
--      "2024.maria@..."              → "maria"
--      dois "rafael.nunes@..."       → "rafael.nunes" (o mais antigo) e "rafael.nunes-2"
-- ------------------------------------------------------------
DO $$
DECLARE
    u         RECORD;
    base      TEXT;
    candidato TEXT;
    sufixo    TEXT;
    n         INT;
BEGIN
    FOR u IN SELECT id, email FROM usuario WHERE login IS NULL ORDER BY id LOOP
        base := lower(split_part(u.email, '@', 1));
        -- tira os acentos mais comuns (e-mail quase nunca tem, mas por garantia)
        base := translate(base, 'áàâãäéèêëíìîïóòôõöúùûüçñý', 'aaaaaeeeeiiiiooooouuuucny');
        -- qualquer outro caractere (_ + espaço etc.) vira ponto
        base := regexp_replace(base, '[^a-z0-9.-]+', '.', 'g');
        -- pontos/hífens seguidos viram um só (fica o primeiro)
        base := regexp_replace(base, '([.-])[.-]+', '\1', 'g');
        -- precisa começar com letra
        base := regexp_replace(base, '^[^a-z]+', '');
        -- no máximo 30 caracteres e sem ponto/hífen no fim
        base := regexp_replace(left(base, 30), '[.-]+$', '');

        IF base = '' THEN
            base := 'usuario';
        ELSIF length(base) < 3 THEN
            base := 'usuario.' || base;
        END IF;

        candidato := base;
        n := 1;
        WHILE EXISTS (SELECT 1 FROM usuario WHERE login = candidato) LOOP
            n := n + 1;
            sufixo := '-' || n;
            candidato := regexp_replace(left(base, 30 - length(sufixo)), '[.-]+$', '') || sufixo;
        END LOOP;

        UPDATE usuario SET login = candidato WHERE id = u.id;
    END LOOP;
END $$;

-- ------------------------------------------------------------
-- Regras do login no banco
-- ------------------------------------------------------------
ALTER TABLE usuario ALTER COLUMN login SET NOT NULL;

ALTER TABLE usuario ADD CONSTRAINT ck_usuario_login_formato CHECK (
    length(login) BETWEEN 3 AND 30
    AND login ~ '^[a-z][a-z0-9]*([.-][a-z0-9]+)*$'
);

CREATE UNIQUE INDEX uk_usuario_login ON usuario (login);

-- ------------------------------------------------------------
-- E-mail opcional (só contato)
-- O CHECK de minúsculas da V1 continua valendo (NULL passa no CHECK).
-- Texto vazio não é aceito: sem e-mail = NULL.
-- ------------------------------------------------------------
ALTER TABLE usuario ALTER COLUMN email DROP NOT NULL;

ALTER TABLE usuario ADD CONSTRAINT ck_usuario_email_nao_vazio CHECK (email IS NULL OR email <> '');

DROP INDEX uk_usuario_email;
CREATE UNIQUE INDEX uk_usuario_email ON usuario (email) WHERE email IS NOT NULL;

COMMENT ON COLUMN usuario.login IS 'Usuário para entrar no sistema (nome.sobrenome, minúsculo, único).';
COMMENT ON COLUMN usuario.email IS 'E-mail de contato (opcional). Se preenchido: minúsculo e único.';
