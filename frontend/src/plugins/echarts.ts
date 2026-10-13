/**
 * ECharts entry with only the chart types and components this project uses.
 *
 * Importing `echarts` pulls in every chart type (~1 MB minified). Importing
 * `echarts/core` and registering the needed pieces keeps the statistics routes
 * small while behaving exactly like the full build.
 *
 * Used charts: radar (知识点掌握度), line (成绩趋势), bar (成绩分布).
 * When a new chart type is needed, add it to the `use()` list below.
 */
import * as echarts from 'echarts/core'
import { BarChart, LineChart, RadarChart } from 'echarts/charts'
import { GridComponent, RadarComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([BarChart, LineChart, RadarChart, GridComponent, RadarComponent, TooltipComponent, CanvasRenderer])

export type EChartsInstance = ReturnType<typeof echarts.init>

export default echarts
