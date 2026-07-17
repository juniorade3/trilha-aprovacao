# Matriz de dependencias e entregaveis

| Capacidade | Backend e banco | Frontend | Teste de aceite | Sprint |
| --- | --- | --- | --- | --- |
| Fundacao | Compose, Flyway, health, contrato de erro base | Vite, layouts e estilos base | banco saudavel e apps iniciam | 1 |
| Autenticacao | Usuario, Spring Security, sessao e CSRF | login, cadastro, guarda, cliente HTTP | usuario nao autenticado e redirecionado; A nao le B | 2 |
| Conteudos | Materia e TopicoDaMateria | listas, detalhe e arvore | unicidade, pai valido e ausencia de ciclos | 3 |
| Concurso | Concurso, Edital, Cargo, Prova, Grupo, MateriaDaProva | wizard e detalhe em arvore | ativacao e selecao unicas; reuso de materia | 4 |
| Edital | ItemDoEdital e Mapeamento | itens e vinculacao | item sem mapa nao soma progresso | 5 |
| Estudos | Material, Cobertura e Registro | materiais e historico | cobertura valida; correcao/cancelamento | 6 |
| Dashboard | consulta agregada e alertas | painel e estado vazio | fatos do A aparecem no B quando topico comum | 7 |
| Consolidacao | OpenAPI, testes de arquitetura e CI | acessibilidade e responsividade | fluxo integral, builds e auditoria | 8 |

## Regras transversais por entrega

- Toda tabela de negocio recebe referencia de propriedade ou e alcançada por uma relacao cuja propriedade e validada na consulta.
- Comandos nunca recebem usuario livre; o contexto autenticado e a unica fonte.
- Listas principais sao paginadas, APIs retornam DTOs e criacoes retornam `201` com `Location`.
- Alteracoes de estado historico usam arquivamento, correcao ou cancelamento; cascatas destrutivas sao proibidas.
- Toda migration chega acompanhada de teste em banco vazio antes de ser usada por uma tela.
- Cada tela tem estados carregando, vazio e erro; todos os formularios possuem labels, foco visivel e mensagens acessiveis.

## Ordem interna de uma capacidade

1. regra de dominio e teste unitario;
2. migration e repositorio/Testcontainers;
3. caso de uso e autorizacao;
4. DTO, controller, erro e teste MVC;
5. cliente HTTP, pagina e teste de componente;
6. fluxo manual curto e atualizacao documental.
