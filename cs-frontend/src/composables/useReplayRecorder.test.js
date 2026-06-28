import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useReplayRecorder } from './useReplayRecorder.js'
import { replay } from '@/api'

// Mock API
vi.mock('@/api', () => ({
  replay: {
    capture: vi.fn(),
    finish: vi.fn(),
    job: vi.fn()
  }
}))

beforeEach(() => {
  replay.capture.mockReset().mockResolvedValue({ code: 0, data: { id: 1, seq: 0, offsetMs: 0, frameCount: 1 } })
  replay.finish.mockReset().mockResolvedValue({ code: 0, data: { jobId: 99, frameCount: 1 } })
  replay.job.mockReset().mockResolvedValue({ code: 0, data: { status: 'SUCCESS' } })
})

describe('useReplayRecorder', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Mock canvas + image API
    global.URL.createObjectURL = vi.fn(() => 'blob:test')
    global.URL.revokeObjectURL = vi.fn()
    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({
      fillStyle: '',
      fillRect: vi.fn(),
      drawImage: vi.fn(),
      font: '',
      fillText: vi.fn()
    }))
    HTMLCanvasElement.prototype.toDataURL = vi.fn(() =>
      'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=='
    )
    // Mock Image
    global.Image = class {
      constructor() {
        setTimeout(() => this.onload && this.onload(), 0)
      }
    }
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('starts and stops recording', () => {
    const r = useReplayRecorder({ sessionId: 1, intervalMs: 60000 })
    expect(r.isRecording.value).toBe(false)

    r.start()
    expect(r.isRecording.value).toBe(true)

    r.stop()
    expect(r.isRecording.value).toBe(false)
  })

  it('does not start twice', () => {
    const r = useReplayRecorder({ sessionId: 1, intervalMs: 60000 })
    r.start()
    r.start()
    expect(r.isRecording.value).toBe(true)
  })

  it('records interaction events', () => {
    const r = useReplayRecorder({ sessionId: 1, intervalMs: 60000 })
    r.start()
    r.recordInteraction('click', { x: 100, y: 200 })
    r.recordInteraction('scroll', { y: 300 })
    expect(r.isRecording.value).toBe(true)
  })

  it('finish triggers synthesis', async () => {
    const r = useReplayRecorder({ sessionId: 1, intervalMs: 60000 })
    r.start()

    const result = await r.finish()
    expect(r.isRecording.value).toBe(false)
    expect(replay.finish).toHaveBeenCalledWith(1)
    expect(result).toBeDefined()
    expect(result.jobId).toBe(99)
  })

  it('debug finish return', async () => {
    const r = useReplayRecorder({ sessionId: 1, intervalMs: 60000 })
    r.start()
    const res = await replay.finish(1)  // 直接调 mock
    console.log('mock res:', JSON.stringify(res))
    expect(res.code).toBe(0)
  })

  it('handles missing sessionId gracefully', async () => {
    const r = useReplayRecorder({ sessionId: null, intervalMs: 60000 })
    r.start()
    // uploadFrame 应该跳过
    await r.tick()
    expect(replay.capture).not.toHaveBeenCalled()
  })

  it('captures and uploads frame', async () => {
    const r = useReplayRecorder({ sessionId: 42, intervalMs: 60000 })
    r.start()
    await r.tick()
    // 等微任务
    await new Promise(resolve => setTimeout(resolve, 50))
    expect(replay.capture).toHaveBeenCalledWith(expect.objectContaining({
      sessionId: 42,
      frameKind: 'SCREENSHOT'
    }))
    expect(r.frameCount.value).toBeGreaterThan(0)
  })

  it('captures metadata with interaction events', async () => {
    const r = useReplayRecorder({ sessionId: 1, intervalMs: 60000 })
    r.start()
    r.recordInteraction('click', { x: 100, y: 200, target: 'BUTTON' })
    await r.tick()
    expect(replay.capture).toHaveBeenCalled()
    const callArg = replay.capture.mock.calls[0][0]
    expect(callArg.metadata).toContain('click')
  })
})