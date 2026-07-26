let tokenCsrf: string | undefined

export class ErroDaApi extends Error {
  constructor(
    readonly status: number,
    mensagem: string,
    readonly codigo?: string,
  ) {
    super(mensagem)
  }
}

export async function obterCsrf(): Promise<void> {
  const resposta = await fetch('/api/v1/autenticacao/csrf', {
    credentials: 'include',
  })
  const dados = (await resposta.json()) as { token: string }
  tokenCsrf = dados.token
}

export async function requisitar<T>(
  caminho: string,
  opcoes: RequestInit = {},
): Promise<T> {
  if (opcoes.method && !['GET', 'HEAD'].includes(opcoes.method) && !tokenCsrf)
    await obterCsrf()
  const cabecalhos = new Headers(opcoes.headers)
  cabecalhos.set('Accept', 'application/json')
  const corpoEhFormulario =
    typeof FormData !== 'undefined' && opcoes.body instanceof FormData
  if (opcoes.body && !corpoEhFormulario && !cabecalhos.has('Content-Type'))
    cabecalhos.set('Content-Type', 'application/json')
  if (tokenCsrf) cabecalhos.set('X-XSRF-TOKEN', tokenCsrf)
  const resposta = await fetch(`/api${caminho}`, {
    ...opcoes,
    headers: cabecalhos,
    credentials: 'include',
  })
  if (!resposta.ok) {
    if (
      resposta.status === 401 &&
      caminho !== '/v1/autenticacao/login' &&
      typeof window !== 'undefined'
    ) {
      tokenCsrf = undefined
      window.dispatchEvent(new CustomEvent('sessao-expirada'))
    }
    const erro = (await resposta.json().catch(() => null)) as {
      mensagem?: string
      codigo?: string
    } | null
    throw new ErroDaApi(
      resposta.status,
      erro?.mensagem ?? 'Nao foi possivel concluir a operacao.',
      erro?.codigo,
    )
  }
  return resposta.status === 204
    ? (undefined as T)
    : (resposta.json() as Promise<T>)
}

export function criarRequisicaoCancelavel<T>(
  caminho: string,
  opcoes: RequestInit = {},
): { promessa: Promise<T>; cancelar: () => void } {
  const controle = new AbortController()
  return {
    promessa: requisitar<T>(caminho, {
      ...opcoes,
      signal: controle.signal,
    }),
    cancelar: () => controle.abort(),
  }
}
