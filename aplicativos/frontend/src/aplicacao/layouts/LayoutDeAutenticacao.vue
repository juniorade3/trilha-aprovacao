<template>
  <div class="layout-de-autenticacao min-vh-100">
    <aside class="apresentacao-da-autenticacao" aria-label="Apresentação">
      <RouterLink class="marca-da-autenticacao" to="/login">
        <span class="simbolo-da-marca" aria-hidden="true">
          <i class="bi bi-check2"></i>
        </span>
        <span>Trilha da Aprovação</span>
      </RouterLink>
      <div>
        <p class="rotulo-da-autenticacao">Sua preparação, com clareza</p>
        <h2>Transforme o edital em uma trilha possível de acompanhar</h2>
        <p>
          Organize matérias, conecte conteúdos e enxergue o que merece atenção
          agora.
        </p>
        <ul class="beneficios-da-autenticacao" aria-label="Recursos principais">
          <li>
            <i class="bi bi-calendar2-week" aria-hidden="true"></i>
            <span>
              <strong>Planejamento possível</strong>
              <small>Uma semana que respeita o tempo disponível.</small>
            </span>
          </li>
          <li>
            <i class="bi bi-crosshair" aria-hidden="true"></i>
            <span>
              <strong>Prioridade explicável</strong>
              <small>Decisões apoiadas pelo edital e suas evidências.</small>
            </span>
          </li>
          <li>
            <i class="bi bi-graph-up-arrow" aria-hidden="true"></i>
            <span>
              <strong>Progresso visível</strong>
              <small>Histórico, revisões e cobertura em um só lugar.</small>
            </span>
          </li>
        </ul>
      </div>
      <p class="nota-da-autenticacao">
        <i class="bi bi-shield-check" aria-hidden="true"></i>
        Seus dados ficam separados por conta.
      </p>
    </aside>
    <main class="conteudo-da-autenticacao">
      <RouterLink
        class="marca-da-autenticacao marca-da-autenticacao-movel"
        to="/login"
      >
        <span class="simbolo-da-marca" aria-hidden="true">
          <i class="bi bi-check2"></i>
        </span>
        <span>Trilha da Aprovação</span>
      </RouterLink>
      <RouterView />
    </main>
  </div>
</template>
