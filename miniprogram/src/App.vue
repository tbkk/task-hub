<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
onLaunch(() => undefined)
</script>

<style lang="scss">
@use './styles/tokens.scss' as *;
page { min-height: 100%; background: $color-background; color: $color-text; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; }
view, text { box-sizing: border-box; }
</style>
