import { useRef, useState, type ChangeEvent, type DragEvent } from 'react'
import { apiRequest } from '../api/client'

type ImportRow = {
  lineNumber: number; sourceOrderId: string; skuCode: string; quantity: number | null
  unitPrice: number | null; status: 'VALID' | 'INVALID'; errors: string[]
}
type ImportResult = {
  jobId: string; fileName: string; status: string; totalCount: number
  validCount: number; invalidCount: number; rows: ImportRow[]
}

const template = `source_order_id,ordered_at,sku_code,quantity,unit_price,currency,recipient_name,postal_code,address_line1,address_line2
EXT-20260802-001,2026-08-02T10:30:00+09:00,MLB-CAP-0091-BK-F,2,39000,KRW,김포르메,04524,서울특별시 중구 세종대로 110,테스트 주문
EXT-20260802-002,2026-08-02T10:35:00+09:00,DX-JK-2421-KH-M,1,159000,KRW,이오퍼레이터,48058,부산광역시 해운대구 센텀중앙로 55,테스트 주문`

export function OrderImportPage() {
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [dragging, setDragging] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState<ImportResult | null>(null)

  function selectFile(selected?: File) {
    if (!selected) return
    setFile(selected); setResult(null); setError('')
  }

  async function upload() {
    if (!file) { setError('검증할 CSV 파일을 선택해 주세요.'); return }
    const formData = new FormData(); formData.append('file', file)
    setLoading(true); setError('')
    try { setResult(await apiRequest<ImportResult>('/api/v1/order-imports/validate', { method: 'POST', body: formData })) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '주문 파일을 검증하지 못했습니다.') }
    finally { setLoading(false) }
  }

  function downloadTemplate() {
    const blob = new Blob([`\uFEFF${template}`], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob); const anchor = document.createElement('a')
    anchor.href = url; anchor.download = 'forme-order-import-template.csv'; anchor.click(); URL.revokeObjectURL(url)
  }

  function onDrop(event: DragEvent<HTMLButtonElement>) {
    event.preventDefault(); setDragging(false); selectFile(event.dataTransfer.files[0])
  }

  return <>
    <header className="topbar inventory-title">
      <div><p className="eyebrow">ORDER INTEGRATION</p><h1>외부 주문 통합</h1><p className="subtitle">외부 OMS·마켓 주문을 표준 CSV로 받아 처리 전에 오류를 검증합니다.</p></div>
      <button className="secondary" onClick={downloadTemplate}>CSV 양식 받기 ↓</button>
    </header>
    <section className="import-guide">
      <div><span>01</span><b>표준 양식 작성</b><small>열 이름과 순서를 유지합니다.</small></div>
      <i>→</i><div><span>02</span><b>업로드·검증</b><small>SKU, 수량, 주문 정보를 확인합니다.</small></div>
      <i>→</i><div><span>03</span><b>오류 수정</b><small>실패 행만 고쳐 다시 올립니다.</small></div>
    </section>
    <section className="panel import-panel">
      <div className="panel-head"><div><p className="eyebrow">VALIDATION GATE</p><h2>주문 CSV 업로드</h2></div><small>최대 5MB · 5,000행 · UTF-8</small></div>
      <div className="import-body">
        <input ref={inputRef} className="visually-hidden" type="file" accept=".csv,text/csv" onChange={(event: ChangeEvent<HTMLInputElement>) => selectFile(event.target.files?.[0])} />
        <button className={`dropzone ${dragging ? 'dragging' : ''}`} onClick={() => inputRef.current?.click()}
          onDragOver={(event) => { event.preventDefault(); setDragging(true) }} onDragLeave={() => setDragging(false)} onDrop={onDrop}>
          <span className="upload-icon">CSV</span><b>{file ? file.name : 'CSV 파일을 놓거나 눌러서 선택하세요'}</b>
          <small>{file ? `${(file.size / 1024).toFixed(1)} KB · 다른 파일 선택` : '원본 주문은 검증 기록과 함께 안전하게 보관됩니다.'}</small>
        </button>
        {error && <p className="inline-error" role="alert">{error}</p>}
        <button className="primary import-submit" disabled={!file || loading} onClick={() => void upload()}>{loading ? '검증하는 중…' : '주문 데이터 검증'}</button>
      </div>
    </section>
    {result && <section className="panel result-panel">
      <div className="panel-head"><div><p className="eyebrow">VALIDATION RESULT</p><h2>검증 결과</h2></div><small>작업 ID {result.jobId.slice(0, 8)}</small></div>
      <div className="result-summary">
        <div><span>전체</span><strong>{result.totalCount}</strong></div><div className="valid"><span>정상</span><strong>{result.validCount}</strong></div><div className="invalid"><span>오류</span><strong>{result.invalidCount}</strong></div>
        <p>{result.invalidCount ? '오류 행을 수정한 뒤 파일을 다시 업로드해 주세요.' : '모든 주문이 다음 처리 단계로 이동할 수 있습니다.'}</p>
      </div>
      <div className="table-wrap"><table className="import-table"><thead><tr><th>행</th><th>외부 주문번호</th><th>SKU</th><th>수량</th><th>단가</th><th>결과</th></tr></thead>
        <tbody>{result.rows.map((row) => <tr key={row.lineNumber}><td>{row.lineNumber}</td><td><b>{row.sourceOrderId || '—'}</b></td><td>{row.skuCode || '—'}</td><td>{row.quantity ?? '—'}</td><td>{row.unitPrice?.toLocaleString() ?? '—'}</td><td><span className={`validation-badge ${row.status.toLowerCase()}`}>{row.status === 'VALID' ? '정상' : '오류'}</span>{row.errors.length > 0 && <small className="row-errors">{row.errors.join(' · ')}</small>}</td></tr>)}</tbody>
      </table></div>
    </section>}
  </>
}
