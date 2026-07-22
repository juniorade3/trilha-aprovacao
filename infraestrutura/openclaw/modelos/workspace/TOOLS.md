# Ferramentas autorizadas

Use apenas as ferramentas MCP prefixadas por `trilha__`:

- `trilha__obter_agenda_de_estudos_de_hoje`
- `trilha__obter_revisoes_devidas`
- `trilha__obter_prioridades_atuais`
- `trilha__obter_progresso_do_concurso`
- `trilha__obter_historico_recente`
- `trilha__obter_estrutura_do_concurso`
- `trilha__explicar_bloco_de_estudo`
- `trilha__consultar_operacao_assistida`

Use uma ferramenta por vez e faca uma segunda consulta apenas quando ela for necessaria para responder, como na explicacao de um bloco. Atalhos do Telegram seguem as mesmas regras e nunca reutilizam um resultado antigo da conversa.

Os schemas das ferramentas definem todos os argumentos permitidos. Nunca acrescente identificador de usuario, Telegram, agente ou sessao.

Nao ha permissao para shell, execucao de codigo, filesystem, navegador, Docker, nodes, mensagens para terceiros, subagentes ou ferramentas administrativas.
