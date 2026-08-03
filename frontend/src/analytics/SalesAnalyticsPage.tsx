import { useCallback, useEffect, useState } from 'react'
import { apiRequest } from '../api/client'
import { useAuth } from '../auth/AuthContext'

type AnalyticsItem = {
  brandCode: string; productName: string; skuCode: string; orderCount: number; unitsSold: number
  grossSales: number; onHandQuantity: number; reservedQuantity: number; availableQuantity: number; sellThroughRate: number
}
type Dashboard = { days: number; orderCount: number; unitsSold: number; grossSales: number; availableQuantity: number; lastRefreshedAt: string | null; items: AnalyticsItem[] }
type PlanMetric = { label: string; planningTimeMs: number; executionTimeMs: number; planLines: string[] }
type Plans = { days: number; rawQuery: PlanMetric; aggregateQuery: PlanMetric }
type IndexBenchmark = { days: number; sampleRows: number; withoutIndex: PlanMetric; withIndex: PlanMetric }

const won = new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW', maximumFractionDigits: 0 })

export function SalesAnalyticsPage() {
  const { operator } = useAuth()
  const [days, setDays] = useState(30)
  const [dashboard, setDashboard] = useState<Dashboard | null>(null)
  const [plans, setPlans] = useState<Plans | null>(null)
  const [benchmarkRows, setBenchmarkRows] = useState(100000)
  const [benchmark, setBenchmark] = useState<IndexBenchmark | null>(null)
  const [loading, setLoading] = useState(true)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async (range = days) => {
    setLoading(true); setError('')
    try { setDashboard(await apiRequest<Dashboard>(`/api/v1/analytics/sales-inventory?days=${range}`)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '판매·재고 지표를 불러오지 못했습니다.') }
    finally { setLoading(false) }
  }, [days])

  useEffect(() => {
    let active = true
    apiRequest<Dashboard>('/api/v1/analytics/sales-inventory?days=30')
      .then((result) => { if (active) setDashboard(result) })
      .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : '판매·재고 지표를 불러오지 못했습니다.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  async function refresh() {
    setWorking(true); setError(''); setNotice('')
    try {
      const result = await apiRequest<{ aggregateRows: number }>(`/api/v1/analytics/sales-inventory/refresh?days=${days}`, { method: 'POST' })
      setNotice(`판매 집계 ${result.aggregateRows}행을 새로 계산했습니다.`)
      await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '판매 집계를 갱신하지 못했습니다.') }
    finally { setWorking(false) }
  }

  async function comparePlans() {
    setWorking(true); setError('')
    try { setPlans(await apiRequest<Plans>(`/api/v1/analytics/sales-inventory/query-plans?days=${days}`)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'SQL 실행계획을 분석하지 못했습니다.') }
    finally { setWorking(false) }
  }

  async function generateBenchmark() {
    setWorking(true); setError(''); setNotice(''); setBenchmark(null)
    try {
      const result = await apiRequest<{ sampleRows: number; distinctSkus: number }>(`/api/v1/analytics/sales-inventory/index-benchmark/data?rows=${benchmarkRows}`, { method: 'POST' })
      setNotice(`성능 비교용 샘플 ${result.sampleRows.toLocaleString()}행과 SKU ${result.distinctSkus.toLocaleString()}개를 생성했습니다.`)
    } catch (reason) { setError(reason instanceof Error ? reason.message : '성능 비교용 데이터를 생성하지 못했습니다.') }
    finally { setWorking(false) }
  }

  async function compareIndexes() {
    setWorking(true); setError('')
    try { setBenchmark(await apiRequest<IndexBenchmark>(`/api/v1/analytics/sales-inventory/index-benchmark?days=${days}`)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '인덱스 성능을 비교하지 못했습니다.') }
    finally { setWorking(false) }
  }

  const maxSales = Math.max(...(dashboard?.items.map((item) => item.grossSales) ?? [0]), 1)
  const improvement = plans && plans.rawQuery.executionTimeMs > 0
    ? Math.max(0, (1 - plans.aggregateQuery.executionTimeMs / plans.rawQuery.executionTimeMs) * 100) : 0
  const indexImprovement = benchmark && benchmark.withoutIndex.executionTimeMs > 0
    ? (1 - benchmark.withIndex.executionTimeMs / benchmark.withoutIndex.executionTimeMs) * 100 : 0

  return <>
    <header className="topbar inventory-title">
      <div><p className="eyebrow">SALES & INVENTORY INTELLIGENCE</p><h1>판매·재고 분석</h1><p className="subtitle">일별 집계 데이터로 상품 판매와 현재 재고를 빠르게 비교합니다.</p></div>
      <div className="analytics-actions">
        <select aria-label="조회 기간" value={days} onChange={(event) => { const range = Number(event.target.value); setDays(range); setPlans(null); void load(range) }}><option value={7}>최근 7일</option><option value={30}>최근 30일</option><option value={90}>최근 90일</option><option value={365}>최근 1년</option></select>
        {operator?.roles.includes('ROLE_ADMIN') && <button className="secondary" disabled={working} onClick={() => void refresh()}>집계 새로고침</button>}
      </div>
    </header>
    {notice && <div className="success-notice">{notice}<button onClick={() => setNotice('')}>×</button></div>}
    {error && <p className="form-error analytics-error" role="alert">{error}</p>}
    <section className="analytics-metrics">
      <article><span>주문</span><strong>{dashboard?.orderCount.toLocaleString() ?? '—'}</strong><small>중복 없는 주문 수</small></article>
      <article><span>판매 수량</span><strong>{dashboard?.unitsSold.toLocaleString() ?? '—'}</strong><small>상품 합계</small></article>
      <article className="revenue"><span>매출</span><strong>{dashboard ? won.format(dashboard.grossSales) : '—'}</strong><small>할인 전 총액</small></article>
      <article><span>판매 가능 재고</span><strong>{dashboard?.availableQuantity.toLocaleString() ?? '—'}</strong><small>전체 창고 합계</small></article>
    </section>
    <section className="panel analytics-panel">
      <div className="panel-head"><div><p className="eyebrow">SKU PERFORMANCE</p><h2>상품별 판매와 재고</h2></div><small>{dashboard?.lastRefreshedAt ? `${new Date(dashboard.lastRefreshedAt).toLocaleString('ko-KR')} 집계` : '집계 전'}</small></div>
      <div className="table-wrap"><table className="analytics-table"><thead><tr><th>상품</th><th>주문</th><th>판매</th><th>매출</th><th>실재고</th><th>예약</th><th>판매 가능</th><th>판매 소진율</th></tr></thead><tbody>
        {loading ? <tr><td colSpan={8} className="empty-cell">분석 데이터를 불러오는 중입니다.</td></tr> : dashboard?.items.length ? dashboard.items.map((item) => <tr key={item.skuCode}><td><b>{item.brandCode}</b><span>{item.productName}</span><small>{item.skuCode}</small></td><td>{item.orderCount}</td><td>{item.unitsSold}</td><td><b>{won.format(item.grossSales)}</b><i className="sales-bar"><em style={{ width: `${item.grossSales / maxSales * 100}%` }} /></i></td><td>{item.onHandQuantity}</td><td>{item.reservedQuantity}</td><td className="available">{item.availableQuantity}</td><td><strong>{item.sellThroughRate.toFixed(1)}%</strong></td></tr>) : <tr><td colSpan={8} className="empty-cell">집계된 판매 데이터가 없습니다. 관리자가 집계를 실행해 주세요.</td></tr>}
      </tbody></table></div>
    </section>
    <section className="panel plan-panel">
      <div className="panel-head"><div><p className="eyebrow">POSTGRESQL EXPLAIN ANALYZE</p><h2>SQL 실행계획 비교</h2></div><button className="secondary" disabled={working} onClick={() => void comparePlans()}>실행계획 분석</button></div>
      {!plans ? <div className="plan-empty"><b>원본 주문 조회와 집계 테이블 조회를 같은 조건으로 비교합니다.</b><span>서버에 등록된 읽기 전용 SQL만 실행하므로 사용자가 임의 SQL을 입력할 수 없습니다.</span></div> : <div className="plan-comparison">
        <article><span>BEFORE</span><h3>{plans.rawQuery.label}</h3><strong>{plans.rawQuery.executionTimeMs.toFixed(3)} ms</strong><small>계획 {plans.rawQuery.planningTimeMs.toFixed(3)} ms</small><details><summary>실행계획 보기</summary><pre>{plans.rawQuery.planLines.join('\n')}</pre></details></article>
        <div className="improvement"><span>실행시간 감소</span><strong>{improvement.toFixed(1)}%</strong><small>현재 로컬 데이터 기준</small></div>
        <article className="optimized"><span>AFTER</span><h3>{plans.aggregateQuery.label}</h3><strong>{plans.aggregateQuery.executionTimeMs.toFixed(3)} ms</strong><small>계획 {plans.aggregateQuery.planningTimeMs.toFixed(3)} ms</small><details><summary>실행계획 보기</summary><pre>{plans.aggregateQuery.planLines.join('\n')}</pre></details></article>
      </div>}
    </section>
    <section className="panel plan-panel benchmark-panel">
      <div className="panel-head"><div><p className="eyebrow">INDEX PERFORMANCE LAB</p><h2>대량 데이터 인덱스 비교</h2></div></div>
      <div className="benchmark-controls">
        <div><b>격리된 테스트 데이터</b><span>실제 주문을 건드리지 않고 같은 데이터를 인덱스 없는 표와 있는 표에 복제합니다.</span></div>
        <select aria-label="샘플 데이터 크기" value={benchmarkRows} onChange={(event) => setBenchmarkRows(Number(event.target.value))}><option value={10000}>10,000행</option><option value={100000}>100,000행</option><option value={300000}>300,000행</option><option value={500000}>500,000행</option></select>
        {operator?.roles.includes('ROLE_ADMIN') && <button className="secondary" disabled={working} onClick={() => void generateBenchmark()}>샘플 생성</button>}
        <button className="primary" disabled={working} onClick={() => void compareIndexes()}>인덱스 비교 실행</button>
      </div>
      {!benchmark ? <div className="plan-empty"><b>동일한 판매 집계 SQL을 두 테이블에서 실행합니다.</b><span>관리자가 먼저 샘플을 생성한 뒤 비교하면 PostgreSQL이 선택한 스캔 방식과 실행시간을 확인할 수 있습니다.</span></div> : <div className="plan-comparison">
        <article><span>WITHOUT INDEX</span><h3>{benchmark.withoutIndex.label}</h3><strong>{benchmark.withoutIndex.executionTimeMs.toFixed(3)} ms</strong><small>샘플 {benchmark.sampleRows.toLocaleString()}행 · 계획 {benchmark.withoutIndex.planningTimeMs.toFixed(3)} ms</small><details><summary>실행계획 보기</summary><pre>{benchmark.withoutIndex.planLines.join('\n')}</pre></details></article>
        <div className={`improvement ${indexImprovement < 0 ? 'slower' : ''}`}><span>실행시간 변화</span><strong>{indexImprovement >= 0 ? '↓' : '↑'} {Math.abs(indexImprovement).toFixed(1)}%</strong><small>캐시·조회 범위에 따라 달라집니다</small></div>
        <article className="optimized"><span>WITH INDEX</span><h3>{benchmark.withIndex.label}</h3><strong>{benchmark.withIndex.executionTimeMs.toFixed(3)} ms</strong><small>복합 커버링 인덱스 · 계획 {benchmark.withIndex.planningTimeMs.toFixed(3)} ms</small><details><summary>실행계획 보기</summary><pre>{benchmark.withIndex.planLines.join('\n')}</pre></details></article>
      </div>}
      <div className="benchmark-note"><b>왜 항상 빨라지지는 않나요?</b><span>조회 범위가 표의 대부분이면 PostgreSQL은 인덱스를 여러 번 읽는 것보다 표 전체를 한 번 읽는 편이 낫다고 판단할 수 있습니다. 그래서 실행계획을 직접 측정해야 합니다.</span></div>
    </section>
  </>
}
