type GeradorDeChave = () => string

function gerarUuid() {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6]! & 0x0f) | 0x40
  bytes[8] = (bytes[8]! & 0x3f) | 0x80
  const hexadecimal = [...bytes].map((byte) =>
    byte.toString(16).padStart(2, '0'),
  )
  return [
    hexadecimal.slice(0, 4).join(''),
    hexadecimal.slice(4, 6).join(''),
    hexadecimal.slice(6, 8).join(''),
    hexadecimal.slice(8, 10).join(''),
    hexadecimal.slice(10).join(''),
  ].join('-')
}

export class ControleDeTentativaIdempotente {
  private assinatura?: string
  private chave?: string

  constructor(private readonly gerarChave: GeradorDeChave = gerarUuid) {}

  chavePara(dados: unknown) {
    const assinaturaAtual = JSON.stringify(dados)
    if (assinaturaAtual !== this.assinatura) {
      this.assinatura = assinaturaAtual
      this.chave = this.gerarChave()
    }
    return this.chave!
  }

  concluir() {
    this.assinatura = undefined
    this.chave = undefined
  }
}
