<template><view class="action-spacer" aria-hidden="true" /><view class="action-bar"><slot /></view></template>
<style scoped>.action-spacer{height:calc(76px + env(safe-area-inset-bottom))}.action-bar{position:fixed;right:0;bottom:0;left:0;z-index:20;display:flex;gap:12px;padding:12px 16px calc(12px + env(safe-area-inset-bottom));background:#fff;border-top:1px solid #e4e8ef}</style>
