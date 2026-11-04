-- ============================================================
-- V2 - Dados iniciais
-- Setores e locais são exemplos: ajuste pela tela de Admin (Etapa 5).
-- ============================================================

-- ------------------------------------------------------------
-- Áreas
-- ------------------------------------------------------------
INSERT INTO area (nome, sigla) VALUES
    ('TI',         'TI'),
    ('Manutenção', 'MAN');

-- ------------------------------------------------------------
-- Categorias da TI
-- ------------------------------------------------------------
INSERT INTO categoria (area_id, nome)
SELECT a.id, c.nome
FROM area a
CROSS JOIN (VALUES
    ('Computador ou notebook'),
    ('Impressora e scanner'),
    ('Internet e rede'),
    ('E-mail'),
    ('Sistemas e programas'),
    ('Acesso e senhas'),
    ('Telefonia e ramais'),
    ('Câmeras e CFTV'),
    ('Instalação de equipamento'),
    ('Outros')
) AS c(nome)
WHERE a.sigla = 'TI';

-- ------------------------------------------------------------
-- Categorias da Manutenção
-- ------------------------------------------------------------
INSERT INTO categoria (area_id, nome)
SELECT a.id, c.nome
FROM area a
CROSS JOIN (VALUES
    ('Elétrica'),
    ('Hidráulica'),
    ('Ar-condicionado'),
    ('Alvenaria e pintura'),
    ('Portas, fechaduras e chaves'),
    ('Móveis e marcenaria'),
    ('Serralheria'),
    ('Telhado e infiltração'),
    ('Iluminação'),
    ('Outros')
) AS c(nome)
WHERE a.sigla = 'MAN';

-- ------------------------------------------------------------
-- Setores (exemplos)
-- ------------------------------------------------------------
INSERT INTO setor (nome) VALUES
    ('Administrativo'),
    ('Financeiro'),
    ('Recursos Humanos'),
    ('Comercial e Marketing'),
    ('Comunicação'),
    ('Jurídico'),
    ('Futebol Profissional'),
    ('Categorias de Base'),
    ('Departamento Médico'),
    ('Patrimônio'),
    ('Tecnologia da Informação'),
    ('Manutenção');

-- ------------------------------------------------------------
-- Locais (exemplos)
-- ------------------------------------------------------------
INSERT INTO local (nome) VALUES
    ('Sede administrativa'),
    ('Estádio da Serrinha'),
    ('CT Edmo Pinheiro'),
    ('Alojamento da base'),
    ('Loja oficial'),
    ('Outro local');

-- ------------------------------------------------------------
-- SLA padrão (minutos). "Dia útil" = 480 min (8 h de expediente).
-- Crítica: 30 min / 4 h | Alta: 2 h / 1 dia útil
-- Média: 4 h / 3 dias úteis | Baixa: 1 dia útil / 5 dias úteis
-- ------------------------------------------------------------
INSERT INTO sla_config (area_id, prioridade, minutos_primeira_resposta, minutos_resolucao)
SELECT a.id, s.prioridade, s.primeira, s.resolucao
FROM area a
CROSS JOIN (VALUES
    ('CRITICA',  30,  240),
    ('ALTA',    120,  480),
    ('MEDIA',   240, 1440),
    ('BAIXA',   480, 2400)
) AS s(prioridade, primeira, resolucao)
WHERE a.sigla IN ('TI', 'MAN');

-- ------------------------------------------------------------
-- Usuário administrador inicial
-- E-mail: admin@chamados.local | Senha inicial: Goias@Chamados2026
-- TROCAR A SENHA APÓS O PRIMEIRO ACESSO.
-- ------------------------------------------------------------
INSERT INTO usuario (nome, email, senha_hash, perfil, area_id) VALUES
    ('Administrador',
     'admin@chamados.local',
     '$2a$10$GQZ.aF8VYF3V0KsUaU6sEeR.Vai0KTedMUjVSsCoUey7UajEh6fDG',
     'ADMIN',
     NULL);
