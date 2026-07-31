# Implantacao na rede local

Esta composicao publica somente o frontend, por HTTP, na porta
`PORTA_FRONTEND` (padrao `5173`). PostgreSQL e backend nao publicam portas. O
backend e o OpenClaw se encontram pela rede Docker externa
`trilha-aplicacao`.

## Preparacao

Crie a rede e dois arquivos fora do repositorio:

```bash
docker network create --internal trilha-aplicacao
install -d -m 700 "$HOME/.config/trilha-aprovacao"
openssl rand -base64 48 > "$HOME/.config/trilha-aprovacao/senha-banco"
chmod 644 "$HOME/.config/trilha-aprovacao/senha-banco"
```

Crie `$HOME/.config/trilha-aprovacao/segredos-backend.yml`. O segredo do
gateway deve ser o mesmo arquivo/valor configurado no
OpenClaw, e ambos os segredos devem ter pelo menos 32 bytes:

```yaml
trilha:
  importacao-de-edital:
    interpretacao-assistida:
      habilitada: true
      provedor: codex-cli
      modelo: gpt-5.6-sol
      executavel-do-codex: /opt/codex-cli/bin/codex
      codex-home: /home/aplicacao/.codex
      limite-de-paginas-renderizadas: 20
      dpi-da-renderizacao: 144
  automacao:
    habilitada: true
    identificador-do-bot: 123456789
    segredo-de-hash: "substitua-por-um-segredo-aleatorio-com-32-bytes"
    identificador-da-chave-do-gateway: gateway-openclaw
    segredo-do-gateway: "substitua-pelo-segredo-do-gateway-openclaw"
```

A interpretação assistida de editais é opcional. O provedor `codex-cli` usa a
sessão ChatGPT do Codex CLI e não recebe chave de API. Sem a funcionalidade
habilitada e sem uma sessão válida, o parser local e o editor manual continuam
funcionando e o botão de IA permanece indisponível. O PDF não é encaminhado ao
OpenClaw.

Como alternativa operacional, pode-se usar a Responses API. Nesse caso, troque
o provedor e mantenha a chave somente neste arquivo externo:

```yaml
trilha:
  importacao-de-edital:
    interpretacao-assistida:
      habilitada: true
      provedor: responses-api
      chave-da-api: "sk-..."
      modelo: gpt-5.6-sol
```

Esse provedor chama diretamente a Responses API, sem ferramentas e sem
persistência da resposta no provedor (`store: false`).

```bash
chmod 600 "$HOME/.config/trilha-aprovacao/segredos-backend.yml"
export ARQUIVO_SENHA_BANCO="$HOME/.config/trilha-aprovacao/senha-banco"
export ARQUIVO_SEGREDOS_BACKEND="$HOME/.config/trilha-aprovacao/segredos-backend.yml"
```

Os dois arquivos ficam legiveis pelos usuarios internos dos containers. No
host, o diretorio pai permanece `0700`, portanto outras contas nao conseguem
alcanca-los.

Nao ative o perfil Spring `producao`: ele exige cookie `Secure`, que nao e
enviado pelo navegador nesta implantacao HTTP da LAN.

## Login do Codex por codigo

Faça o login somente no host privado que executa a aplicação. Crie um diretório
dedicado, pertencente ao UID `1000` usado pelo backend, e use-o como
`CODEX_HOME` durante o fluxo por código:

```bash
install -d -m 700 "$HOME/.config/trilha-aprovacao/codex"
export DIRETORIO_CREDENCIAIS_CODEX="$HOME/.config/trilha-aprovacao/codex"
test "$(stat -c '%u' "$DIRETORIO_CREDENCIAIS_CODEX")" = 1000
CODEX_HOME="$DIRETORIO_CREDENCIAIS_CODEX" codex login --device-auth
CODEX_HOME="$DIRETORIO_CREDENCIAIS_CODEX" codex login status
```

Conclua no navegador o código exibido pelo CLI. O diretório é montado apenas no
backend em `/home/aplicacao/.codex`; não o adicione ao repositório, não copie
nem mostre `auth.json` e não o compartilhe com o OpenClaw ou outros containers.
O bind precisa permanecer gravável pelo UID `1000` para que o Codex possa
renovar a sessão. Para revogar a sessão, execute
`CODEX_HOME="$DIRETORIO_CREDENCIAIS_CODEX" codex logout` no host confiável.

## Subida e atualizacao

Na raiz do repositorio, use sempre o Compose base junto ao override do Codex.
Assim a variável de credenciais continua opcional para quem sobe apenas a
composição base:

```bash
docker compose \
  -f infraestrutura/implantacao/compose.yaml \
  -f infraestrutura/implantacao/override-codex-cli.yaml \
  up -d --build
```

Suba o OpenClaw com o arquivo original e o override desta implantacao, mantendo
tambem as variaveis obrigatorias descritas em `infraestrutura/openclaw/RUNBOOK.md`:

```bash
docker compose \
  -f infraestrutura/openclaw/compose.yaml \
  -f infraestrutura/implantacao/override-openclaw.yaml \
  up -d --build
```

Acesse `http://IP_DO_SERVIDOR:5173`. O Swagger fica em
`http://IP_DO_SERVIDOR:5173/swagger-ui.html`. Para usar outra porta, defina, por
exemplo, `PORTA_FRONTEND=8088` antes de subir. Consulte o estado e os logs com
os mesmos dois arquivos:

```bash
docker compose \
  -f infraestrutura/implantacao/compose.yaml \
  -f infraestrutura/implantacao/override-codex-cli.yaml \
  ps
docker compose \
  -f infraestrutura/implantacao/compose.yaml \
  -f infraestrutura/implantacao/override-codex-cli.yaml \
  logs -f backend
```
