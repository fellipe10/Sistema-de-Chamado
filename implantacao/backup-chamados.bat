@echo off
REM ============================================================
REM  Backup diário da Central de Chamados
REM  - Banco: pg_dump no formato "custom" (.dump), um arquivo por dia
REM  - Anexos: cópia da pasta D:\chamados\anexos (só o que mudou)
REM  - Apaga backups do banco com mais de 30 dias
REM  Agendado no Agendador de Tarefas (ver claude/guia-implantacao.md, passo 10).
REM
REM  ATENÇÃO: este arquivo guarda a senha do banco. Deixe a pasta D:\chamados\backup
REM  acessível só para administradores.
REM ============================================================
setlocal

REM ---------- Ajuste aqui ----------
set "PG_BIN=C:\Program Files\PostgreSQL\17\bin"
set "PGHOST=localhost"
set "PGPORT=5432"
set "PGUSER=chamados"
set "PGPASSWORD=TROQUE-PELA-SENHA-DO-BANCO"
set "BANCO=chamados"
set "ANEXOS=D:\chamados\anexos"
REM Destino: de preferência OUTRO disco ou uma pasta da rede (ex.: \\servidor-arquivos\backups\chamados)
set "DESTINO=E:\backups\chamados"
set "DIAS_GUARDAR=30"
REM ----------------------------------

for /f %%d in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HHmm"') do set "DATA=%%d"

if not exist "%DESTINO%\banco"  mkdir "%DESTINO%\banco"
if not exist "%DESTINO%\anexos" mkdir "%DESTINO%\anexos"
set "LOG=%DESTINO%\backup.log"

echo.>> "%LOG%"
echo [%DATA%] Iniciando backup >> "%LOG%"

REM 1) Banco
"%PG_BIN%\pg_dump.exe" -F c -Z 6 -d "%BANCO%" -f "%DESTINO%\banco\chamados_%DATA%.dump" 2>> "%LOG%"
if errorlevel 1 (
    echo [%DATA%] ERRO no backup do banco >> "%LOG%"
    exit /b 1
)
echo [%DATA%] Banco OK: chamados_%DATA%.dump >> "%LOG%"

REM 2) Anexos (copia novos e alterados; nunca apaga nada do backup)
robocopy "%ANEXOS%" "%DESTINO%\anexos" /E /XO /R:2 /W:5 /NP /NFL /NDL >> "%LOG%"
if %ERRORLEVEL% GEQ 8 (
    echo [%DATA%] ERRO na copia dos anexos >> "%LOG%"
    exit /b 1
)
echo [%DATA%] Anexos OK >> "%LOG%"

REM 3) Limpeza: backups do banco com mais de %DIAS_GUARDAR% dias
forfiles /p "%DESTINO%\banco" /m chamados_*.dump /d -%DIAS_GUARDAR% /c "cmd /c del @path" 2>nul

echo [%DATA%] Backup concluido >> "%LOG%"
endlocal
exit /b 0
