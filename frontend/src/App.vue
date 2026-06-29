<template>
  <!-- 挂载布局组件，侧边栏与内容区由 Layout.vue 控制 -->
  <router-view />
</template>

<style scoped>
.container {
  max-width: 760px;
  margin: 40px auto;
  font-family: Arial, sans-serif;
}

nav {
  margin-bottom: 18px;
  display: flex;
  gap: 14px;
}
</style>
