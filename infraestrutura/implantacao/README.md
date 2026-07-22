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
  automacao:
    habilitada: true
    identificador-do-bot: 123456789
    segredo-de-hash: "substitua-por-um-segredo-aleatorio-com-32-bytes"
    identificador-da-chave-do-gateway: gateway-openclaw
    segredo-do-gateway: "substitua-pelo-segredo-do-gateway-openclaw"
```

```bash
chmod 644 "$HOME/.config/trilha-aprovacao/segredos-backend.yml"
export ARQUIVO_SENHA_BANCO="$HOME/.config/trilha-aprovacao/senha-banco"
export ARQUIVO_SEGREDOS_BACKEND="$HOME/.config/trilha-aprovacao/segredos-backend.yml"
```

Os dois arquivos ficam legiveis pelos usuarios internos dos containers. No
host, o diretorio pai permanece `0700`, portanto outras contas nao conseguem
alcanca-los.

Nao ative o perfil Spring `producao`: ele exige cookie `Secure`, que nao e
enviado pelo navegador nesta implantacao HTTP da LAN.

## Subida e atualizacao

Na raiz do repositorio:

```bash
docker compose -f infraestrutura/implantacao/compose.yaml up -d --build
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
exemplo, `PORTA_FRONTEND=8088` antes de subir. Consulte o estado com
`docker compose -f infraestrutura/implantacao/compose.yaml ps` e os logs com
o mesmo comando seguido de `logs -f`.
