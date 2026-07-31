export const FUSO_HORARIO_DA_APLICACAO = 'America/Sao_Paulo'

type PartesDaDataHora = {
  ano: number
  mes: number
  dia: number
  hora: number
  minuto: number
}

const formatadorDaDataHora = new Intl.DateTimeFormat('en-US', {
  timeZone: FUSO_HORARIO_DA_APLICACAO,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

function partesDaDataHora(valor: Date | string): PartesDaDataHora {
  const data = valor instanceof Date ? valor : new Date(valor)
  if (Number.isNaN(data.getTime())) throw new Error('Data e horário inválidos.')
  const partes = formatadorDaDataHora.formatToParts(data)
  const numero = (tipo: Intl.DateTimeFormatPartTypes) =>
    Number(partes.find((parte) => parte.type === tipo)?.value)
  return {
    ano: numero('year'),
    mes: numero('month'),
    dia: numero('day'),
    hora: numero('hour'),
    minuto: numero('minute'),
  }
}

function preencher(valor: number) {
  return String(valor).padStart(2, '0')
}

export function dataCivilEmSaoPaulo(valor: Date | string = new Date()) {
  const partes = partesDaDataHora(valor)
  return `${partes.ano}-${preencher(partes.mes)}-${preencher(partes.dia)}`
}

export function dataHoraCivilEmSaoPaulo(valor: Date | string = new Date()) {
  const partes = partesDaDataHora(valor)
  return `${partes.ano}-${preencher(partes.mes)}-${preencher(partes.dia)}T${preencher(partes.hora)}:${preencher(partes.minuto)}`
}

export function instanteDeDataHoraCivilEmSaoPaulo(valor: string) {
  const correspondencia = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(
    valor,
  )
  if (!correspondencia) throw new Error('Informe uma data e horário válidos.')

  const desejado = correspondencia.slice(1).map(Number)
  const desejadoComoUtc = Date.UTC(
    desejado[0]!,
    desejado[1]! - 1,
    desejado[2]!,
    desejado[3]!,
    desejado[4]!,
  )
  let instante = desejadoComoUtc

  for (let tentativa = 0; tentativa < 3; tentativa += 1) {
    const exibido = partesDaDataHora(new Date(instante))
    const exibidoComoUtc = Date.UTC(
      exibido.ano,
      exibido.mes - 1,
      exibido.dia,
      exibido.hora,
      exibido.minuto,
    )
    instante += desejadoComoUtc - exibidoComoUtc
  }

  const resultado = new Date(instante)
  if (dataHoraCivilEmSaoPaulo(resultado) !== valor)
    throw new Error('Informe uma data e horário válidos.')
  return resultado.toISOString()
}

function partesDaDataCivil(valor: string) {
  const correspondencia = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor)
  if (!correspondencia) throw new Error('Data inválida.')
  const partes = correspondencia.slice(1).map(Number)
  const data = new Date(Date.UTC(partes[0]!, partes[1]! - 1, partes[2]!))
  if (
    data.getUTCFullYear() !== partes[0] ||
    data.getUTCMonth() !== partes[1]! - 1 ||
    data.getUTCDate() !== partes[2]
  )
    throw new Error('Data inválida.')
  return data
}

export function adicionarDiasADataCivil(valor: string, quantidade: number) {
  const data = partesDaDataCivil(valor)
  data.setUTCDate(data.getUTCDate() + quantidade)
  return data.toISOString().slice(0, 10)
}

export function inicioDaSemanaCivil(valor = dataCivilEmSaoPaulo()) {
  const data = partesDaDataCivil(valor)
  const diaDaSemana = data.getUTCDay() || 7
  return adicionarDiasADataCivil(valor, 1 - diaDaSemana)
}

export function dataCivilEhSegundaFeira(valor: string) {
  return partesDaDataCivil(valor).getUTCDay() === 1
}
