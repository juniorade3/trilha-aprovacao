# Modelo de dominio

```text
Usuario 1--* Concurso 1--* Edital
                     1--* CargoDoConcurso 1--* Prova 1--* GrupoDeConteudo
Usuario 1--* Materia 1--* TopicoDaMateria
GrupoDeConteudo *--1 MateriaDaProva *--1 Materia
Edital 1--* ItemDoEdital *--1 MateriaDaProva
ItemDoEdital *--* TopicoDaMateria, por MapeamentoDeItemDoEdital
Usuario 1--* MaterialDeEstudo
MaterialDeEstudo *--* TopicoDaMateria, por CoberturaDeTopicoPorMaterial
TopicoDaMateria 1--* RegistroDeEstudo
```

## Invariantes principais

- Um usuario possui no maximo um Concurso ativo; cada concurso possui no maximo um CargoDoConcurso selecionado e um Edital principal.
- Materia e unica por usuario, ignorando caixa e espacos externos. TopicoDaMateria forma uma arvore sem ciclos e com nome unico entre irmaos.
- A Materia relacionada a um GrupoDeConteudo pertence ao mesmo usuario; a mesma materia nao se repete no grupo.
- ItemDoEdital preserva a redacao oficial. Seu mapeamento so pode apontar para TopicoDaMateria da mesma materia.
- Material e topico coberto pertencem ao mesmo usuario. RegistroDeEstudo pode referenciar material apenas se ele cobrir o topico.
- Progresso e uma consulta derivada: topico possui estudo se existir RegistroDeEstudo ativo. Nao existe tabela de Progresso.
