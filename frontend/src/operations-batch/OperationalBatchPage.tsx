import { useEffect, useMemo, useState } from 'react'
import { apiRequest } from '../api/client'
import { useAuth } from '../auth/AuthContext'

type Status = 'RUNNING' | 'COMPLETED' | 'FAILED'
type Job = { jobCode: string; name: string; description: string; cronExpression: string; enabled: boolean; maxRetryCount: number; lastStatus: Status | null; lastStartedAt: string | null; lastCompletedAt: string | null }
type Execution = { id: string; jobCode: string; jobName: string; triggerType: 'SCHEDULED' | 'MANUAL' | 'RETRY'; status: Status; attemptNumber: number; retryOf: string | null; requestedBy: string; startedAt: string; completedAt: string | null; processedCount: number; resultSummary: string | null; errorMessage: string | null }
type RejectedItem = { id: string; sourceOrderId: string; skuCode: string; productName: string; errorCodes: string; resolutionStatus: 'PENDING' | 'RESOLVED'; rejectedAt: string; resolvedAt: string | null }
type DagRun = { dagRunId: string; state: string; intervalStart: string; intervalEnd: string; reprocessRejected: boolean }

const statusLabel: Record<Status, string> = { RUNNING: '실행 중', COMPLETED: '완료', FAILED: '실패' }
const triggerLabel = { SCHEDULED: '자동', MANUAL: '수동', RETRY: '재시도' }
const dateTime = (value: string | null) => value ? new Date(value).toLocaleString('ko-KR') : '—'
const localInput = (date: Date) => {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

export function OperationalBatchPage() {
  const { operator } = useAuth()
  const admin = operator?.roles.includes('ROLE_ADMIN') ?? false
  const [jobs, setJobs] = useState<Job[]>([])
  const [executions, setExecutions] = useState<Execution[]>([])
  const [filter, setFilter] = useState<Status | ''>('')
  const [loading, setLoading] = useState(true)
  const [working, setWorking] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [rejectedItems, setRejectedItems] = useState<RejectedItem[]>([])
  const [intervalEnd, setIntervalEnd] = useState(() => localInput(new Date()))
  const [intervalStart, setIntervalStart] = useState(() => localInput(new Date(Date.now() - 24 * 60 * 60_000)))
  const [reprocessRejected, setReprocessRejected] = useState(true)

  async function load() {
    setLoading(true); setError('')
    try {
      const query = filter ? `?status=${filter}&limit=100` : '?limit=100'
      const [jobRows, executionRows, rejectedRows] = await Promise.all([
        apiRequest<Job[]>('/api/v1/operations/batch-jobs'),
        apiRequest<Execution[]>(`/api/v1/operations/batch-executions${query}`),
        apiRequest<RejectedItem[]>('/api/v1/operations/airflow/rejected-items?limit=100'),
      ])
      setJobs(jobRows); setExecutions(executionRows); setRejectedItems(rejectedRows)
    } catch (reason) { setError(reason instanceof Error ? reason.message : '배치 작업을 불러오지 못했습니다.') }
    finally { setLoading(false) }
  }

  useEffect(() => {
    let active = true
    const query = filter ? `?status=${filter}&limit=100` : '?limit=100'
    Promise.all([apiRequest<Job[]>('/api/v1/operations/batch-jobs'), apiRequest<Execution[]>(`/api/v1/operations/batch-executions${query}`), apiRequest<RejectedItem[]>('/api/v1/operations/airflow/rejected-items?limit=100')])
      .then(([jobRows, executionRows, rejectedRows]) => { if (active) { setJobs(jobRows); setExecutions(executionRows); setRejectedItems(rejectedRows) } })
      .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : '배치 작업을 불러오지 못했습니다.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [filter])

  async function run(jobCode: string) {
    setWorking(jobCode); setError(''); setNotice('')
    try {
      const result = await apiRequest<Execution>(`/api/v1/operations/batch-jobs/${jobCode}/run`, { method: 'POST' })
      setNotice(result.status === 'COMPLETED' ? `${result.jobName} 작업을 완료했습니다.` : `${result.jobName} 작업이 실패했습니다. 실행 이력에서 원인을 확인해 주세요.`)
      await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '배치 작업을 실행하지 못했습니다.') }
    finally { setWorking('') }
  }

  async function retry(execution: Execution) {
    setWorking(execution.id); setError(''); setNotice('')
    try {
      const result = await apiRequest<Execution>(`/api/v1/operations/batch-executions/${execution.id}/retry`, { method: 'POST' })
      setNotice(result.status === 'COMPLETED' ? '재시도가 완료되었습니다.' : '재시도도 실패했습니다. 오류 원인을 확인해 주세요.')
      await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '재시도하지 못했습니다.') }
    finally { setWorking('') }
  }

  async function triggerBackfill() {
    setWorking('airflow-backfill'); setError(''); setNotice('')
    try {
      const result = await apiRequest<DagRun>('/api/v1/operations/airflow/backfills', {
        method: 'POST',
        body: JSON.stringify({ intervalStart: new Date(intervalStart).toISOString(), intervalEnd: new Date(intervalEnd).toISOString(), reprocessRejected }),
      })
      setNotice(`Airflow 작업을 요청했습니다. 실행 ID: ${result.dagRunId}`)
      await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Airflow 백필을 요청하지 못했습니다.') }
    finally { setWorking('') }
  }

  const totals = useMemo(() => ({
    running: executions.filter((item) => item.status === 'RUNNING').length,
    failed: executions.filter((item) => item.status === 'FAILED').length,
    success: executions.length ? Math.round(executions.filter((item) => item.status === 'COMPLETED').length / executions.length * 100) : 0,
  }), [executions])

  return <>
    <header className="topbar inventory-title">
      <div><p className="eyebrow">SCHEDULED JOB CONTROL</p><h1>배치 작업</h1><p className="subtitle">자동 작업의 실행 상태와 실패 원인을 확인하고 안전하게 재처리합니다.</p></div>
      <button className="secondary" onClick={() => void load()} disabled={loading}>새로고침</button>
    </header>
    {notice && <div className="success-notice">{notice}<button onClick={() => setNotice('')}>×</button></div>}
    {error && <p className="form-error analytics-error" role="alert">{error}</p>}
    <section className="batch-metrics">
      <article><span>등록 작업</span><strong>{jobs.length}</strong><small>활성 {jobs.filter((job) => job.enabled).length}개</small></article>
      <article><span>현재 실행 중</span><strong>{totals.running}</strong><small>동일 작업 중복 실행 차단</small></article>
      <article className={totals.failed ? 'danger' : ''}><span>실패 이력</span><strong>{totals.failed}</strong><small>선택한 조회 범위</small></article>
      <article><span>성공률</span><strong>{totals.success}%</strong><small>최근 {executions.length}회</small></article>
    </section>
    <section className="batch-job-grid">
      {jobs.map((job) => <article className="panel batch-job-card" key={job.jobCode}>
        <div className="batch-job-head"><div><p className="eyebrow">{job.jobCode}</p><h2>{job.name}</h2></div><span className={`batch-status ${job.enabled ? 'enabled' : 'disabled'}`}>{job.enabled ? '활성' : '중지'}</span></div>
        <p>{job.description}</p>
        <dl><div><dt>실행 주기</dt><dd>매시 정각 <small>{job.cronExpression}</small></dd></div><div><dt>최근 상태</dt><dd>{job.lastStatus ? statusLabel[job.lastStatus] : '실행 전'}</dd></div><div><dt>최대 재시도</dt><dd>{job.maxRetryCount}회</dd></div></dl>
        {admin ? <button className="primary" disabled={working === job.jobCode || !job.enabled} onClick={() => void run(job.jobCode)}>{working === job.jobCode ? '실행 중…' : '지금 수동 실행'}</button> : <small className="role-hint">수동 실행은 관리자만 가능합니다.</small>}
      </article>)}
    </section>
    <section className="panel airflow-control-panel">
      <div className="panel-head"><div><p className="eyebrow">AIRFLOW DATA RECOVERY</p><h2>과거 데이터 백필·오류 재처리</h2></div><span className="pending-count">미해결 {rejectedItems.filter((item) => item.resolutionStatus === 'PENDING').length}건</span></div>
      <div className="backfill-form">
        <label>시작 시각<input type="datetime-local" value={intervalStart} onChange={(event) => setIntervalStart(event.target.value)} /></label>
        <label>종료 시각<input type="datetime-local" value={intervalEnd} onChange={(event) => setIntervalEnd(event.target.value)} /></label>
        <label className="reprocess-check"><input type="checkbox" checked={reprocessRejected} onChange={(event) => setReprocessRejected(event.target.checked)} /><span><b>미해결 오류도 다시 검증</b><small>상품·SKU 기준정보를 수정한 뒤 실행하세요.</small></span></label>
        {admin ? <button className="primary" disabled={working === 'airflow-backfill' || !intervalStart || !intervalEnd} onClick={() => void triggerBackfill()}>{working === 'airflow-backfill' ? '요청 중…' : 'Airflow 백필 실행'}</button> : <small className="role-hint">백필 실행은 관리자만 가능합니다.</small>}
      </div>
      <div className="table-wrap"><table className="rejected-table"><thead><tr><th>주문</th><th>상품·SKU</th><th>오류 원인</th><th>처리 상태</th><th>격리·해결 시각</th></tr></thead><tbody>
        {rejectedItems.length ? rejectedItems.map((item) => <tr key={item.id}><td>{item.sourceOrderId}</td><td><b>{item.productName}</b><small>{item.skuCode}</small></td><td><code>{item.errorCodes}</code></td><td><span className={`rejection-status ${item.resolutionStatus.toLowerCase()}`}>{item.resolutionStatus === 'PENDING' ? '미해결' : '해결'}</span></td><td>{dateTime(item.rejectedAt)}<small>{item.resolvedAt ? `해결 ${dateTime(item.resolvedAt)}` : '기준정보 수정 후 재처리 필요'}</small></td></tr>) : <tr><td colSpan={5} className="empty-cell">격리된 오류 데이터가 없습니다.</td></tr>}
      </tbody></table></div>
      <div className="batch-safety-note"><b>백필이란?</b><span>과거에 빠졌거나 잘못 처리된 기간을 다시 수집하는 작업입니다. 같은 주문상품은 fact의 고유키로 중복 적재되지 않으며 실행 요청은 감사 로그에 남습니다.</span></div>
    </section>
    <section className="panel batch-history-panel">
      <div className="panel-head"><div><p className="eyebrow">EXECUTION HISTORY</p><h2>실행 이력</h2></div><select aria-label="실행 상태 필터" value={filter} onChange={(event) => { setLoading(true); setFilter(event.target.value as Status | '') }}><option value="">전체 상태</option><option value="RUNNING">실행 중</option><option value="COMPLETED">완료</option><option value="FAILED">실패</option></select></div>
      <div className="table-wrap"><table className="batch-history-table"><thead><tr><th>작업</th><th>상태</th><th>실행 방식</th><th>시도</th><th>요청자</th><th>시작·완료</th><th>처리 결과</th><th>조치</th></tr></thead><tbody>
        {loading ? <tr><td colSpan={8} className="empty-cell">실행 이력을 불러오는 중입니다.</td></tr> : executions.length ? executions.map((item) => <tr key={item.id}><td><b>{item.jobName}</b><small>{item.jobCode}</small></td><td><span className={`execution-status ${item.status.toLowerCase()}`}>{statusLabel[item.status]}</span></td><td>{triggerLabel[item.triggerType]}</td><td>{item.attemptNumber}차{item.retryOf && <small>원본 {item.retryOf.slice(0, 8)}</small>}</td><td>{item.requestedBy}</td><td>{dateTime(item.startedAt)}<small>{dateTime(item.completedAt)}</small></td><td className={item.status === 'FAILED' ? 'execution-error' : ''}>{item.errorMessage ?? item.resultSummary ?? '처리 중'}{item.status === 'COMPLETED' && <small>{item.processedCount.toLocaleString()}건</small>}</td><td>{admin && item.status === 'FAILED' ? <button className="text-button" disabled={working === item.id} onClick={() => void retry(item)}>{working === item.id ? '재시도 중…' : '재시도'}</button> : '—'}</td></tr>) : <tr><td colSpan={8} className="empty-cell">조건에 맞는 실행 이력이 없습니다.</td></tr>}
      </tbody></table></div>
      <div className="batch-safety-note"><b>안전장치</b><span>같은 작업은 동시에 한 번만 실행되며, 실패 재시도는 원본 실행과 연결되고 최대 횟수를 넘길 수 없습니다.</span></div>
    </section>
  </>
}
