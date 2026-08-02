import { useCallback, useEffect, useState } from 'react'
import { apiRequest } from '../api/client'

type AuditLog = { id: string; actor: string; action: string; entityType: string; entityId: string; summary: string; occurredAt: string }
const actionLabel: Record<string, string> = {
  INVENTORY_ADJUSTMENT_REQUESTED: '조정 요청', INVENTORY_ADJUSTMENT_APPROVED: '승인·반영', INVENTORY_ADJUSTMENT_REJECTED: '요청 거절',
}

export function AuditLogPage() {
  const [items, setItems] = useState<AuditLog[]>([])
  const [query, setQuery] = useState('')
  const [action, setAction] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setLoading(true); setError('')
    const params = new URLSearchParams(); if (query.trim()) params.set('query', query.trim()); if (action) params.set('action', action)
    try { setItems(await apiRequest<AuditLog[]>(`/api/v1/audit-logs?${params}`)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '감사 로그를 불러오지 못했습니다.') }
    finally { setLoading(false) }
  }, [query, action])
  useEffect(() => {
    let active = true
    apiRequest<AuditLog[]>('/api/v1/audit-logs')
      .then((logs) => { if (active) setItems(logs) })
      .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : '감사 로그를 불러오지 못했습니다.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])
  return <>
    <header className="topbar inventory-title"><div><p className="eyebrow">AUDIT TRAIL</p><h1>감사 로그</h1><p className="subtitle">누가, 언제, 무엇을 요청하고 승인했는지 변경 이력을 추적합니다.</p></div></header>
    <section className="panel audit-panel">
      <form className="audit-filters" onSubmit={(event) => { event.preventDefault(); void load() }}>
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="담당자, 요청 ID, 내용 검색" />
        <select value={action} onChange={(event) => setAction(event.target.value)}><option value="">전체 행위</option>{Object.entries(actionLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
        <button className="primary">조회</button>
      </form>
      {error && <p className="inline-error" role="alert">{error}</p>}
      <div className="table-wrap"><table className="audit-table"><thead><tr><th>시각</th><th>행위</th><th>담당자</th><th>변경 내용</th><th>요청 ID</th></tr></thead><tbody>
        {loading ? <tr><td colSpan={5} className="empty-cell">감사 로그를 불러오는 중입니다.</td></tr> : items.length === 0 ? <tr><td colSpan={5} className="empty-cell">기록된 감사 로그가 없습니다.</td></tr> : items.map((item) => <tr key={item.id}><td>{new Date(item.occurredAt).toLocaleString('ko-KR')}</td><td><span className="audit-action">{actionLabel[item.action] ?? item.action}</span></td><td><b>{item.actor}</b></td><td>{item.summary}</td><td><code>{item.entityId.slice(0, 8)}</code></td></tr>)}
      </tbody></table></div>
    </section>
  </>
}
