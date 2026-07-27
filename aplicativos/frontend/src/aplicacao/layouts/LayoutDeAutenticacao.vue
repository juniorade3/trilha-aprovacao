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
        <h2>Transforme o edital em uma trilha possível de acompanhar.</h2>
        <p>
          Organize matérias, conecte conteúdos e enxergue o que merece atenção
          agora.
        </p>
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
