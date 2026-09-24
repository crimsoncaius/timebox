import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../lib/api'
import { useTaskTypeRecommendation } from './useTaskTypeRecommendation'

const types = [{ id: 1, name: 'learning/music', created_at: '', updated_at: '' }, { id: 2, name: 'work', created_at: '', updated_at: '' }]
const reply = { task_type_id: 1, confidence: .9, reason: 'recommended' }
afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers() })
describe('picker Task Type recommendation', () => {
  it('uses all context in one debounced request on opening, including classified work', async () => {
    vi.useFakeTimers()
    const request = vi.spyOn(api, 'recommendTaskType').mockResolvedValue(reply)
    const { result, rerender } = renderHook(({ open, query }) => useTaskTypeRecommendation('Scales', types, 2, open, query, 'Practice piano'), { initialProps: { open: false, query: '' } })
    await act(() => vi.advanceTimersByTimeAsync(600))
    expect(request).not.toHaveBeenCalled()
    rerender({ open: true, query: 'learning' })
    await act(() => vi.advanceTimersByTimeAsync(499))
    expect(request).not.toHaveBeenCalled()
    await act(() => vi.advanceTimersByTimeAsync(1))
    expect(request).toHaveBeenCalledTimes(1)
    expect(request.mock.calls[0][0]).toEqual({ name: 'Scales', picker_query: 'learning', linked_task_name: 'Practice piano' })
    expect(result.current.recommendation?.id).toBe(1)
    act(() => result.current.dismiss())
    expect(result.current.recommendation).toBeUndefined()
    rerender({ open: false, query: 'learning' })
    rerender({ open: true, query: 'learning' })
    await act(() => vi.advanceTimersByTimeAsync(500))
    expect(result.current.recommendation?.id).toBe(1)
  })
  it('invalidates late results on context change and close', async () => {
    vi.useFakeTimers()
    const pending: ((r: typeof reply) => void)[] = []
    vi.spyOn(api, 'recommendTaskType').mockImplementation(() => new Promise(resolve => pending.push(resolve)))
    const { result, rerender } = renderHook(({ name, open }) => useTaskTypeRecommendation(name, types, null, open, 'music'), { initialProps: { name: 'Piano', open: true } })
    await act(() => vi.advanceTimersByTimeAsync(500))
    rerender({ name: 'Run', open: true })
    await act(async () => pending[0](reply))
    expect(result.current.recommendation).toBeUndefined()
    await act(() => vi.advanceTimersByTimeAsync(500))
    rerender({ name: 'Run', open: false })
    await act(async () => pending[1](reply))
    expect(result.current.recommendation).toBeUndefined()
  })
  it('supports query only, resets dismissal on edits, hides current type, and fails quietly', async () => {
    vi.useFakeTimers()
    const request = vi.spyOn(api, 'recommendTaskType').mockResolvedValue(reply)
    const { result, rerender } = renderHook(({ query, id }) => useTaskTypeRecommendation(undefined, types, id, true, query), { initialProps: { query: '', id: 2 } })
    await act(() => vi.advanceTimersByTimeAsync(500))
    expect(request).not.toHaveBeenCalled()
    rerender({ query: 'piano', id: 2 })
    await act(() => vi.advanceTimersByTimeAsync(500))
    expect(result.current.recommendation?.id).toBe(1)
    act(() => result.current.dismiss())
    rerender({ query: 'guitar', id: 2 })
    await act(() => vi.advanceTimersByTimeAsync(500))
    expect(result.current.recommendation?.id).toBe(1)
    rerender({ query: 'guitar', id: 1 })
    expect(result.current.recommendation).toBeUndefined()
    request.mockRejectedValue(new Error('offline'))
    rerender({ query: 'reading', id: 2 })
    await act(() => vi.advanceTimersByTimeAsync(500))
    expect(result.current.recommendation).toBeUndefined()
  })
})
