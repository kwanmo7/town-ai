import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// 각 Test가 독립된 DOM에서 실행되도록 React Testing Library 상태를 정리한다.
afterEach(() => {
  cleanup()
})
