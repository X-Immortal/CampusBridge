<template>
  <div class="app-shell">
    <main class="app-content">
      <router-view />
    </main>
    <footer class="app-footer">
      <a
        href="https://beian.miit.gov.cn/"
        target="_blank"
        rel="noopener"
      >
        苏 ICP备2026035146号
      </a>
    </footer>
  </div>
</template>

<style>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.app-content {
  flex: 1;
}
.app-footer {
  text-align: center;
  padding: 12px 0;
  font-size: 0.82rem;
  color: #9b8f98;
}
.app-footer a {
  color: #9b8f98;
  text-decoration: none;
}
.app-footer a:hover {
  text-decoration: underline;
}
</style>
