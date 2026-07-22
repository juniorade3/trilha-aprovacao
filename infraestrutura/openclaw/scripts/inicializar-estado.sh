#!/usr/bin/env bash
set -Eeuo pipefail

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/biblioteca.sh"

diretorio_de_estado=""
diretorio_de_credenciais=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --diretorio-estado) diretorio_de_estado="${2:-}"; shift 2 ;;
    --diretorio-credenciais-mcp) diretorio_de_credenciais="${2:-}"; shift 2 ;;
    *) falhar "argumento desconhecido: $1" ;;
  esac
done

for comando in jq realpath install stat; do exigir_comando "${comando}"; done
exigir_argumento --diretorio-estado "${diretorio_de_estado}"
exigir_argumento --diretorio-credenciais-mcp "${diretorio_de_credenciais}"
diretorio_de_estado="$(normalizar_diretorio_de_estado "${diretorio_de_estado}")"
diretorio_de_credenciais="$(normalizar_diretorio_de_credenciais "${diretorio_de_credenciais}")"
validar_separacao_dos_diretorios "${diretorio_de_estado}" "${diretorio_de_credenciais}"
inicializar_estado "${diretorio_de_estado}"
inicializar_diretorio_de_credenciais "${diretorio_de_credenciais}"
validar_permissoes_do_estado "${diretorio_de_estado}"
printf 'Estado do OpenClaw inicializado em %s; credenciais MCP em %s.\n' \
  "${diretorio_de_estado}" "${diretorio_de_credenciais}"
