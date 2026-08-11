import { useCallback, useEffect, useState } from 'react'

interface AsyncState<T> {
  data: T | null
  error: Error | null
  isLoading: boolean
}

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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, dependencies)

  useEffect(() => {
    void load()
  }, [load])

  return { ...state, reload: load }
}
