-- ============================================================
-- V3 - Atendimento (Etapa 3)
-- - Regra oficial dos tipos de interação garantida pelo banco
-- - Troca de senha obrigatória no primeiro acesso
-- - Controle de versão do chamado (dois atendentes alterando ao mesmo tempo)
-- ============================================================

-- ------------------------------------------------------------
-- 1) Tipos de interação × campo "interno" (regra oficial)
--    COMENTARIO e ATRIBUICAO       → sempre internos (só a equipe vê)
--    RESPOSTA_SOLICITANTE          → nunca interna (aparece na consulta pública)
--    MUDANCA_STATUS                → livre (o sistema grava como não interna)
--
--    Antes do CHECK, acerta linhas antigas que não seguem a regra
--    (só podem existir se alguém inseriu à mão durante os testes):
--    - COMENTARIO/ATRIBUICAO não internos passam a ser internos;
--    - RESPOSTA_SOLICITANTE marcada como interna vira COMENTARIO interno
--      (assim nada que era "escondido" passa a aparecer para o solicitante).
-- ------------------------------------------------------------
UPDATE chamado_interacao
   SET interno = TRUE
 WHERE tipo IN ('COMENTARIO', 'ATRIBUICAO')
   AND interno = FALSE;

UPDATE chamado_interacao
   SET tipo = 'COMENTARIO'
 WHERE tipo = 'RESPOSTA_SOLICITANTE'
   AND interno = TRUE;

ALTER TABLE chamado_interacao
    ADD CONSTRAINT ck_interacao_tipo_interno CHECK (
        (tipo NOT IN ('COMENTARIO', 'ATRIBUICAO') OR interno = TRUE)
        AND (tipo <> 'RESPOSTA_SOLICITANTE' OR interno = FALSE)
    );

-- ------------------------------------------------------------
-- 2) Troca de senha obrigatória
--    Todo usuário existente (inclusive o admin inicial) fica obrigado a trocar.
-- ------------------------------------------------------------
ALTER TABLE usuario
    ADD COLUMN trocar_senha BOOLEAN NOT NULL DEFAULT TRUE;

-- ------------------------------------------------------------
-- 3) Versão do chamado (@Version no JPA)
--    Cada alteração no chamado soma 1; se duas pessoas alterarem ao mesmo
--    tempo, a segunda recebe o aviso "Este chamado foi alterado por outra pessoa".
-- ------------------------------------------------------------
ALTER TABLE chamado
    ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;

-- ------------------------------------------------------------
-- 4) Índice para a ordenação padrão da fila (prazo de resolução)
-- ------------------------------------------------------------
CREATE INDEX ix_chamado_area_status_prazo ON chamado (area_id, status, prazo_resolucao);
