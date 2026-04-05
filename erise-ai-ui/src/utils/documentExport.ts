import html2canvas from 'html2canvas'
import { jsPDF } from 'jspdf'
import TurndownService from 'turndown'

export interface TocItem {
  index: number
  level: number
  text: string
  tagName: string
}

interface DocumentExportPayload {
  fileName: string
  title: string
  summary?: string
  bodyHtml: string
}

const exportStyles = `
  body {
    font-family: Aptos, 'Microsoft YaHei', 'PingFang SC', sans-serif;
    color: #1c242b;
    line-height: 1.8;
    margin: 0;
    background: #ffffff;
  }
  .document-export {
    padding: 56px 60px 72px;
  }
  .document-export__title {
    margin: 0 0 18px;
    font-size: 34px;
    font-weight: 700;
    line-height: 1.25;
  }
  .document-export__summary {
    margin: 0 0 28px;
    padding: 16px 18px;
    border-radius: 18px;
    background: #f5f7fa;
    color: #52616f;
  }
  .document-export__body img {
    max-width: 100%;
    height: auto;
  }
  .document-export__body table {
    width: 100%;
    border-collapse: collapse;
  }
  .document-export__body td,
  .document-export__body th {
    border: 1px solid #d0d7de;
    padding: 8px 10px;
  }
`

const turndownService = new TurndownService({
  headingStyle: 'atx',
  bulletListMarker: '-',
  codeBlockStyle: 'fenced',
})

const sanitizeFileName = (value: string) => value.trim().replace(/[\\/:*?"<>|]+/g, '-').replace(/\s+/g, ' ') || 'document'

const downloadBlob = (blob: Blob, fileName: string) => {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 5000)
}

export const renderDocumentHtml = (title: string, summary: string | undefined, bodyHtml: string) => {
  const summaryHtml = summary?.trim() ? `<div class="document-export__summary">${summary.trim()}</div>` : ''
  return `
    <div class="document-export">
      <h1 class="document-export__title">${title || '未命名文档'}</h1>
      ${summaryHtml}
      <div class="document-export__body">${bodyHtml}</div>
    </div>
  `
}

export const buildTocFromHtml = (bodyHtml: string): TocItem[] => {
  const parser = new DOMParser()
  const doc = parser.parseFromString(bodyHtml || '<p></p>', 'text/html')
  const headings = Array.from(doc.body.querySelectorAll('h1, h2, h3, h4'))
  return headings.map((heading, index) => ({
    index,
    level: Number.parseInt(heading.tagName.slice(1), 10),
    text: heading.textContent?.trim() || `标题 ${index + 1}`,
    tagName: heading.tagName.toLowerCase(),
  }))
}

export const exportDocumentAsDoc = async ({ fileName, title, summary, bodyHtml }: DocumentExportPayload) => {
  const html = `
    <html>
      <head>
        <meta charset="utf-8" />
        <title>${title}</title>
        <style>${exportStyles}</style>
      </head>
      <body>${renderDocumentHtml(title, summary, bodyHtml)}</body>
    </html>
  `
  const blob = new Blob(['\ufeff', html], { type: 'application/msword' })
  downloadBlob(blob, `${sanitizeFileName(fileName)}.doc`)
}

export const exportDocumentAsMarkdown = async ({ fileName, title, summary, bodyHtml }: DocumentExportPayload) => {
  const header = [`# ${title || '未命名文档'}`]
  if (summary?.trim()) {
    header.push('', `> ${summary.trim()}`)
  }
  const markdown = [header.join('\n'), turndownService.turndown(bodyHtml || '<p></p>')].filter(Boolean).join('\n\n')
  const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' })
  downloadBlob(blob, `${sanitizeFileName(fileName)}.md`)
}

const renderCanvas = (element: HTMLElement) =>
  html2canvas(element, {
    scale: 2,
    backgroundColor: '#ffffff',
    useCORS: true,
    logging: false,
    windowWidth: element.scrollWidth,
    windowHeight: element.scrollHeight,
  })

export const exportElementAsJpg = async (element: HTMLElement, fileName: string) => {
  const canvas = await renderCanvas(element)
  const blob = await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((value) => {
      if (value) {
        resolve(value)
        return
      }
      reject(new Error('无法生成 JPG 文件'))
    }, 'image/jpeg', 0.95)
  })
  downloadBlob(blob, `${sanitizeFileName(fileName)}.jpg`)
}

export const exportElementAsPdf = async (element: HTMLElement, fileName: string) => {
  const canvas = await renderCanvas(element)
  const pdf = new jsPDF('p', 'pt', 'a4')
  const pageWidth = pdf.internal.pageSize.getWidth()
  const pageHeight = pdf.internal.pageSize.getHeight()
  const imageData = canvas.toDataURL('image/jpeg', 0.95)
  const imageHeight = (canvas.height * pageWidth) / canvas.width

  let remainingHeight = imageHeight
  let offset = 0

  pdf.addImage(imageData, 'JPEG', 0, offset, pageWidth, imageHeight)
  remainingHeight -= pageHeight

  while (remainingHeight > 0) {
    offset = remainingHeight - imageHeight
    pdf.addPage()
    pdf.addImage(imageData, 'JPEG', 0, offset, pageWidth, imageHeight)
    remainingHeight -= pageHeight
  }

  pdf.save(`${sanitizeFileName(fileName)}.pdf`)
}
