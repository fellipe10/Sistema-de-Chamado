# Guia de implantação — Central de Chamados (Goiás Esporte Clube)

Para instalar o sistema num **Windows Server ou num PC da rede interna** que fica ligado o tempo todo.
Siga os passos na ordem. Cada passo diz **o que fazer** e **como conferir** se deu certo.

> Arquivos prontos que este guia usa (estão na pasta `implantacao/` do projeto):
> `chamados-servico.xml` (serviço do Windows), `backup-chamados.bat` e `restaurar-chamados.bat`.

---

## 0. Como vai ficar

| O quê | Onde |
|---|---|
| Programa (`chamados.jar`) + serviço | `D:\chamados\app\` |
| Arquivos anexados aos chamados | `D:\chamados\anexos\` |
| Logs (o "diário" do sistema) | `D:\chamados\logs\` |
| Scripts de backup | `D:\chamados\backup\` |
| Cópias de segurança | `E:\backups\chamados\` (**outro disco** ou pasta da rede) |
| Banco de dados | PostgreSQL 17 no mesmo computador, banco `chamados` |
| Endereço para a equipe | `http://NOME-DO-SERVIDOR:8081` |

Se o servidor não tiver disco `D:`, use `C:\chamados\...` e troque os caminhos em todos os arquivos.

**Antes de começar:** peça à TI um **IP fixo** (ou um nome fixo na rede) para esse computador. Se o IP mudar, ninguém acha o sistema.

---

## 1. Pré-requisitos

### 1.1 Java 17
1. Baixe o **Eclipse Temurin 17 (JDK), Windows x64, arquivo .msi** em https://adoptium.net/temurin/releases/?version=17
2. Na instalação, marque **"Add to PATH"** e **"Set JAVA_HOME variable"**.
3. **Conferir:** abra o Prompt de Comando e digite:
   ```
   java -version
   where java
   ```
   Tem que aparecer `openjdk version "17..."`. **Anote o caminho** que o `where java` mostrar
   (ex.: `C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot\bin\java.exe`) — ele vai no passo 6.

### 1.2 PostgreSQL 17
1. Baixe o instalador do **PostgreSQL 17 para Windows** em https://www.postgresql.org/download/windows/ (instalador da EDB).
2. Durante a instalação:
   - deixe a porta **5432**;
   - crie a senha do usuário **postgres** (o administrador do banco) e **guarde num lugar seguro** (KeePass);
   - pode desmarcar o "Stack Builder".
3. **Conferir:** em "Serviços" do Windows (`services.msc`) deve existir **postgresql-x64-17** em execução.

---

## 2. Criar o banco e o usuário do sistema

O sistema usa um usuário próprio no banco (`chamados`), **não** o `postgres`.

1. Abra o **SQL Shell (psql)** pelo menu Iniciar. Aperte Enter nas perguntas (servidor, banco, porta, usuário) e digite a senha do `postgres`.
2. Cole os comandos abaixo, **trocando a senha** por uma senha forte (só letras e números facilita):
   ```sql
   CREATE ROLE chamados LOGIN PASSWORD 'TROQUE-PELA-SENHA-DO-BANCO';
   CREATE DATABASE chamados OWNER chamados ENCODING 'UTF8' TEMPLATE template0;
   ```
3. **Conferir:** digite `\l` — o banco `chamados` aparece na lista com dono `chamados`. Saia com `\q`.

**As tabelas NÃO precisam ser criadas à mão.** Na primeira vez que o sistema subir, ele cria tudo sozinho
(Flyway, versões V1 a V8) e cadastra as áreas TI e Manutenção, as categorias, setores, locais, prazos, expediente,
feriados de 2026–2027 e o administrador inicial.

---

## 3. Criar as pastas

No Prompt de Comando **como administrador**:
```
mkdir D:\chamados\app
mkdir D:\chamados\anexos
mkdir D:\chamados\logs
mkdir D:\chamados\backup
mkdir E:\backups\chamados
```
Depois, clique com o botão direito em `D:\chamados\app` e em `D:\chamados\backup` → Propriedades → Segurança e
deixe acesso **só para Administradores e SYSTEM** (esses arquivos vão guardar a senha do banco).

---

## 4. Gerar o programa (`chamados.jar`)

Na máquina de desenvolvimento (a do Fellipe), na pasta do projeto:
```
.\gradlew.bat clean bootJar
```
O arquivo sai em `build\libs\chamados.jar`. Copie para `D:\chamados\app\chamados.jar` no servidor.

> O `bootJar` não leva as ferramentas de desenvolvimento (devtools) e nem os usuários de teste:
> eles só existem no perfil **dev**, que nunca é usado no servidor.

---

## 5. Variáveis de ambiente e primeiro teste

O sistema, no perfil de produção (**prod**), lê do Windows 4 informações obrigatórias. Elas **não ficam dentro do programa**:

| Variável | O que é | Exemplo |
|---|---|---|
| `CHAMADOS_DB_URL` | Endereço do banco | `jdbc:postgresql://localhost:5432/chamados` |
| `CHAMADOS_DB_USER` | Usuário do banco | `chamados` |
| `CHAMADOS_DB_PASSWORD` | Senha do usuário do banco (passo 2) | `********` |
| `CHAMADOS_ANEXOS_DIR` | Pasta dos anexos | `D:\chamados\anexos` |
| `CHAMADOS_LOG_DIR` *(opcional)* | Pasta dos logs | `D:\chamados\logs` |

Se faltar alguma, o sistema **não sobe** e escreve no log:
`*** Perfil prod: faltam as variáveis de ambiente ...`

**Teste rápido antes de criar o serviço** (Prompt de Comando, troque a senha):
```
cd /d D:\chamados\app
set CHAMADOS_DB_URL=jdbc:postgresql://localhost:5432/chamados
set CHAMADOS_DB_USER=chamados
set CHAMADOS_DB_PASSWORD=TROQUE-PELA-SENHA-DO-BANCO
set CHAMADOS_ANEXOS_DIR=D:\chamados\anexos
set CHAMADOS_LOG_DIR=D:\chamados\logs
java -jar chamados.jar --spring.profiles.active=prod
```
**Conferir:** em uns 20 segundos aparecem as linhas
- `The following 1 profile is active: "prod"`
- `Successfully applied 8 migrations` (só na primeira vez)
- `Started ChamadosApplication`
- `[prod] Conferência de produção concluída.`

Abra `http://localhost:8081` no navegador do servidor: aparece o formulário "Abrir chamado".
Feche com **Ctrl+C** (o serviço do passo 6 é que vai deixar o sistema ligado de verdade).

---

## 6. Instalar como serviço do Windows (WinSW)

Um **serviço** liga o sistema junto com o Windows, mesmo sem ninguém logado, e o religa sozinho se cair.

### Por que WinSW (e não NSSM)
Os dois funcionam. Escolhi o **WinSW** porque:
- tudo fica num **arquivo XML** (`chamados-servico.xml`) que vai junto com o projeto — dá para ver e conferir o que está configurado;
- as **variáveis de ambiente** ficam nesse XML, só para o serviço (não ficam espalhadas no Windows);
- já faz **reinício automático** se cair e **rotação do log** do console;
- é mantido e de licença livre (MIT). O NSSM não recebe versão nova desde 2017 e alguns antivírus reclamam dele.

### Passo a passo
1. Baixe **`WinSW-x64.exe` da versão 2.12.0** em https://github.com/winsw/winsw/releases/tag/v2.12.0
   (é a versão estável; a 3.0 ainda está em teste).
2. Copie para `D:\chamados\app\` e **renomeie para `chamados-servico.exe`**.
3. Copie `implantacao\chamados-servico.xml` do projeto para `D:\chamados\app\chamados-servico.xml`
   (**mesmo nome do .exe**, só muda a extensão).
4. Abra o XML no Bloco de Notas e ajuste:
   - `<executable>`: o caminho do `java.exe` anotado no passo 1.1;
   - `CHAMADOS_DB_PASSWORD`: a senha do banco;
   - os caminhos `D:\chamados\...`, se usou outros.
5. No Prompt de Comando **como administrador**:
   ```
   cd /d D:\chamados\app
   chamados-servico.exe install
   chamados-servico.exe start
   chamados-servico.exe status
   ```
   O `status` tem que mostrar **Started**.
6. **Conferir:** em `services.msc` aparece **Central de Chamados (Goiás EC)**, Automático (Atraso na Inicialização), Em execução.
   O log fica em `D:\chamados\logs\chamados.log`.

Comandos úteis (sempre como administrador, na pasta `D:\chamados\app`):

| Para | Comando |
|---|---|
| Parar | `chamados-servico.exe stop` |
| Ligar | `chamados-servico.exe start` |
| Reiniciar | `chamados-servico.exe restart` |
| Ver se está ligado | `chamados-servico.exe status` |
| Remover o serviço | `chamados-servico.exe uninstall` |

> Mudou alguma coisa no XML? Rode `stop`, `uninstall`, `install`, `start`.

---

## 7. Liberar a porta 8081 no firewall

Prompt de Comando **como administrador**:
```
netsh advfirewall firewall add rule name="Central de Chamados (8081)" dir=in action=allow protocol=TCP localport=8081 profile=domain,private
```
Isso libera só a rede interna (perfis Domínio e Particular), **não** a internet.

---

## 8. Endereço para a equipe

1. Descubra o nome e o IP do servidor:
   ```
   hostname
   ipconfig
   ```
2. De **outro computador** da rede, abra: `http://NOME-DO-SERVIDOR:8081` (ou `http://IP-DO-SERVIDOR:8081`, ex.: `http://192.168.0.20:8081`).
3. Divulgue:
   - **Para todos os funcionários (abrir chamado):** `http://NOME-DO-SERVIDOR:8081/abrir`
   - **Para a equipe da TI e da Manutenção:** `http://NOME-DO-SERVIDOR:8081/login`
   - No celular da manutenção: o celular precisa estar no **Wi-Fi interno**. Salve o endereço na tela inicial.

---

## 9. Primeiro acesso do administrador

1. Entre em `http://NOME-DO-SERVIDOR:8081/login` com:
   - usuário: `admin`
   - senha: `Goias@Chamados2026`
   > Desde a V8 todo mundo entra pelo **usuário** no formato `nome.sobrenome` (não mais pelo e-mail).
   > O admin inicial é o usuário `admin`, com o e-mail de contato `ti@goiasec.com.br`.
2. O sistema **obriga a trocar a senha** na hora. Crie uma senha forte e guarde no KeePass.
   (Enquanto essa senha da instalação não for trocada, o log avisa: `[prod] O admin inicial ... ainda não trocou a senha`.)
3. Recomendado em seguida, em **Configurações**:
   1. **Usuários** → crie o **seu** usuário com perfil Administrador (ex.: `fellipe.silva`; o e-mail é opcional,
      só para contato). Passe a usar ele.
      Entre com ele e **desative** o usuário `admin` (o sistema não deixa ficar sem nenhum admin ativo).
      Tenha **sempre 2 administradores ativos**: se um esquecer a senha, o outro redefine.
   2. **Configurações gerais** → preencha os **ramais** da TI e da Manutenção (aparecem no rodapé das telas públicas).
   3. **Setores** e **Locais** → revise os cadastros iniciais (eram exemplos): renomeie, desative os que não servem, cadastre os que faltam.
   4. **Usuários** → cadastre os atendentes e gestores. O sistema sugere o usuário a partir do nome
      (ex.: "Rafael Nunes" → `rafael.nunes`; se já existir, `rafael.nunes-2`) e você pode editar.
      Cada um recebe uma senha temporária que aparece **uma vez** na tela. Passe para a pessoa o **usuário** e a senha.
   5. **Feriados** → cadastre os pontos facultativos em que o clube para (Carnaval, Corpus Christi...). Todo fim de ano, cadastre os do ano seguinte (o sistema avisa quando faltar).
   6. **Expediente** e **Prazos (SLA)** → confira se batem com o combinado com cada área.

---

## 10. Backup diário (banco + anexos)

O backup tem duas partes, e **as duas são necessárias**: o banco (chamados, histórico, usuários) e a pasta de anexos (os arquivos).

1. Copie `implantacao\backup-chamados.bat` do projeto para `D:\chamados\backup\`.
2. Abra no Bloco de Notas e ajuste a parte **"Ajuste aqui"**: a senha do banco (`PGPASSWORD`) e o destino (`DESTINO`).
   O ideal é que o destino seja **outro disco** ou **uma pasta de outro computador** (`\\servidor-arquivos\backups\chamados`).
3. **Teste à mão** (Prompt de Comando como administrador):
   ```
   D:\chamados\backup\backup-chamados.bat
   ```
   **Conferir:** apareceu `E:\backups\chamados\banco\chamados_AAAA-MM-DD_HHMM.dump` e a pasta `E:\backups\chamados\anexos`.
   O arquivo `E:\backups\chamados\backup.log` termina com `Backup concluido`.
4. **Agende para todo dia às 23h30** (Prompt como administrador):
   ```
   schtasks /create /tn "Central de Chamados - backup diario" /tr "D:\chamados\backup\backup-chamados.bat" /sc daily /st 23:30 /ru SYSTEM /rl HIGHEST
   ```
   **Conferir:** no **Agendador de Tarefas** aparece a tarefa. Clique com o botão direito → **Executar** e veja o `backup.log`.
5. O script guarda os backups do banco dos **últimos 30 dias** e apaga os mais velhos. Os anexos são copiados sem apagar nada.

**Uma vez por mês:** abra o `backup.log` e veja se todos os dias terminaram com `Backup concluido`.
**A cada 6 meses:** faça um teste de restauração num computador de teste (passo 11) — backup que nunca foi testado não é backup.

---

## 11. Como restaurar um backup

Use quando o banco estragar, o servidor for trocado, ou alguém apagar algo importante.
**Tudo o que foi feito depois do backup escolhido é perdido.**

1. Copie `implantacao\restaurar-chamados.bat` para `D:\chamados\backup\` e ajuste a parte "Ajuste aqui" (caminhos).
2. Escolha o arquivo do dia em `E:\backups\chamados\banco\`.
3. Prompt de Comando **como administrador**:
   ```
   D:\chamados\backup\restaurar-chamados.bat E:\backups\chamados\banco\chamados_2027-03-10_2330.dump
   ```
4. O script pede para digitar **SIM** e a **senha do postgres**. Ele:
   para o serviço → apaga e recria o banco → restaura o backup → copia os anexos de volta → liga o serviço.
5. **Conferir:** entre no sistema e veja se os chamados daquele dia estão lá.

**Servidor novo?** Faça os passos 1 a 3 e 6 a 8 no servidor novo, crie só o usuário `chamados` do passo 2
(o script recria o banco), copie a pasta de backups e rode o passo 11.

---

## 12. Como atualizar para uma versão nova

1. Gere o `chamados.jar` novo (passo 4).
2. No servidor, **faça um backup na hora**: rode `D:\chamados\backup\backup-chamados.bat`.
3. Avise a equipe (o sistema fica fora do ar por ~1 minuto).
4. Prompt como administrador:
   ```
   cd /d D:\chamados\app
   chamados-servico.exe stop
   ren chamados.jar chamados-anterior.jar
   ```
5. Copie o `chamados.jar` novo para `D:\chamados\app\`.
6. ```
   chamados-servico.exe start
   ```
7. **Conferir** no `D:\chamados\logs\chamados.log`:
   - se a versão nova trouxe mudança no banco: `Successfully applied N migrations`; se não: `Schema "public" is up to date`;
   - `Started ChamadosApplication`;
   - entre no sistema e abra um chamado de teste.

> **Atualizando de uma versão anterior ao login por usuário (V8)?** Na primeira subida, cada pessoa ganha um usuário
> tirado do e-mail (a parte antes do @, ex.: `rafael.nunes@goiasec.com.br` → `rafael.nunes`; repetidos ganham `-2`, `-3`)
> e o admin inicial vira `admin`. Antes de liberar, abra **Configurações → Usuários**, confira/ajuste os usuários
> gerados e avise a equipe com qual usuário cada um entra (a senha continua a mesma).

**Deu errado? Voltar para a versão anterior:**
1. `chamados-servico.exe stop`
2. Apague o `chamados.jar` novo e renomeie `chamados-anterior.jar` para `chamados.jar`.
3. **Se a versão nova mudou o banco** (o log mostrou "Successfully applied"), restaure o backup do item 2 (passo 11) — a versão antiga não entende o banco novo.
4. `chamados-servico.exe start`

---

## 13. Problemas comuns

| Sintoma | O que fazer |
|---|---|
| Serviço não liga | Abra `D:\chamados\logs\chamados.log` (e `D:\chamados\logs\servico\`). Procure linhas com `ERROR` ou `***`. |
| Log diz "faltam as variáveis de ambiente" | Confira os `<env>` do `chamados-servico.xml` (passo 6) e reinstale o serviço. |
| Log diz `password authentication failed` | A senha do banco no XML não bate com a do passo 2. |
| Log diz `Port 8081 was already in use` | Outro programa usa a porta. Descubra qual: `netstat -ano \| findstr :8081` (o último número é o PID; veja no Gerenciador de Tarefas). |
| Funciona no servidor mas não nos outros PCs | Firewall (passo 7) ou IP/nome errado (passo 8). Teste com o IP. |
| Anexo dá erro ao enviar | A pasta `D:\chamados\anexos` precisa existir e o serviço precisa poder gravar nela. O sistema nem liga se não conseguir gravar (o log mostra a pasta). |
| Esqueci a senha do admin | Outro administrador entra em **Configurações → Usuários → Redefinir senha**. Por isso é bom ter sempre 2 admins ativos. |
| Prazos estranhos depois de mudar expediente/feriado/SLA | Use o botão **Recalcular prazos** na tela que foi alterada. |

---

## Resumo das portas, contas e senhas (preencha e guarde no KeePass)

| Item | Valor |
|---|---|
| Endereço do sistema | `http://__________:8081` |
| Senha do `postgres` (administrador do PostgreSQL) | |
| Senha do usuário `chamados` do banco | |
| Administradores do sistema (2) | |
| Pasta dos backups | |
