import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

declare const Buffer: {
  from(value: string, encoding?: string): any
}

declare const process: {
  env: Record<string, string | undefined>
}

const ACCESS_TOKEN_KEY = 'erise-access-token'
const REFRESH_TOKEN_KEY = 'erise-refresh-token'
const USER_KEY = 'erise-user'
const apiBaseURL = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:8088/api'
const apiUrl = (path: string) => `${apiBaseURL}${path}`

interface ApiEnvelope<T> {
  code: number
  msg?: string
  message?: string
  data: T
}

interface UserView {
  id: number
  username: string
  displayName: string
  email: string
  roleCode: string
}

interface AuthSession {
  accessToken: string
  refreshToken: string
  user: UserView
}

interface CaptchaView {
  captchaId: string
  captchaImage: string
}

interface ProjectView {
  id: number
  name: string
  description?: string
}

interface FileInitUploadView {
  fileId: number
}

interface FileView {
  id: number
  fileName: string
  parseStatus: string
  indexStatus: string
}

interface DocumentView {
  id: number
  title: string
  docStatus: string
}

interface SearchResultView {
  sourceId: number
  title: string
  sourceType: string
}

interface AiTempFileView {
  id: number
  sessionId: number
  projectId: number
  fileName: string
  mimeType: string
  sizeBytes: number
  parseStatus: string
  indexStatus: string
  createdAt: string
}

interface AiSessionSummaryView {
  id: number
  projectId: number
  title: string
  lastMessageAt: string
  createdAt: string
  tempFileCount: number
  recentSourceType: string
}

const unwrap = async <T>(responsePromise: Promise<import('@playwright/test').APIResponse>) => {
  const response = await responsePromise
  const rawText = await response.text()
  expect(response.ok(), rawText).toBeTruthy()
  const payload = JSON.parse(rawText) as ApiEnvelope<T>
  expect(payload.code).toBe(0)
  return payload.data
}

const decodeCaptchaCode = (captchaImage: string) => {
  const encoded = captchaImage.split(',')[1]
  const svg = atob(encoded)
  const match = svg.match(/>([A-Z0-9]{4})<\/text>/i)
  if (!match) {
    throw new Error(`Unable to decode captcha from svg: ${svg}`)
  }
  return match[1]
}

const encodeMultipartText = (value: string) => Buffer.from(value, 'utf-8')

const authHeaders = (accessToken: string) => ({
  Authorization: `Bearer ${accessToken}`,
})

const fetchCaptcha = async (request: APIRequestContext) => {
  const captcha = await unwrap<CaptchaView>(request.get(apiUrl('/v1/auth/captcha')))
  return {
    captchaId: captcha.captchaId,
    captchaCode: decodeCaptchaCode(captcha.captchaImage),
  }
}

const registerUser = async (request: APIRequestContext, seed: string) => {
  const captcha = await fetchCaptcha(request)
  const username = `u${seed.slice(-12)}`
  return unwrap<AuthSession>(
    request.post(apiUrl('/v1/auth/register'), {
      data: {
        username,
        email: `${username}@example.com`,
        password: 'Admin123!',
        displayName: `E2E User ${seed}`,
        captchaId: captcha.captchaId,
        captchaCode: captcha.captchaCode,
      },
    }),
  )
}

const loginAdmin = async (request: APIRequestContext) => {
  const captcha = await fetchCaptcha(request)
  return unwrap<AuthSession>(
    request.post(apiUrl('/v1/auth/login'), {
      data: {
        username: 'admin',
        password: 'Admin123!',
        captchaId: captcha.captchaId,
        captchaCode: captcha.captchaCode,
      },
    }),
  )
}

const createProject = async (request: APIRequestContext, accessToken: string, seed: string) =>
  unwrap<ProjectView>(
    request.post(apiUrl('/v1/projects'), {
      headers: authHeaders(accessToken),
      data: {
        name: `Acceptance Project ${seed}`,
        description: 'Project seeded by Playwright for frontend acceptance coverage.',
      },
    }),
  )

const createDocument = async (
  request: APIRequestContext,
  accessToken: string,
  projectId: number,
  seed: string,
) =>
  unwrap<DocumentView>(
    request.post(apiUrl('/v1/documents'), {
      headers: authHeaders(accessToken),
      data: {
        projectId,
        title: `Acceptance Document ${seed}`,
        summary: 'Seeded document for search and citation verification.',
      },
    }),
  )

const uploadProjectFile = async (
  request: APIRequestContext,
  accessToken: string,
  projectId: number,
  seed: string,
) => {
  const init = await unwrap<FileInitUploadView>(
    request.post(apiUrl('/v1/files/init-upload'), {
      headers: authHeaders(accessToken),
      data: {
        projectId,
        fileName: `acceptance-${seed}.txt`,
        fileSize: 96,
        mimeType: 'text/plain',
      },
    }),
  )

  await unwrap<FileView>(
    request.post(apiUrl('/v1/files/upload'), {
      headers: authHeaders(accessToken),
      multipart: {
        fileId: String(init.fileId),
        file: {
          name: `acceptance-${seed}.txt`,
          mimeType: 'text/plain',
          buffer: encodeMultipartText(`Acceptance File ${seed}\nThis file is used to verify list visibility and parsing status.`),
        },
      },
    }),
  )

  return unwrap<FileView>(
    request.post(apiUrl('/v1/files/complete-upload'), {
      headers: authHeaders(accessToken),
      data: {
        fileId: init.fileId,
      },
    }),
  )
}

const getSearchResults = async (
  request: APIRequestContext,
  accessToken: string,
  query: string,
  projectId: number,
) =>
  unwrap<{ records: SearchResultView[] }>(
    request.get(apiUrl(`/v1/search?q=${encodeURIComponent(query)}&projectId=${projectId}&pageNum=1&pageSize=20`), {
      headers: authHeaders(accessToken),
    }),
  )

const createMockAiSession = (projectId: number, seed: string): AiSessionSummaryView => {
  const now = new Date().toISOString()
  return {
    id: Number(seed.slice(-8)),
    projectId,
    title: `Acceptance AI Session ${seed}`,
    lastMessageAt: now,
    createdAt: now,
    tempFileCount: 1,
    recentSourceType: 'TEMP_FILE',
  }
}

const createMockTempFile = (sessionId: number, projectId: number, seed: string): AiTempFileView => ({
  id: Number(seed.slice(-7)),
  sessionId,
  projectId,
  fileName: `ai-temp-${seed}.txt`,
  mimeType: 'text/plain',
  sizeBytes: 96,
  parseStatus: 'READY',
  indexStatus: 'READY',
  createdAt: new Date().toISOString(),
})

const mockAiPageApis = async (
  page: Page,
  session: AiSessionSummaryView,
  document: DocumentView,
  tempFile: AiTempFileView,
) => {
  const createdAt = new Date().toISOString()

  await page.route('**/api/v1/ai/models', async (route) => {
    await route.fulfill({
      json: {
        code: 0,
        data: [
          {
            providerCode: 'OPENAI',
            modelCode: 'gpt-5.4-mini',
            modelName: 'GPT-5.4 Mini',
            isDefault: true,
            supportStream: true,
            maxContextTokens: 128000,
          },
        ],
      },
    })
  })

  await page.route('**/api/v1/ai/settings/retrieval', async (route) => {
    await route.fulfill({
      json: {
        code: 0,
        data: {
          similarityThreshold: 0.65,
          topK: 5,
          webSearchEnabledDefault: false,
        },
      },
    })
  })

  await page.route('**/api/v1/ai/sessions', async (route) => {
    await route.fulfill({
      json: {
        code: 0,
        data: [session],
      },
    })
  })

  await page.route(`**/api/v1/ai/sessions/${session.id}`, async (route) => {
    await route.fulfill({
      json: {
        code: 0,
        data: {
          id: session.id,
          projectId: session.projectId,
          title: session.title,
          messages: [
            {
              id: session.id * 10 + 1,
              roleCode: 'USER',
              content: 'What is the title of the attached document?',
              citations: [],
              createdAt,
              status: 'sent',
            },
            {
              id: session.id * 10 + 2,
              roleCode: 'ASSISTANT',
              content: `The attached document title is ${document.title}.`,
              citations: [
                {
                  sourceType: 'DOCUMENT',
                  sourceId: document.id,
                  sourceTitle: document.title,
                  snippet: 'This document is used to verify AI citations and search results.',
                  pageNo: 1,
                  score: 0.93,
                },
              ],
              createdAt,
              status: 'sent',
              modelCode: 'gpt-5.4-mini',
              providerCode: 'OPENAI',
              latencyMs: 812,
            },
          ],
        },
      },
    })
  })

  await page.route('**/api/v1/ai/temp-files?*', async (route) => {
    await route.fulfill({
      json: {
        code: 0,
        data: [tempFile],
      },
    })
  })
}

const applySession = async (page: Page, session: AuthSession) => {
  await page.goto('/login')
  await page.evaluate(
    ({ accessToken, refreshToken, user }) => {
      window.localStorage.setItem('erise-access-token', accessToken)
      window.localStorage.setItem('erise-refresh-token', refreshToken)
      window.localStorage.setItem('erise-user', JSON.stringify(user))
    },
    session,
  )
}

test.describe('前端闭环验收', () => {
  test('普通用户关键路径可验收', async ({ page, request }) => {
    const seed = `${Date.now()}`
    const session = await registerUser(request, seed)
    const project = await createProject(request, session.accessToken, seed)
    const document = await createDocument(request, session.accessToken, project.id, seed)
    const file = await uploadProjectFile(request, session.accessToken, project.id, seed)
    const aiSession = createMockAiSession(project.id, seed)
    const tempFile = createMockTempFile(aiSession.id, project.id, seed)

    await expect
      .poll(async () => {
        const result = await getSearchResults(request, session.accessToken, document.title, project.id)
        return result.records.some((item) => item.sourceId === document.id)
      })
      .toBeTruthy()

    await applySession(page, session)

    await page.goto('/workspace')
    await expect(page).toHaveURL(/\/workspace$/)
    await expect(page.locator('body')).toContainText('项目活动')
    await expect(page.locator('[title="管理后台"]')).toHaveCount(0)

    await page.goto('/projects')
    await expect(page.getByText(project.name)).toBeVisible()

    await page.goto(`/projects/${project.id}`)
    await expect(page.locator('body')).toContainText(project.name)
    await expect(page.locator('body')).toContainText('项目 AI')

    await page.goto(`/projects/${project.id}/documents`)
    await expect(page.locator('body')).toContainText(document.title)

    await page.goto(`/projects/${project.id}/files`)
    await expect(page.locator('body')).toContainText(file.fileName)

    await page.goto(`/search?q=${encodeURIComponent(document.title)}&projectId=${project.id}`)
    await expect(page.locator('body')).toContainText('搜索结果')
    await expect(page.locator('body')).toContainText(document.title)

    await mockAiPageApis(page, aiSession, document, tempFile)
    await page.goto(`/projects/${project.id}/ai?sessionId=${aiSession.id}`)
    await expect(page.locator('body')).toContainText('会话元信息')
    await expect(page.locator('body')).toContainText('模型 / Provider')
    await expect(page.locator('body')).toContainText('索引状态')
    await expect(page.locator('body')).toContainText(document.title)
    await expect(page.locator('body')).toContainText(tempFile.fileName)

    await page.getByRole('button', { name: /新对话|新建对话/ }).first().click()
    await expect(page.getByRole('dialog')).toContainText('新建对话')
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).toBeHidden()

    await page.locator('.thread-item__delete').first().click()
    await expect(page.getByRole('dialog')).toContainText('删除会话')
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).toBeHidden()
  })

  test('管理员入口与后台关键路径可验收', async ({ page, request }) => {
    const adminSession = await loginAdmin(request)
    await applySession(page, adminSession)

    await page.goto('/ai')
    const adminEntry = page.locator('[title="管理后台"]').first()
    await expect(adminEntry).toBeVisible()
    await adminEntry.click()

    await expect(page).toHaveURL(/\/admin$/)
    await expect(page.locator('body')).toContainText('AI 管理后台')

    await page.goto('/admin/ai/models')
    await expect(page.locator('body')).toContainText('模型配置')

    await page.goto('/admin/ai/index-tasks')
    await expect(page.locator('body')).toContainText('索引任务')

    await page.goto('/admin/acceptance')
    await expect(page.locator('body')).toContainText('前端闭环与验收')
    await expect(page.locator('body')).toContainText('发布演练清单')
  })
})
