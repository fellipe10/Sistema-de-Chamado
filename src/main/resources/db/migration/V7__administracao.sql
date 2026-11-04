-- ============================================================
-- V7 - Administração (Etapa 5)
-- Parâmetros gerais do sistema, editados pelo admin em /admin/configuracoes.
-- ============================================================

-- ------------------------------------------------------------
-- Parâmetros (chave → valor)
--   ramal_ti                  → ramal da TI (texto livre, ex.: "214")
--   ramal_manutencao          → ramal da Manutenção
--   rodape_publico            → texto do rodapé das telas públicas;
--                               {ramal_ti} e {ramal_manutencao} são trocados pelos ramais
--   fechamento_dias_uteis     → dias úteis para o fechamento automático de chamados RESOLVIDOS
--                               (vazio = usa o application.yml; 0 = desligado)
-- Valor NULL ou vazio = "não preenchido" (o sistema usa o padrão).
-- ------------------------------------------------------------
CREATE TABLE parametro_sistema (
    chave         VARCHAR(60)  PRIMARY KEY,
    valor         VARCHAR(500),
    atualizado_em TIMESTAMP    NOT NULL DEFAULT LOCALTIMESTAMP,
    CONSTRAINT ck_parametro_chave CHECK (chave ~ '^[a-z][a-z0-9_]{1,59}$')
);

INSERT INTO parametro_sistema (chave, valor) VALUES
    ('ramal_ti', NULL),
    ('ramal_manutencao', NULL),
    ('rodape_publico', 'Urgente de verdade? Ligue para a TI (ramal {ramal_ti}) ou para a Manutenção (ramal {ramal_manutencao}).'),
    ('fechamento_dias_uteis', NULL);

-- ------------------------------------------------------------
-- Índices de apoio às telas de administração
-- (contar chamados por setor, local e em andamento por atendente)
-- ------------------------------------------------------------
CREATE INDEX ix_chamado_setor ON chamado (setor_id);
CREATE INDEX ix_chamado_local ON chamado (local_id);
