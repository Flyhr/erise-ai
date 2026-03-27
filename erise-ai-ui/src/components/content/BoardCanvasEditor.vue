<template>
  <div class="board-editor">
    <div class="board-toolbar">
      <el-color-picker v-model="strokeColor" :disabled="readonly" @change="syncToolbar" />
      <el-slider v-model="strokeWidth" :disabled="readonly" :min="2" :max="18" :show-tooltip="false" style="width: 180px" />
      <el-button plain :disabled="readonly" @click="undo">撤销</el-button>
      <el-button plain :disabled="readonly" @click="clearBoard">清空</el-button>
    </div>

    <div class="board-surface">
      <canvas
        ref="canvasRef"
        class="board-canvas"
        width="960"
        height="540"
        @pointerdown="startDraw"
        @pointermove="draw"
        @pointerup="finishDraw"
        @pointerleave="finishDraw"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

interface Point {
  x: number
  y: number
}

interface Stroke {
  color: string
  width: number
  points: Point[]
}

interface BoardModel {
  width: number
  height: number
  background: string
  strokes: Stroke[]
}

const props = defineProps<{
  modelValue: BoardModel
  readonly?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: BoardModel]
}>()

const canvasRef = ref<HTMLCanvasElement>()
const strokeColor = ref('#14532d')
const strokeWidth = ref(4)
const drawing = ref(false)
let activeStroke: Stroke | null = null

const cloneModel = (value: BoardModel): BoardModel => ({
  width: value.width,
  height: value.height,
  background: value.background,
  strokes: value.strokes.map((stroke) => ({
    color: stroke.color,
    width: stroke.width,
    points: stroke.points.map((point) => ({ ...point })),
  })),
})

const emitChange = (value: BoardModel) => emit('update:modelValue', cloneModel(value))

const redraw = async () => {
  await nextTick()
  const canvas = canvasRef.value
  if (!canvas) return
  const context = canvas.getContext('2d')
  if (!context) return
  context.clearRect(0, 0, canvas.width, canvas.height)
  context.fillStyle = props.modelValue.background || '#ffffff'
  context.fillRect(0, 0, canvas.width, canvas.height)
  context.strokeStyle = 'rgba(148, 163, 184, 0.12)'
  context.lineWidth = 1
  for (let x = 0; x <= canvas.width; x += 24) {
    context.beginPath()
    context.moveTo(x, 0)
    context.lineTo(x, canvas.height)
    context.stroke()
  }
  for (let y = 0; y <= canvas.height; y += 24) {
    context.beginPath()
    context.moveTo(0, y)
    context.lineTo(canvas.width, y)
    context.stroke()
  }
  props.modelValue.strokes.forEach((stroke) => {
    if (stroke.points.length < 2) return
    context.beginPath()
    context.strokeStyle = stroke.color
    context.lineWidth = stroke.width
    context.lineCap = 'round'
    context.lineJoin = 'round'
    context.moveTo(stroke.points[0].x, stroke.points[0].y)
    stroke.points.slice(1).forEach((point) => {
      context.lineTo(point.x, point.y)
    })
    context.stroke()
  })
}

const readPoint = (event: PointerEvent): Point | null => {
  const canvas = canvasRef.value
  if (!canvas) return null
  const rect = canvas.getBoundingClientRect()
  return {
    x: ((event.clientX - rect.left) / rect.width) * canvas.width,
    y: ((event.clientY - rect.top) / rect.height) * canvas.height,
  }
}

const syncToolbar = () => {
  if (!strokeColor.value) {
    strokeColor.value = '#14532d'
  }
}

const startDraw = (event: PointerEvent) => {
  if (props.readonly) return
  const point = readPoint(event)
  if (!point) return
  drawing.value = true
  activeStroke = {
    color: strokeColor.value,
    width: strokeWidth.value,
    points: [point],
  }
  emitChange({
    ...cloneModel(props.modelValue),
    strokes: [...props.modelValue.strokes, activeStroke],
  })
}

const draw = (event: PointerEvent) => {
  if (!drawing.value || props.readonly || !activeStroke) return
  const point = readPoint(event)
  if (!point) return
  const next = cloneModel(props.modelValue)
  const latest = next.strokes[next.strokes.length - 1]
  latest.points.push(point)
  emitChange(next)
}

const finishDraw = () => {
  drawing.value = false
  activeStroke = null
}

const undo = () => {
  if (props.readonly || props.modelValue.strokes.length === 0) return
  const next = cloneModel(props.modelValue)
  next.strokes.pop()
  emitChange(next)
}

const clearBoard = () => {
  if (props.readonly) return
  emitChange({
    ...cloneModel(props.modelValue),
    strokes: [],
  })
}

watch(() => props.modelValue, redraw, { deep: true, immediate: true })
</script>

<style scoped>
.board-editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.board-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.board-surface {
  border-radius: 24px;
  overflow: hidden;
  border: 1px solid var(--line);
  background: #fff;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.board-canvas {
  width: 100%;
  display: block;
  touch-action: none;
  cursor: crosshair;
}
</style>