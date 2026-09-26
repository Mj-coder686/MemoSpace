<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from './components/AppShell.vue'
import { UiToastViewport } from './components/ui'

const route = useRoute()
const standalonePage = computed(() => Boolean(route.meta.public || route.meta.admin))
</script>

<template>
  <UiToastViewport />
  <router-view v-if="standalonePage" v-slot="{ Component }">
    <Transition name="route-flow" appear>
      <div :key="route.path" class="route-screen standalone-route-screen">
        <component :is="Component" />
      </div>
    </Transition>
  </router-view>
  <AppShell v-else>
    <router-view v-slot="{ Component }">
      <Transition name="route-flow">
        <div :key="route.path" class="route-screen">
          <component :is="Component" />
        </div>
      </Transition>
    </router-view>
  </AppShell>
</template>
