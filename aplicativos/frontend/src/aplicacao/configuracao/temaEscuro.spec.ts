/// <reference types="node" />

import { readdirSync, readFileSync } from 'node:fs'
import { extname, join } from 'node:path'

import { describe, expect, it } from 'vitest'

const raizDoFrontend = process.cwd()
const raizDoCodigoFonte = join(raizDoFrontend, 'src')

function listarArquivosVue(diretorio: string): string[] {
  return readdirSync(diretorio, { withFileTypes: true }).flatMap((entrada) => {
    const caminho = join(diretorio, entrada.name)
    if (entrada.isDirectory()) return listarArquivosVue(caminho)
    return extname(entrada.name) === '.vue' ? [caminho] : []
  })
}

describe('contrato do tema escuro', () => {
  it('ativa o tema escuro antes da aplicacao ser carregada', () => {
    const html = readFileSync(join(raizDoFrontend, 'index.html'), 'utf8')

    expect(html).toMatch(/<html[^>]+data-bs-theme="dark"/)
    expect(html).not.toContain('data-bs-theme="light"')
  })

  it('nao permite utilitarios que recriam superficies claras', () => {
    const utilitariosClaros =
      /\b(?:bg-light|btn-light|btn-outline-light|text-bg-light)\b/g
    const ocorrencias = listarArquivosVue(raizDoCodigoFonte).flatMap(
      (arquivo) => {
        const conteudo = readFileSync(arquivo, 'utf8')
        return [...conteudo.matchAll(utilitariosClaros)].map(
          (resultado) =>
            `${arquivo.slice(raizDoFrontend.length + 1)}: ${resultado[0]}`,
        )
      },
    )

    expect(ocorrencias).toEqual([])
  })
})
