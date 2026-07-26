#!/usr/bin/env bash
set -Eeuo pipefail

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/biblioteca.sh"

diretorio_de_estado=""
diretorio_de_credenciais=""
vinculo_anterior=""
vinculo_novo=""
bot=""
conta_do_bot="default"
telegram=""
chat=""
agente=""
sessao=""
arquivo_do_token=""
url_mcp=""
modelo="openai/gpt-5.5"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --diretorio-estado) diretorio_de_estado="${2:-}"; shift 2 ;;
    --diretorio-credenciais-mcp) diretorio_de_credenciais="${2:-}"; shift 2 ;;
    --identificador-vinculo-anterior) vinculo_anterior="${2:-}"; shift 2 ;;
    --identificador-vinculo-novo) vinculo_novo="${2:-}"; shift 2 ;;
    --identificador-bot) bot="${2:-}"; shift 2 ;;
    --identificador-conta-bot) conta_do_bot="${2:-}"; shift 2 ;;
    --identificador-telegram) telegram="${2:-}"; shift 2 ;;
    --identificador-chat) chat="${2:-}"; shift 2 ;;
    --identificador-agente) agente="${2:-}"; shift 2 ;;
    --identificador-sessao) sessao="${2:-}"; shift 2 ;;
    --token-mcp-arquivo) arquivo_do_token="${2:-}"; shift 2 ;;
    --url-mcp) url_mcp="${2:-}"; shift 2 ;;
    --modelo) modelo="${2:-}"; shift 2 ;;
    *) falhar "argumento desconhecido: $1" ;;
  esac
done

exigir_argumento --diretorio-estado "${diretorio_de_estado}"
exigir_argumento --diretorio-credenciais-mcp "${diretorio_de_credenciais}"
exigir_argumento --identificador-vinculo-anterior "${vinculo_anterior}"
exigir_argumento --identificador-vinculo-novo "${vinculo_novo}"
exigir_argumento --identificador-bot "${bot}"
validar_identificador_da_conta_do_bot "${conta_do_bot}"
exigir_argumento --identificador-telegram "${telegram}"
exigir_argumento --identificador-chat "${chat}"
exigir_argumento --identificador-agente "${agente}"
exigir_argumento --identificador-sessao "${sessao}"
exigir_argumento --token-mcp-arquivo "${arquivo_do_token}"
exigir_argumento --url-mcp "${url_mcp}"

# O backend nao troca uma credencial dentro do mesmo vinculo: ele revoga o
# vinculo anterior e emite outro. A operacao local espelha essa identidade e
# nunca injeta um token novo no agente antigo.
exec "${diretorio_dos_scripts}/provisionar-vinculo.sh" \
  --diretorio-estado "${diretorio_de_estado}" \
  --diretorio-credenciais-mcp "${diretorio_de_credenciais}" \
  --identificador-vinculo "${vinculo_novo}" \
  --substituir-vinculo "${vinculo_anterior}" \
  --identificador-bot "${bot}" \
  --identificador-conta-bot "${conta_do_bot}" \
  --identificador-telegram "${telegram}" \
  --identificador-chat "${chat}" \
  --identificador-agente "${agente}" \
  --identificador-sessao "${sessao}" \
  --token-mcp-arquivo "${arquivo_do_token}" \
  --url-mcp "${url_mcp}" \
  --modelo "${modelo}"
