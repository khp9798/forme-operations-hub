import { useState } from 'react'
import './App.css'
import { LoginScreen, useAuth } from './auth/AuthContext'
import { InventoryPage } from './inventory/InventoryPage'
import { OrderImportPage } from './order-import/OrderImportPage'

const metrics = [
  { label: '오늘 통합 주문', value: '18,420', delta: '+12.4%', tone: 'blue' },
  { label: '출고 대기', value: '1,284', delta: '처리 필요', tone: 'amber' },
  { label: '재고 불일치', value: '27', delta: '-8건', tone: 'red' },
  { label: '배치 성공률', value: '99.82%', delta: '최근 24시간', tone: 'green' },
]

const jobs = [
  { name: 'GLOBAL_ORDER_INGEST', source: 'OMS-CN', count: '42,180', status: '완료', time: '03:18' },
  { name: 'INVENTORY_RECONCILIATION', source: 'WMS-ICN', count: '128,442', status: '처리 중', time: '09:42' },
  { name: 'STORE_SALES_IMPORT', source: 'POS-KR', count: '18,902', status: '일부 실패', time: '09:30' },
  { name: 'LOW_STOCK_DETECTION', source: 'S-ERP', count: '8,420', status: '대기', time: '10:00' },
]

const exceptions = [
  { code: 'MLB-CAP-0091-BK', place: 'ICN-01', issue: 'OMS 12 / WMS 10', age: '8분 전' },
  { code: 'DX-JK-2421-KH', place: 'SEL-04', issue: '예약 재고 초과', age: '21분 전' },
  { code: 'ST-SHOE-110-WH', place: 'BUS-02', issue: '바코드 미등록', age: '34분 전' },
]

function App() {
  const { operator, restoring, logout } = useAuth()
  const [page, setPage] = useState<'overview' | 'orders' | 'inventory'>('overview')

  if (restoring) return <div className="session-loading">운영 세션을 확인하는 중입니다.</div>
  if (!operator) return <LoginScreen />

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand"><span>FORME</span><small>OPS</small></div>
        <nav aria-label="주요 메뉴">
          <button className={page === 'overview' ? 'active' : ''} onClick={() => setPage('overview')}>운영 현황</button>
          <button className={page === 'orders' ? 'active' : ''} onClick={() => setPage('orders')}>주문 통합</button>
          <button className={page === 'inventory' ? 'active' : ''} onClick={() => setPage('inventory')}>재고 관리</button>
          <button disabled>배치 작업 <small>준비 중</small></button>
          <button disabled>승인 업무 <small>준비 중</small></button>
          <button disabled>감사 로그 <small>준비 중</small></button>
          <button disabled>AI 어시스턴트 <small>준비 중</small></button>
        </nav>
        <div className="sidebar-foot">
          <span className="health-dot" /> 모든 시스템 정상
          <small>production · ap-northeast</small>
        </div>
      </aside>

      <main>
        {page === 'inventory' ? <InventoryPage /> : page === 'orders' ? <OrderImportPage /> : <>
        <header className="topbar">
          <div><p className="eyebrow">GLOBAL OPERATIONS CONTROL</p><h1>운영 현황</h1></div>
          <div className="header-actions">
            <button className="search">⌕&nbsp; 주문·SKU·작업 검색</button>
            <div className="operator"><span>{operator.username}</span><small>{operator.roles.includes('ROLE_OPERATOR') ? 'OPERATOR' : 'USER'}</small></div>
            <button className="profile" aria-label="로그아웃" onClick={() => void logout()}>OUT</button>
          </div>
        </header>

        <section className="notice">
          <div><b>AI 브리핑</b><span>중국 OMS 주문량이 평소보다 18% 높습니다. ICN-01 출고 인력을 확인해 주세요.</span></div>
          <button>근거 데이터 보기 →</button>
        </section>

        <section className="metric-grid" aria-label="핵심 운영 지표">
          {metrics.map((metric) => (
            <article className={`metric ${metric.tone}`} key={metric.label}>
              <span>{metric.label}</span><strong>{metric.value}</strong><small>{metric.delta}</small>
            </article>
          ))}
        </section>

        <section className="content-grid">
          <article className="panel jobs-panel">
            <div className="panel-head"><div><p className="eyebrow">BATCH & INTEGRATION</p><h2>연동 작업</h2></div><button>전체 작업 →</button></div>
            <div className="table-wrap">
              <table>
                <thead><tr><th>작업</th><th>소스</th><th>처리 건수</th><th>상태</th><th>시작</th></tr></thead>
                <tbody>{jobs.map((job) => <tr key={job.name}><td><b>{job.name}</b></td><td>{job.source}</td><td>{job.count}</td><td><span className={`status ${job.status.replace(' ', '-')}`}>{job.status}</span></td><td>{job.time}</td></tr>)}</tbody>
              </table>
            </div>
          </article>

          <article className="panel exception-panel">
            <div className="panel-head"><div><p className="eyebrow">ACTION REQUIRED</p><h2>재고 예외</h2></div><span className="count">27</span></div>
            <div className="exception-list">
              {exceptions.map((item) => <button key={item.code}><span><b>{item.code}</b><small>{item.place} · {item.issue}</small></span><em>{item.age}</em></button>)}
            </div>
            <button className="primary">불일치 재고 검토</button>
          </article>
        </section>

        <section className="panel flow-panel">
          <div className="panel-head"><div><p className="eyebrow">TODAY'S PIPELINE</p><h2>글로벌 주문 처리 흐름</h2></div><small>마지막 갱신 10:02:41</small></div>
          <div className="pipeline">
            <div><span>01</span><b>주문 수집</b><strong>18,420</strong></div><i>→</i>
            <div><span>02</span><b>검증 완료</b><strong>18,338</strong></div><i>→</i>
            <div><span>03</span><b>재고 할당</b><strong>17,990</strong></div><i>→</i>
            <div><span>04</span><b>출고 지시</b><strong>16,706</strong></div>
          </div>
        </section>
        </>}
      </main>
    </div>
  )
}

export default App
