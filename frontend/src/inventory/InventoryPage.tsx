import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { apiRequest } from '../api/client'
import { useAuth } from '../auth/AuthContext'

type InventoryPosition = {
  warehouseCode: string; warehouseName: string; brandCode: string; styleCode: string
  productName: string; skuCode: string; colorCode: string; sizeCode: string
  onHandQuantity: number; reservedQuantity: number; availableQuantity: number
  damagedQuantity: number; updatedAt: string
}

type MovementType = 'RECEIPT' | 'ADJUSTMENT_IN' | 'ADJUSTMENT_OUT' | 'RESERVE' | 'RELEASE' | 'DAMAGE'
const movementLabels: Record<MovementType, string> = {
  RECEIPT: '입고', ADJUSTMENT_IN: '수량 증가', ADJUSTMENT_OUT: '수량 감소',
  RESERVE: '출고 예약', RELEASE: '예약 해제', DAMAGE: '파손 처리',
}

export function InventoryPage() {
  const { credential } = useAuth()
  const [items, setItems] = useState<InventoryPosition[]>([])
  const [query, setQuery] = useState('')
  const [warehouse, setWarehouse] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<InventoryPosition | null>(null)
  const [notice, setNotice] = useState('')

  const load = useCallback(async () => {
    if (!credential) return
    setLoading(true); setError('')
    const params = new URLSearchParams()
    if (query.trim()) params.set('query', query.trim())
    if (warehouse) params.set('warehouseCode', warehouse)
    try {
      setItems(await apiRequest<InventoryPosition[]>(`/api/v1/inventory?${params}`, credential))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '재고를 불러오지 못했습니다.')
    } finally { setLoading(false) }
  }, [credential, query, warehouse])

  useEffect(() => {
    if (!credential) return
    let active = true
    apiRequest<InventoryPosition[]>('/api/v1/inventory', credential)
      .then((positions) => { if (active) setItems(positions) })
      .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : '재고를 불러오지 못했습니다.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [credential])

  async function submitMovement(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!credential || !selected) return
    const data = new FormData(event.currentTarget)
    try {
      const result = await apiRequest<{ idempotent: boolean }>('/api/v1/inventory/movements', credential, {
        method: 'POST', body: JSON.stringify({
          warehouseCode: selected.warehouseCode, skuCode: selected.skuCode,
          movementType: data.get('movementType'), quantity: Number(data.get('quantity')),
          reason: data.get('reason'), idempotencyKey: crypto.randomUUID(),
        }),
      })
      setNotice(result.idempotent ? '이미 처리된 요청입니다.' : '재고 변경을 반영했습니다.')
      setSelected(null)
      await load()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '재고를 변경하지 못했습니다.')
    }
  }

  return <>
    <header className="topbar inventory-title">
      <div><p className="eyebrow">INVENTORY CONTROL</p><h1>재고 관리</h1><p className="subtitle">창고·SKU별 실재고와 예약 수량을 조회하고 안전하게 조정합니다.</p></div>
      <button className="secondary" onClick={() => void load()}>새로고침</button>
    </header>
    {notice && <div className="success-notice">{notice}<button onClick={() => setNotice('')}>×</button></div>}
    <section className="inventory-summary">
      <div><span>조회 SKU</span><strong>{items.length}</strong></div>
      <div><span>총 실재고</span><strong>{items.reduce((sum, item) => sum + item.onHandQuantity, 0).toLocaleString()}</strong></div>
      <div><span>예약</span><strong>{items.reduce((sum, item) => sum + item.reservedQuantity, 0).toLocaleString()}</strong></div>
      <div><span>판매 가능</span><strong>{items.reduce((sum, item) => sum + item.availableQuantity, 0).toLocaleString()}</strong></div>
    </section>
    <section className="panel inventory-panel">
      <form className="inventory-filters" onSubmit={(event) => { event.preventDefault(); void load() }}>
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="상품명, 스타일 코드, SKU 검색" />
        <select value={warehouse} onChange={(event) => setWarehouse(event.target.value)}>
          <option value="">전체 창고</option><option value="ICN-01">ICN-01 인천</option><option value="SEL-04">SEL-04 서울</option>
        </select>
        <button className="primary">조회</button>
      </form>
      {error && <p className="inline-error" role="alert">{error}</p>}
      <div className="table-wrap"><table className="inventory-table">
        <thead><tr><th>상품 / SKU</th><th>창고</th><th>실재고</th><th>예약</th><th>판매 가능</th><th>불량</th><th>업데이트</th><th></th></tr></thead>
        <tbody>
          {loading ? <tr><td colSpan={8} className="empty-cell">재고를 불러오는 중입니다.</td></tr> : items.length === 0 ? <tr><td colSpan={8} className="empty-cell">조건에 맞는 재고가 없습니다.</td></tr> : items.map((item) => <tr key={`${item.warehouseCode}-${item.skuCode}`}>
            <td><b>{item.productName}</b><small>{item.brandCode} · {item.skuCode}<br />{item.colorCode} / {item.sizeCode}</small></td>
            <td><b>{item.warehouseCode}</b><small>{item.warehouseName}</small></td>
            <td className="quantity">{item.onHandQuantity}</td><td className="quantity muted-number">{item.reservedQuantity}</td>
            <td className="quantity available">{item.availableQuantity}</td><td className="quantity">{item.damagedQuantity}</td>
            <td>{new Date(item.updatedAt).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</td>
            <td><button className="text-button" onClick={() => { setSelected(item); setError('') }}>재고 조정 →</button></td>
          </tr>)}
        </tbody>
      </table></div>
    </section>
    {selected && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setSelected(null) }}>
      <section className="movement-modal" role="dialog" aria-modal="true" aria-labelledby="movement-title">
        <button className="modal-close" onClick={() => setSelected(null)} aria-label="닫기">×</button>
        <p className="eyebrow">INVENTORY MOVEMENT</p><h2 id="movement-title">재고 조정</h2>
        <div className="selected-sku"><b>{selected.productName}</b><span>{selected.warehouseCode} · {selected.skuCode}</span><small>실재고 {selected.onHandQuantity} / 예약 {selected.reservedQuantity} / 판매 가능 {selected.availableQuantity}</small></div>
        <form onSubmit={submitMovement}>
          <label>처리 유형<select name="movementType" defaultValue="RECEIPT">{Object.entries(movementLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label>
          <label>수량<input name="quantity" type="number" min="1" defaultValue="1" required /></label>
          <label>처리 사유<textarea name="reason" maxLength={500} placeholder="예: 8월 정기 입고 검수 완료" required /></label>
          <button className="primary modal-submit">변경 반영</button>
        </form>
      </section>
    </div>}
  </>
}
