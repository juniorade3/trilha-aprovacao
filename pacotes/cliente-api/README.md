# Cliente API

Nenhum cliente gerado foi criado nesta reconstrução. Existe somente um
consumidor, o frontend Vue, e ele usa o cliente `fetch` central em
`aplicativos/frontend/src/compartilhado/api/clienteHttp.ts`.

Gerar e manter outro pacote agora duplicaria tipos e ciclo de versao sem uso
real. A OpenAPI consolidada em `/v3/api-docs` permite introduzir este pacote no
futuro quando surgir um segundo consumidor.
