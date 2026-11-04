@echo off
REM ============================================================
REM  Restaurar a Central de Chamados a partir de um backup
REM  USO (Prompt de Comando como administrador):
REM     restaurar-chamados.bat E:\backups\chamados\banco\chamados_2027-03-10_2330.dump
REM
REM  O que faz:
REM   1. Para o serviço
REM   2. Apaga o banco "chamados" e cria de novo, vazio
REM   3. Restaura o arquivo .dump escolhido
REM   4. Copia de volta os anexos do backup
REM   5. Liga o serviço
REM  Tudo o que foi registrado DEPOIS do backup escolhido é perdido.
REM ============================================================
setlocal

REM ---------- Ajuste aqui (os mesmos valores do backup-chamados.bat) ----------
set "PG_BIN=C:\Program Files\PostgreSQL\17\bin"
set "PGHOST=localhost"
set "PGPORT=5432"
set "BANCO=chamados"
set "DONO=chamados"
set "ANEXOS=D:\chamados\anexos"
set "BACKUP_ANEXOS=E:\backups\chamados\anexos"
set "SERVICO=central-chamados"
REM ----------------------------------------------------------------------------

if "%~1"=="" (
    echo Informe o arquivo .dump. Exemplo:
    echo    restaurar-chamados.bat E:\backups\chamados\banco\chamados_2027-03-10_2330.dump
    exit /b 1
)
if not exist "%~1" (
    echo Arquivo nao encontrado: %~1
    exit /b 1
)

echo.
echo  ATENCAO: o banco "%BANCO%" sera APAGADO e substituido pelo backup:
echo     %~1
echo  Tudo o que foi feito depois desse backup sera perdido.
echo.
set /p CONFIRMA=Digite SIM para continuar:
if /i not "%CONFIRMA%"=="SIM" (
    echo Cancelado.
    exit /b 1
)

echo.
echo Senha do usuario "postgres" (administrador do PostgreSQL), definida na instalacao:
set /p PGPASSWORD=Senha:

echo [1/5] Parando o servico...
net stop %SERVICO%

echo [2/5] Recriando o banco vazio...
"%PG_BIN%\psql.exe" -U postgres -d postgres -c "DROP DATABASE IF EXISTS %BANCO% WITH (FORCE);" || goto erro
"%PG_BIN%\psql.exe" -U postgres -d postgres -c "CREATE DATABASE %BANCO% OWNER %DONO% ENCODING 'UTF8';" || goto erro

echo [3/5] Restaurando o banco...
"%PG_BIN%\pg_restore.exe" -U postgres -d %BANCO% --no-owner --role=%DONO% "%~1" || goto erro

echo [4/5] Copiando os anexos de volta...
robocopy "%BACKUP_ANEXOS%" "%ANEXOS%" /E /R:2 /W:5 /NP /NFL /NDL
if %ERRORLEVEL% GEQ 8 goto erro

echo [5/5] Ligando o servico...
net start %SERVICO%

echo.
echo Restauracao concluida. Abra o sistema no navegador e confira.
endlocal
exit /b 0

:erro
echo.
echo ERRO na restauracao. O servico continua parado. Leia a mensagem acima e peca ajuda antes de tentar de novo.
endlocal
exit /b 1
