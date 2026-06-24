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
    <ToastContainer />
  </div>
</template>

<script setup>
import ToastContainer from './components/ToastContainer.vue'
</script>

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
  color: var(--color-text-muted);
}
.app-footer a {
  color: var(--color-text-muted);
  text-decoration: none;
}
.app-footer a:hover {
  text-decoration: underline;
}
</style>
