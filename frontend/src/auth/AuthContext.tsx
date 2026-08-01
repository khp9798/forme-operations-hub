import { createContext, useContext, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { apiRequest, loginRequest, logoutRequest } from '../api/client'

type Operator = { username: string; roles: string[] }

type AuthContextValue = {
  operator: Operator | null
  restoring: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [operator, setOperator] = useState<Operator | null>(null)
  const [restoring, setRestoring] = useState(true)

  useEffect(() => {
    apiRequest<Operator>('/api/v1/auth/me')
      .then(setOperator)
      .catch(() => setOperator(null))
      .finally(() => setRestoring(false))
  }, [])

  const value = useMemo<AuthContextValue>(() => ({
    operator,
    restoring,
    login: async (username, password) => {
      await loginRequest(username, password)
      const current = await apiRequest<Operator>('/api/v1/auth/me')
      setOperator(current)
    },
    logout: async () => {
      await logoutRequest()
      setOperator(null)
    },
  }), [operator, restoring])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// Auth provider와 소비 훅을 함께 두어 인증 저장소의 공개 API를 한 파일에서 관리합니다.
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('AuthProvider 안에서 사용해야 합니다.')
  return context
}

export function LoginScreen() {
  const { login } = useAuth()
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    setSubmitting(true)
    setError('')
    try {
      await login(String(data.get('username')), String(data.get('password')))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '로그인하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return <main className="login-page">
    <section className="login-copy">
      <p className="eyebrow">FORME GLOBAL OPERATIONS</p>
      <h1>흩어진 운영 데이터를<br />한곳에서 통제합니다.</h1>
      <p>주문, 재고, 배치 작업과 승인 흐름을 연결하는 사내 운영 플랫폼입니다.</p>
      <div className="login-stat"><strong>99.82%</strong><span>최근 배치 성공률</span></div>
    </section>
    <section className="login-card">
      <div className="brand dark"><span>FORME</span><small>OPS</small></div>
      <p className="eyebrow">OPERATOR SIGN IN</p>
      <h2>운영자 로그인</h2>
      <p className="muted">허가된 사내 계정으로 접속해 주세요.</p>
      <form onSubmit={handleSubmit}>
        <label>아이디<input name="username" defaultValue="ops-admin" autoComplete="username" required /></label>
        <label>비밀번호<input name="password" type="password" autoComplete="current-password" required /></label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="primary login-submit" disabled={submitting}>{submitting ? '확인 중…' : '로그인'}</button>
      </form>
      <small className="demo-help">로컬 기본 비밀번호: forme-local-admin</small>
    </section>
  </main>
}
