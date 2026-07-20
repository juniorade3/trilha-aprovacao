# Sprint 03 — Consolidação e aceite integral

> Ler primeiro `CONTEXTO-COMUM.md` e os registros das Sprints 01 e 02.

## 1. Objetivo

Fechar a Geração Determinística com fluxo completo, segurança, documentação, acessibilidade, desempenho e regressões.

## 2. Valor entregue

A funcionalidade fica segura para uso diário e pronta para servir de base aos motores futuros.

## 3. Dependências

Sprints 01 e 02 concluídas e mescladas.

## 4. Escopo

- revisar a jornada Prioridades → Configuração → Prévia → Aplicação;
- corrigir mensagens, estados vazios e preservação do contexto;
- consolidar testes de ponta a ponta;
- provar determinismo;
- provar isolamento;
- testar migrations em banco vazio;
- revisar N+1 e índices;
- consolidar OpenAPI e Swagger;
- validar responsividade e teclado;
- remover arquivos temporários;
- atualizar documentação factual;
- executar `make verificar`;
- preparar PR final e relatório.

## 5. Fora do escopo

- nova regra de geração;
- Motor de Evidências;
- Motor de Revisões;
- Motor de Lacunas;
- IA;
- notificações;
- calendário;
- geração em plano ativo.

## 6. Regras de negócio

1. A geração continua opcional.
2. Sair da gaveta sem aplicar não altera o plano.
3. Alterar prioridade ou configuração invalida a prévia visual.
4. Erro preserva prioridades salvas.
5. Manual e ajustado nunca são removidos.
6. A mesma entrada continua produzindo a mesma saída.
7. Nenhuma validação não executada pode ser declarada aprovada.
8. Não deixar workflows, scripts ou artefatos temporários.
9. Não iniciar o módulo seguinte.

## 7. Modelo de domínio

Não criar entidade nova sem falha comprovada.

Revisar:

- prioridade;
- gerador;
- prévia;
- origem;
- justificativa;
- transações;
- consultas entre módulos;
- constraints e índices.

## 8. Backend

- consolidar consulta de elegíveis em uma chamada;
- revisar índices por plano, data e origem;
- adicionar teste integrado do fluxo completo;
- testar base PostgreSQL vazia;
- validar arquitetura modular;
- validar `/v3/api-docs`;
- provar A/B em todas as rotas;
- revisar logs e erros;
- documentar algoritmo e limitações.

## 9. Frontend

- manter a semana selecionada ao abrir e fechar a gaveta;
- voltar etapas sem perder dados salvos;
- indicar prévia desatualizada;
- foco correto ao abrir e fechar modal/gaveta;
- justificativas acessíveis;
- badges com texto, não apenas cor;
- mensagens anunciadas;
- fluxo funcional em 390, 768 e 1280 px;
- testes da jornada com API simulada apenas nos testes.

## 10. Contrato da API

Contrato final esperado:

| Método | Rota |
| --- | --- |
| GET | `/api/v1/planos-semanais/{id}/materias-para-geracao` |
| PUT | `/api/v1/planos-semanais/{id}/prioridades-de-materias` |
| POST | `/api/v1/planos-semanais/{id}/geracao-deterministica/previa` |
| POST | `/api/v1/planos-semanais/{id}/geracao-deterministica` |

Confirmar todos no Swagger com modelos, códigos e tag `Planejamento`.

## 11. Fluxo principal

1. Criar semana.
2. Informar disponibilidades diferentes.
3. Criar um bloco manual.
4. Abrir geração.
5. Definir prioridades.
6. Configurar 50 minutos e revisão de 20.
7. Gerar prévia.
8. Ler justificativas.
9. Aplicar.
10. Editar um gerado.
11. Regenerar.
12. Confirmar preservação.
13. Ativar plano.
14. Confirmar bloqueio de nova geração.
15. Consultar Hoje e executar um bloco sem regressão.

## 12. Critérios de aceite

- Dado o fluxo completo, então funciona sem dados simulados.
- Dado mesma entrada, então a prévia é idêntica.
- Dado disponibilidades diferentes, então nenhum dia excede sua capacidade.
- Dado capacidade suficiente, então busca três matérias.
- Dado capacidade insuficiente, então reduz e explica.
- Dado revisão, então não duplica revisão manual.
- Dado regeneração, então preserva manual e ajustado.
- Dado usuário B, então não acessa recursos de A.
- Dado banco vazio, então todas as migrations sobem.
- Dado OpenAPI, então todas as rotas aparecem.
- Dado 390, 768 e 1280 px, então não há rolagem horizontal obrigatória.
- Dado teclado, então gaveta, prioridades, modal e aplicação são operáveis.
- Dado execução após geração, então Hoje e histórico continuam funcionando.

## 13. Testes obrigatórios

- todos os testes anteriores;
- E2E backend do fluxo;
- integração frontend da jornada;
- Testcontainers em banco vazio;
- arquitetura;
- OpenAPI;
- segurança;
- concorrência;
- regressão total;
- lint, tipos, build, formatação e `npm audit`;
- validação manual documentada.

## 14. Arquivos provavelmente afetados

- testes consolidados;
- documentação da geração;
- OpenAPI;
- índices somente se necessários;
- componentes de geração;
- README principal somente com estado factual.

## 15. Ordem de implementação

- [ ] revisar critérios anteriores;
- [ ] executar fluxo automatizado;
- [ ] revisar consultas e índices;
- [ ] validar Swagger;
- [ ] validar acessibilidade e responsividade;
- [ ] executar `make verificar`;
- [ ] remover temporários;
- [ ] registrar resultados reais;
- [ ] revisar diff e abrir PR.

## 16. Validação final

```bash
docker compose config
docker compose up -d
make verificar
git diff --check
```

Registrar cada item como:

- `APROVADO`;
- `FALHOU`;
- `PARCIAL`;
- `BLOQUEADO`;
- `NÃO EXECUTADO`.

Validar manualmente:

- 390, 768 e 1280 px;
- teclado;
- sessão expirada;
- erro de rede e nova tentativa;
- Swagger;
- usuário A versus B;
- geração, edição, regeneração, ativação e execução.

## 17. Registro de conclusão

```text
STATUS:
ARQUIVOS ALTERADOS:
DECISÕES TOMADAS:
TESTES EXECUTADOS:
VALIDAÇÃO MANUAL:
PENDÊNCIAS:
PRÓXIMA SPRINT: Planejamento 2 concluído; não iniciar Motor de Evidências sem autorização
```
