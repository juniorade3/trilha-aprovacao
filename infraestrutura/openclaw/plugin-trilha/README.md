# Plugin Trilha para OpenClaw

Plugin nativo compatível com OpenClaw `2026.7.1`. Atua como adaptador confiável
entre Telegram e integrador interno da Trilha:

- `/conectar <codigo>` cria vínculo antes de o remetente estar autorizado;
- `/confirmar <codigo>`, `CONFIRMAR <codigo>` ou o código isolado confirmam
  operação assistida;
- qualquer outro texto segue o fluxo normal do agente.

Confirmações válidas são terminais: plugin responde sem encaminhar texto ao
modelo. Código malformado iniciado por `/confirmar` ou `CONFIRMAR` recebe erro
fixo, também sem chegar ao modelo.

## Compatibilidade com dispatcher

Plugin registra `inbound_claim` e `before_dispatch`. No OpenClaw `2026.7.1`,
`inbound_claim` global não é chamado para bindings de canal criados por
configuração; ele é despachado somente para bindings runtime pertencentes ao
plugin. `before_dispatch` cobre bindings configurados e termina antes dos
comandos conversacionais e do modelo. Registro duplo é workaround específico
dessa versão.

Somente Telegram, conversa privada e contexto autorizado são aceitos. Remetente
e chat são identidades independentes: `identificadorDoTelegram` vem do remetente;
`identificadorDoChat`, da conversa. Eles podem ser diferentes. Grupo,
identificador inválido, conta ausente ou conta divergente falham fechados.
Comando nativo `/confirmar` usa `requireAuth: true`; `before_dispatch` roda após
admissão do ingress e não recebe `commandAuthorized` nessa versão.

## Configuração

Em `plugins.entries.trilha-aprovacao.config`:

```json
{
  "urlDoIntegrador": "http://integrador:8090",
  "tempoLimiteEmMs": 5000,
  "identificadorDaContaDoBot": "default"
}
```

`identificadorDaContaDoBot` deve coincidir exatamente com `accountId` recebido
do OpenClaw. Conta nomeada nunca usa fallback. URL aceita somente `http` ou
`https`, sem credenciais, query, fragmento ou caminho. Timeout permitido:
`1000` a `15000` ms.

## Vínculo

Plugin envia `POST /v1/vinculos/telegram`, sem autenticação:

```json
{
  "versaoDoContrato": "1",
  "canal": "TELEGRAM",
  "codigoDeVinculo": "23456789AB",
  "identificadorDoTelegram": "123456789",
  "identificadorDoChat": "987654321",
  "identificadorDaContaDoBot": "default"
}
```

Mapeamento:

- `2xx`: vínculo concluído;
- `400`, `404`, `410`, `422`: código recusado;
- `409`: conflito;
- `429`: limite;
- demais status, timeout ou rede: resultado indeterminado.

Corpo de resposta é descartado.

## Confirmação

Plugin envia `POST /v1/operacoes/telegram/confirmacao`:

```json
{
  "versaoDoContrato": "1",
  "canal": "TELEGRAM",
  "codigo": "2345678A",
  "metodo": "TEXTO",
  "identificadorDoTelegram": "123456789",
  "identificadorDoChat": "987654321",
  "identificadorDaContaDoBot": "default",
  "identificadorDoUpdate": "update-10"
}
```

`messageId` vira `identificadorDoUpdate` quando presente. Sem ele, plugin gera
SHA-256 estável sobre material canônico de conta, remetente, chat e código. Hash
não expõe código puro.

Somente dois corpos `2xx` fechados são aceitos:

```json
{
  "codigo": "OPERACAO_APLICADA",
  "recibo": {
    "identificadorDaOperacao": "123e4567-e89b-12d3-a456-426614174000",
    "tipo": "REGISTRAR_ESTUDO",
    "estado": "APLICADA",
    "resultado": {}
  }
}
```

Resposta ao Telegram informa somente UUID do recibo. `resultado` nunca é
repassado.

```json
{
  "codigo": "NOVA_CONFIRMACAO_EXIGIDA",
  "proximoCodigo": "BCDEFGHJ",
  "proximaFrase": "/confirmar BCDEFGHJ"
}
```

Corpo `2xx` malformado, campo extra, código desconhecido ou recibo inconsistente
gera estado indeterminado; nunca afirma aplicação.

Mapeamento:

- `400`: contexto inválido;
- `404`: vínculo ausente;
- `409`, `410`, `422`: operação recusada, expirada ou alterada. O plugin lê
  somente um JSON fechado `{ "codigo": "..." }` do integrador e mapeia a
  allowlist local `EXECUCAO_DO_BLOCO_NAO_ENCONTRADA` para orientar o usuário a
  iniciar o bloco; códigos desconhecidos, corpo malformado ou campos extras
  continuam com a mensagem genérica;
- `429`: limite;
- demais status ou rede: resultado indeterminado;
- timeout: orientação para consultar operação antes de repetir.

Plugin não possui token do bot, token MCP ou segredo HMAC. Logs não incluem
código, corpo, token ou erro bruto.

## Verificação

Host:

```bash
npm run check
```

Runtime e dispatcher da imagem oficial fixada:

```bash
VALIDAR_COM_IMAGEM_OPENCLAW=1 infraestrutura/openclaw/scripts/validar.sh
```

Gate OCI valida mapper real de DM, hook real e dispatcher real. Teste confirma
que `before_dispatch` entrega recibo e não chama `replyResolver`.
