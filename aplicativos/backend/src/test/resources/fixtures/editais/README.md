# Fixtures de importação

Fixtures autorais, pequenas e sem conteúdo protegido.

- `edital-textual-simples.txt`: um cargo e hierarquia completa.
- `edital-dois-cargos.txt`: seleção explícita e isolamento entre cargos.
- `edital-com-tabela.txt`: item literal contendo colunas textuais.
- `arquivo-invalido.txt`: ordem estrutural inválida.

PDF textual e PDF sem camada de texto são gerados com PDFBox no teste para
garantir PDF válido. Arquivo acima do limite também é gerado em memória, sem
adicionar mais de 10 MiB ao repositório.
