import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AuthLayout from '@/components/layout/AuthLayout.vue'
import MainLayout from '@/components/layout/MainLayout.vue'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import LoginView from '@/views/auth/LoginView.vue'
import WorkspaceView from '@/views/workspace/WorkspaceView.vue'
import ProjectsView from '@/views/project/ProjectsView.vue'
import ProjectDetailView from '@/views/project/ProjectDetailView.vue'
import FilesView from '@/views/file/FilesView.vue'
import FileDetailView from '@/views/file/FileDetailView.vue'
import OfficeFileEditView from '@/views/file/OfficeFileEditView.vue'
import DocumentsView from '@/views/document/DocumentsView.vue'
import DocumentEditView from '@/views/document/DocumentEditView.vue'
import ContentItemsView from '@/views/content/ContentItemsView.vue'
import ContentItemEditView from '@/views/content/ContentItemEditView.vue'
import SearchView from '@/views/search/SearchView.vue'
import AiView from '@/views/ai/AiView.vue'
import ProfileView from '@/views/settings/ProfileView.vue'
import AdminDashboardView from '@/views/admin/AdminDashboardView.vue'
import AdminUsersView from '@/views/admin/AdminUsersView.vue'
import AdminTasksView from '@/views/admin/AdminTasksView.vue'
import AdminAuditLogsView from '@/views/admin/AdminAuditLogsView.vue'
import AdminModelsView from '@/views/admin/AdminModelsView.vue'
import NotFoundView from '@/views/admin/NotFoundView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      component: AuthLayout,
      children: [{ path: '', component: LoginView }],
    },
    {
      path: '/',
      component: MainLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/workspace' },
        { path: 'workspace', component: WorkspaceView },
        { path: 'projects', component: ProjectsView },
        { path: 'projects/:id', component: ProjectDetailView, props: true },
        { path: 'projects/:id/files', component: FilesView, props: true },
        { path: 'projects/:id/documents', component: DocumentsView, props: true },
        { path: 'projects/:id/contents/:type', component: ContentItemsView, props: true },
        { path: 'projects/:id/ai', component: AiView, props: true },
        { path: 'files/:id', component: FileDetailView, props: true },
        { path: 'files/:id/edit', component: OfficeFileEditView, props: true },
        { path: 'documents/:id/edit', component: DocumentEditView, props: true },
        { path: 'contents/:id/edit', component: ContentItemEditView, props: true },
        { path: 'search', component: SearchView },
        { path: 'ai', component: AiView },
        { path: 'settings/profile', component: ProfileView },
      ],
    },
    {
      path: '/admin',
      component: AdminLayout,
      meta: { requiresAuth: true, admin: true },
      children: [
        { path: '', component: AdminDashboardView },
        { path: 'users', component: AdminUsersView },
        { path: 'tasks', component: AdminTasksView },
        { path: 'audit-logs', component: AdminAuditLogsView },
        { path: 'ai-models', component: AdminModelsView },
      ],
    },
    { path: '/:pathMatch(.*)*', component: NotFoundView },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (authStore.accessToken && !authStore.user) {
    await authStore.hydrate()
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return '/login'
  }

  if (to.meta.admin && !authStore.isAdmin) {
    return '/workspace'
  }

  if (to.path === '/login' && authStore.isAuthenticated) {
    return '/workspace'
  }

  return true
})

export default router