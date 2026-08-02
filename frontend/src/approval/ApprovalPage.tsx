import { useCallback, useEffect, useState } from 'react'
import { apiRequest } from '../api/client'

type Status = 'PENDING' | 'APPROVED' | 'REJECTED'
type AdjustmentRequest = {
  id: string; warehouseCode: string; warehouseName: string; skuCode: string; productName: string
  movementType: string; quantity: number; reason: string; status: Status; requestedBy: string
  requestedAt: string; decidedBy: string | null; decisionComment: string | null; decidedAt: string | null
}

const movementLabel: Record<string, string> = {
  RECEIPT: '입고', ADJUSTMENT_IN: '수량 증가', ADJUSTMENT_OUT: '수량 감소',
  RESERVE: '출고 예약', RELEASE: '예약 해제', DAMAGE: '파손 처리',
}
const statusLabel: Record<Status, string> = { PENDING: '승인 대기', APPROVED: '승인', REJECTED: '거절' }

export function ApprovalPage() {
  const [items, setItems] = useState<AdjustmentRequest[]>([])
  const [status, setStatus] = useState<Status | ''>('PENDING')
  const [comments, setComments] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(true)
  const [workingId, setWorkingId] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      setItems(await apiRequest<AdjustmentRequest[]>(`/api/v1/approvals/inventory-adjustments${status ? `?status=${status}` : ''}`))
    } catch (reason) { setError(reason instanceof Error ? reason.message : '승인 요청을 불러오지 못했습니다.') }
    finally { setLoading(false) }
  }, [status])

  useEffect(() => {
    let active = true
    apiRequest<AdjustmentRequest[]>(`/api/v1/approvals/inventory-adjustments${status ? `?status=${status}` : ''}`)
      .then((requests) => { if (active) setItems(requests) })
      .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : '승인 요청을 불러오지 못했습니다.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [status])

  async function decide(item: AdjustmentRequest, decision: 'APPROVE' | 'REJECT') {
    const comment = comments[item.id]?.trim() ?? ''
    if (decision === 'REJECT' && !comment) { setError('거절 사유를 입력해 주세요.'); return }
    setWorkingId(item.id); setError(''); setNotice('')
    try {
      await apiRequest(`/api/v1/approvals/inventory-adjustments/${item.id}/decision`, {
        method: 'POST', body: JSON.stringify({ decision, comment }),
      })
      setNotice(decision === 'APPROVE' ? '승인하여 실제 재고에 반영했습니다.' : '요청을 거절했습니다.')
      await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '승인 요청을 처리하지 못했습니다.') }
    finally { setWorkingId('') }
  }

  const pendingCount = items.filter((item) => item.status === 'PENDING').length
  return <>
    <header className="topbar inventory-title">
      <div><p className="eyebrow">APPROVAL WORKFLOW</p><h1>승인 업무</h1><p className="subtitle">요청자와 승인자를 분리해 중요한 재고 변경을 안전하게 통제합니다.</p></div>
      <button className="secondary" onClick={() => void load()}>새로고침</button>
    </header>
    {notice && <div className="success-notice">{notice}<button onClick={() => setNotice('')}>×</button></div>}
    <section className="approval-summary">
      <div><span>현재 조회</span><strong>{items.length}</strong></div>
      <div><span>처리 대기</span><strong>{pendingCount}</strong></div>
      <div className="control-note"><b>직무 분리 통제</b><span>본인이 만든 요청은 본인이 승인할 수 없습니다.</span></div>
    </section>
    <section className="panel approval-panel">
      <div className="approval-toolbar">
        <div><p className="eyebrow">INVENTORY ADJUSTMENTS</p><h2>재고 조정 요청</h2></div>
        <select value={status} onChange={(event) => setStatus(event.target.value as Status | '')}>
          <option value="PENDING">승인 대기</option><option value="APPROVED">승인 완료</option>
          <option value="REJECTED">거절</option><option value="">전체 상태</option>
        </select>
      </div>
      {error && <p className="inline-error" role="alert">{error}</p>}
      <div className="approval-list">
        {loading ? <p className="empty-approval">승인 요청을 불러오는 중입니다.</p> : items.length === 0 ? <p className="empty-approval">조건에 맞는 승인 요청이 없습니다.</p> : items.map((item) =>
          <article className="approval-card" key={item.id}>
            <div className="approval-card-head"><div><span className={`approval-status ${item.status.toLowerCase()}`}>{statusLabel[item.status]}</span><h3>{item.productName}</h3><small>{item.warehouseCode} · {item.skuCode}</small></div><strong>{movementLabel[item.movementType] ?? item.movementType} {item.quantity}개</strong></div>
            <dl><div><dt>요청자</dt><dd>{item.requestedBy}</dd></div><div><dt>요청 시각</dt><dd>{new Date(item.requestedAt).toLocaleString('ko-KR')}</dd></div><div className="reason"><dt>요청 사유</dt><dd>{item.reason}</dd></div></dl>
            {item.status === 'PENDING' ? <div className="decision-area">
              <input aria-label="승인 또는 거절 의견" value={comments[item.id] ?? ''} onChange={(event) => setComments((current) => ({ ...current, [item.id]: event.target.value }))} placeholder="처리 의견 (거절 시 필수)" maxLength={500} />
              <button className="reject" disabled={workingId === item.id} onClick={() => void decide(item, 'REJECT')}>거절</button>
              <button className="approve" disabled={workingId === item.id} onClick={() => void decide(item, 'APPROVE')}>{workingId === item.id ? '처리 중' : '승인·반영'}</button>
            </div> : <p className="decision-result">{item.decidedBy} · {item.decisionComment || '처리 의견 없음'} · {item.decidedAt && new Date(item.decidedAt).toLocaleString('ko-KR')}</p>}
          </article>)}
      </div>
    </section>
  </>
}
