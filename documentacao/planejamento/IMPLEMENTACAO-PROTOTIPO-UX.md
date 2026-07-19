# Implementação da referência de UX

## Escopo

A referência visual em `trilha-aprovacao-vue3` foi integrada ao
o frontend Vue 3 existente. A implementação manteve Vue Router, Pinia, `fetch`,
sessão, CSRF e os contratos REST atuais. Não houve alteração no backend, no
banco, nas migrações nem nas regras de domínio.

## Mapeamento de telas

| Referência de UX | Rota Vue | Implementação |
| --- | --- | --- |
| Visão geral | `/dashboard` | Painel da jornada com concurso ativo, cargo, prova, cobertura, tempo semanal, lacunas e atividade recente |
| Meu concurso | `/concursos` | Concurso ativo em destaque, demais objetivos e acesso ao acompanhamento |
| Criação guiada | `/concursos/novo` | Assistente em quatro etapas com retomada segura das operações REST |
| Estrutura e conteúdo programático | `/concursos/:identificador` | Resumo do objetivo, abas de visão e conteúdo e edição contextual em gavetas |
| Conteúdos | `/materias` | Catálogo mestre-detalhe com pesquisa, tópicos hierárquicos e cadastros contextuais |
| Conteúdo selecionado | `/materias/:identificador` | Mesmo catálogo integrado, abrindo diretamente a matéria indicada |
| Materiais | `/materiais` | Biblioteca visual com busca, tipo, ordenação, formulário e gaveta de cobertura |
| Histórico | `/estudos` | Resumo semanal, períodos de 7/30 dias e linha do tempo rastreável |
| Registro rápido | ação global | Modal disponível no cabeçalho, dashboard, histórico e botão flutuante |

As rotas antigas importantes foram preservadas. `/materias/:identificador` e
`/materiais/:identificador` abrem o registro correspondente na experiência
nova, enquanto `/estudos/novo` aciona o registro rápido global.

## Componentes compartilhados

- `CabecalhoDaPagina.vue`;
- `BarraDeProgresso.vue`;
- `EstadoDaPagina.vue`;
- `ModalDaAplicacao.vue`;
- `GavetaLateral.vue`;
- `RegistroRapidoDeEstudo.vue`;
- navegação, aviso de confirmação e ação flutuante em `LayoutPrincipal.vue`.

Os componentes usam os estilos Bootstrap já instalados e a identidade visual
marfim, azul-noite, verde-água e âmbar definida em `principal.scss`.

## Integrações reutilizadas

- sessão e CSRF em `clienteHttp.ts`;
- dashboard derivado em `/v1/dashboard`;
- concursos, editais, cargos, provas, grupos e matérias da prova;
- itens oficiais do edital e mapeamentos;
- catálogo pessoal de matérias e tópicos;
- materiais, coberturas e registros de estudo;
- correção e cancelamento de estudos sem exclusão do histórico.

O assistente de concurso coordena as chamadas existentes na camada Vue. Ele
mantém em memória as etapas REST já concluídas durante a tentativa atual para
não repeti-las caso uma operação posterior falhe.

## Dados derivados

- a cobertura do dashboard é calculada com tópicos exigidos e tópicos com
  estudo;
- a etapa atual da jornada é uma apresentação derivada, não uma entidade;
- a cobertura de uma matéria usa registros de estudo ativos;
- tempo, dias, matérias e ritmo semanal usam registros ativos da semana atual;
- materiais disponíveis no registro rápido são filtrados pelas coberturas reais
  do tópico selecionado.

Para não truncar catálogos extensos sem criar endpoints, o frontend percorre as
páginas existentes ao preparar matérias, tópicos, materiais e estudos usados em
seletores e medidas agregadas.

## Divergências justificadas

- meta semanal planejada, comparativo mensal e quantidade de questões resolvidas
  foram omitidos porque não existem nos contratos atuais;
- a referência possui tipos e ilustrações de materiais simulados; a biblioteca
  exibe somente `AULA`, `PDF` e `OUTRO`, que são os tipos reais do domínio;
- a criação guiada não vincula matérias automaticamente: essa escolha depende do
  catálogo pessoal e permanece na edição contextual do concurso;
- as quatro etapas do concurso e da jornada são organização visual; nenhuma
  entidade artificial de progresso foi criada;
- textos e números demonstrativos do protótipo foram substituídos por respostas
  da API ou cálculos legítimos.

## Validação

- navegação autenticada e redirecionamento ao login;
- dashboard com e sem concurso ativo;
- sucesso, falha, carregamento e estados vazios nos testes de componentes;
- sequência REST da criação guiada;
- restrição de cobertura no registro de estudo;
- correção e cancelamento rastreáveis;
- modal por teclado, incluindo fechamento com `Escape`;
- larguras de 390 px, 768 px e 1280 px sem estouro horizontal;
- retorno ao topo ao navegar, preservando a posição salva apenas no histórico do
  navegador;
- `prefers-reduced-motion`;
- tipos, lint, testes, build, formatação e auditoria de dependências.

## Continuidade possível

Indicadores de metas, questões e comparativos temporais podem ser adicionados no
futuro quando houver conceitos e contratos próprios no domínio. Eles não são
necessários para os fluxos entregues nesta implementação.
