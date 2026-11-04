-- ============================================================
-- V5 - Pausa do SLA no "Aguardando" (Etapa 4)
--
-- aguardando_motivo  SOLICITANTE = "Aguardando o solicitante"            → PAUSA o prazo
--                    TERCEIRO    = "Aguardando peça/fornecedor/terceiro" → prazo continua correndo
--                    nulo quando o chamado não está em AGUARDANDO
-- pausa_inicio       quando a pausa atual começou (só com SOLICITANTE)
-- minutos_pausados   minutos ÚTEIS acumulados em pausas já encerradas
-- ============================================================

ALTER TABLE chamado
    ADD COLUMN aguardando_motivo VARCHAR(20),
    ADD COLUMN pausa_inicio      TIMESTAMP,
    ADD COLUMN minutos_pausados  BIGINT NOT NULL DEFAULT 0;

-- Chamados que já estavam em AGUARDANDO antes desta etapa não pausavam o prazo:
-- ficam como "Aguardando peça/fornecedor/terceiro" (mesmo comportamento de antes).
UPDATE chamado
   SET aguardando_motivo = 'TERCEIRO'
 WHERE status = 'AGUARDANDO';

ALTER TABLE chamado
    ADD CONSTRAINT ck_chamado_aguardando_motivo CHECK (
        aguardando_motivo IN ('SOLICITANTE', 'TERCEIRO')
    ),
    -- Motivo preenchido se, e somente se, o status for AGUARDANDO
    ADD CONSTRAINT ck_chamado_aguardando_status CHECK (
        (status = 'AGUARDANDO') = (aguardando_motivo IS NOT NULL)
    ),
    -- Pausa aberta se, e somente se, estiver aguardando o solicitante
    ADD CONSTRAINT ck_chamado_pausa CHECK (
        (pausa_inicio IS NOT NULL) = (COALESCE(aguardando_motivo, '') = 'SOLICITANTE')
    ),
    ADD CONSTRAINT ck_chamado_minutos_pausados CHECK (minutos_pausados >= 0);

-- Painel: chamados resolvidos e de 1ª resposta por período
CREATE INDEX ix_chamado_area_resolucao ON chamado (area_id, data_resolucao);
CREATE INDEX ix_chamado_area_abertura  ON chamado (area_id, data_abertura);
