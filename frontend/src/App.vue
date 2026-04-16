<template>
  <div class="app-shell">
    <header class="header">
      <h1>CampusBridge</h1>
      <p>校园互助服务平台</p>
    </header>
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
:global(body) {
  margin: 0;
  font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  background: #f4f7fb;
  color: #1e293b;
}

.app-shell {
  min-height: 100vh;
}

.header {
  padding: 2.5rem 1.5rem 1rem;
  text-align: center;
}

.header h1 {
  margin: 0;
  font-size: 2rem;
}

.header p {
  margin: 0.5rem 0 0;
  color: #475569;
}

.main-content {
  padding: 0 1.5rem 2rem;
}
</style>
