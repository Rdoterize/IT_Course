<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, AriaComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'
echarts.use([BarChart, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, AriaComponent, TitleComponent, CanvasRenderer])
const props = defineProps<{ option: EChartsCoreOption; label: string }>()
const emit = defineEmits<{ select: [value: { name: string; value: unknown; seriesName: string; seriesIndex: number; dataIndex: number }] }>()
const element = ref<HTMLDivElement>(); let chart: echarts.ECharts | undefined; let observer: ResizeObserver | undefined
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
function render() { chart?.setOption({ textStyle: { fontFamily: '"Microsoft YaHei", "PingFang SC", sans-serif', fontSize: 12 }, aria: { enabled: true, description: props.label }, animationDuration: 400, ...props.option, ...(reducedMotion.matches ? { animation: false } : {}) }, true) }
function select(params: unknown) { if (!params || typeof params !== 'object') return; const item = params as Record<string, unknown>; if (item.componentType !== 'series') return; emit('select', { name: String(item.name || ''), value: item.value, seriesName: String(item.seriesName || ''), seriesIndex: Number(item.seriesIndex || 0), dataIndex: Number(item.dataIndex || 0) }) }
onMounted(() => { if (!element.value) return; chart = echarts.init(element.value); render(); chart.on('click', select); observer = new ResizeObserver(() => chart?.resize()); observer.observe(element.value); reducedMotion.addEventListener('change', render) })
watch(() => props.option, render, { deep: true })
onBeforeUnmount(() => { observer?.disconnect(); reducedMotion.removeEventListener('change', render); chart?.off('click', select); chart?.dispose() })
</script>
<template><div ref="element" class="chart" role="img" :aria-label="label"></div></template>
