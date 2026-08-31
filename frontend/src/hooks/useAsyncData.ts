import { useCallback, useEffect, useState } from 'react'

interface AsyncState<T> {
  data: T | null
  error: Error | null
  isLoading: boolean
}

/** 비동기 조회의 Loading·Error·Data 상태와 수동 재시도를 공통으로 관리한다. */
export function useAsyncData<T>(loader: () => Promise<T>, dependencies: unknown[] = []) {
  const [state, setState] = useState<AsyncState<T>>({
    data: null,
    error: null,
    isLoading: true,
  })

  const load = useCallback(async () => {
    setState((current) => ({ ...current, error: null, isLoading: true }))

    try {
      const data = await loader()
      setState({ data, error: null, isLoading: false })
    } catch (error) {
      setState({
        data: null,
        error: error instanceof Error ? error : new Error('알 수 없는 오류입니다.'),
        isLoading: false,
      })
    }
    // 호출자가 전달한 의존성 기준으로 loader를 다시 실행한다.
    // loader 함수 자체는 매 Render마다 달라질 수 있어 의존성 배열의 책임을 호출자에게 둔다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, dependencies)

  useEffect(() => {
    void load()
  }, [load])

  return { ...state, reload: load }
}
