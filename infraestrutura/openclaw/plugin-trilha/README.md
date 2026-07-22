# Plugin Trilha para OpenClaw

Plugin nativo compatível com OpenClaw `2026.7.1`. Ele registra
`/conectar <codigo>` com `requireAuth: false`, porque o vínculo ocorre antes de o
Telegram fazer parte da lista autorizada. O manipulador aceita apenas o canal
`telegram` em conversa privada cujo remetente, origem e destino coincidam com o
mesmo identificador numérico.

O plugin não possui token do bot, token MCP nem segredo HMAC. Também não registra
o código em log e não usa o corpo retornado pelo integrador. Toda resposta enviada
ao Telegram é uma mensagem fixa escolhida apenas pelo status HTTP.

## Configuração

Em `plugins.entries.trilha-aprovacao.config`:

```json
{
  "urlDoIntegrador": "http://integrador:8090",
  "tempoLimiteEmMs": 5000
}
```

A URL aceita somente `http` ou `https`, sem credenciais, query, fragmento ou
caminho. O endpoint final é sempre `/v1/vinculos/telegram`.

## Contrato esperado do integrador

O plugin envia `POST /v1/vinculos/telegram`, `Content-Type: application/json`,
sem cabeçalho de autenticação:

```json
{
  "versaoDoContrato": "1",
  "canal": "TELEGRAM",
  "codigoDeVinculo": "23456789AB",
  "identificadorDoTelegram": "123456789",
  "identificadorDoChat": "123456789",
  "identificadorDaContaDoBot": "principal"
}
```

Os identificadores permanecem como texto para não perder precisão. A chamada
deve permanecer restrita à rede interna entre OpenClaw e integrador. Cabe ao
integrador:

- conhecer o bot configurado e autenticar/assinar chamadas ao backend da Trilha;
- trocar o código de uso único, provisionar agente e sessão e registrar o
  provisionamento de modo idempotente;
- aplicar rate limit e deduplicação;
- nunca devolver credenciais ao plugin como requisito de funcionamento.

Mapeamento de status usado pelo plugin:

- `2xx`: vínculo concluído;
- `400`, `404`, `410` ou `422`: código inválido, expirado ou consumido;
- `409`: conflito de vínculo ou código já consumido;
- `429`: excesso de tentativas;
- demais status, timeout ou erro de rede: resultado indeterminado, sem afirmar
  sucesso nem ausência de mutação; uma nova tentativa usa a idempotência do
  integrador para consultar ou concluir o mesmo vínculo com segurança.

O corpo da resposta é deliberadamente descartado. Portanto, detalhes operacionais
devem ficar nos logs protegidos do integrador, correlacionados sem armazenar o
código puro.

## Verificação

```bash
npm run check
```

O comando executa `node --check index.js` e `node --test`, sem instalar
dependências adicionais.
