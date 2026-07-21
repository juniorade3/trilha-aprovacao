<template>
  <nav class="navegacao-do-planejamento" aria-label="Área de planejamento">
    <RouterLink to="/planejamento/hoje">
      <i class="bi bi-sun" aria-hidden="true"></i>
      Hoje
    </RouterLink>
    <RouterLink to="/planejamento/semana">
      <i class="bi bi-calendar-week" aria-hidden="true"></i>
      Semana
    </RouterLink>
    <RouterLink to="/planejamento/prioridades">
      <i class="bi bi-list-ol" aria-hidden="true"></i>
      Prioridades
    </RouterLink>
    <RouterLink to="/estudos">
      <i class="bi bi-clock-history" aria-hidden="true"></i>
      Histórico
    </RouterLink>
  </nav>
</template>
