import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../lib/api'
import { useTaskTypeRecommendation } from './useTaskTypeRecommendation'

const types = [{ id: 1, name: 'learning/music', created_at: '', updated_at: '' }]
afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers() })
describe('optional Task Type recommendation', () => {
  it('does not request on opening; debounces changes and ignores late results', async () => {
    vi.useFakeTimers()
    let finish!: (value: { task_type_id: number; confidence: number; reason: string }) => void
    const request = vi.spyOn(api, 'recommendTaskType').mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const { result, rerender } = renderHook(({ name }) => useTaskTypeRecommendation(name, types, null), { initialProps: { name: 'Original' } })
    await act(() => vi.advanceTimersByTimeAsync(600))
    expect(request).not.toHaveBeenCalled()
    rerender({ name: 'Piano' })
    await act(() => vi.advanceTimersByTimeAsync(499))
    expect(request).not.toHaveBeenCalled()
    await act(() => vi.advanceTimersByTimeAsync(1))
    rerender({ name: 'Run' })
    await act(async () => finish({ task_type_id: 1, confidence: .93, reason: 'recommended' }))
    expect(result.current.recommendation).toBeUndefined()
    await act(() => vi.advanceTimersByTimeAsync(500))
    act(() => result.current.markChosen())
    await act(async () => finish({ task_type_id: 1, confidence: .93, reason: 'recommended' }))
    expect(result.current.recommendation).toBeUndefined()
  })
  it('offers without assigning, dismisses, and handles provider failure quietly', async () => {
    vi.useFakeTimers()
    const request = vi.spyOn(api, 'recommendTaskType').mockResolvedValue({ task_type_id: 1, confidence: .8, reason: 'recommended' })
    const { result, rerender } = renderHook(({ name }) => useTaskTypeRecommendation(name, types, null), { initialProps: { name: '' } })
    rerender({ name: 'Piano' })
    await act(() => vi.advanceTimersByTimeAsync(500))
    expect(result.current.recommendation?.id).toBe(1)
    act(() => result.current.dismiss())
    expect(result.current.recommendation).toBeUndefined()
    request.mockRejectedValue(new Error('offline'))
    rerender({ name: 'Guitar' })
    await act(() => vi.advanceTimersByTimeAsync(500))
    expect(result.current.recommendation).toBeUndefined()
    request.mockResolvedValue({ task_type_id: 1, confidence: .9, reason: 'recommended' })
    rerender({ name: 'Piano' })
    await act(() => vi.advanceTimersByTimeAsync(500))
    expect(result.current.recommendation?.id).toBe(1)
  })
})
