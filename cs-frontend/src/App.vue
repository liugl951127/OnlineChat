<script setup>
import { onMounted } from 'vue'
import { useUserStore } from '@/store/user'

const user = useUserStore()
onMounted(() => user.hydrate())
</script>

<template>
  <el-config-provider>
    <router-view />
  </el-config-provider>
</template>

<style lang="scss">
html, body, #app { height: 100%; margin: 0; }
</style>