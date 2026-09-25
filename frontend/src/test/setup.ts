import { cleanup } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import { vi } from 'vitest'

// jsdom exposes scrollBy but reports it as unimplemented. Keep tests deterministic;
// focused scrolling tests can spy on this shared mock.
window.scrollBy = vi.fn()
HTMLElement.prototype.scrollIntoView = vi.fn()
// jsdom has dialog elements but no native top-layer implementation.
HTMLDialogElement.prototype.showModal = function () { this.open = true }
HTMLDialogElement.prototype.close = function () { this.open = false }
import { afterEach } from 'vitest'

class TestResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = TestResizeObserver as typeof ResizeObserver
}

afterEach(() => {
  cleanup()
})
