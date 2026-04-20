import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import {
  cancelRouteLoading,
  startRouteLoading,
} from "@/composables/useRouteLoading";

const LoginView = () => import("@/views/auth/LoginView.vue");
const WorkspaceView = () => import("@/views/workspace/WorkspaceView.vue");
const ProjectsView = () => import("@/views/project/ProjectsView.vue");
const ProjectDetailView = () => import("@/views/project/ProjectDetailView.vue");
const FilesView = () => import("@/views/file/FilesView.vue");
const FileDetailView = () => import("@/views/file/FileDetailView.vue");
const OfficeFileEditView = () => import("@/views/file/OfficeFileEditView.vue");
const DocumentsView = () => import("@/views/document/DocumentsView.vue");
const DocumentEditView = () => import("@/views/document/DocumentEditView.vue");
const ContentItemsView = () => import("@/views/content/ContentItemsView.vue");
const ContentItemEditView = () =>
  import("@/views/content/ContentItemEditView.vue");
const KnowledgeBaseView = () =>
  import("@/views/knowledge/KnowledgeBaseView.vue");
const SearchView = () => import("@/views/search/SearchView.vue");
const AiView = () => import("@/views/ai/AiView.vue");
const ProfileView = () => import("@/views/settings/ProfileView.vue");
const AdminLayout = () => import("@/components/layout/AdminLayout.vue");
const AdminDashboardView = () => import("@/views/admin/AdminDashboardView.vue");
const AdminProjectAssetsView = () =>
  import("@/views/admin/AdminProjectAssetsView.vue");
const AdminUsersView = () => import("@/views/admin/AdminUsersView.vue");
const AdminTasksView = () => import("@/views/admin/AdminTasksView.vue");
const AdminAuditLogsView = () => import("@/views/admin/AdminAuditLogsView.vue");
const AdminModelsView = () => import("@/views/admin/AdminModelsView.vue");
const AdminAiInfrastructureView = () =>
  import("@/views/admin/AdminAiInfrastructureView.vue");
const AdminAiPromptsView = () => import("@/views/admin/AdminAiPromptsView.vue");
const AdminAiRequestLogsView = () =>
  import("@/views/admin/AdminAiRequestLogsView.vue");
const AdminAiFeedbackView = () =>
  import("@/views/admin/AdminAiFeedbackView.vue");
const AdminAiIndexTasksView = () =>
  import("@/views/admin/AdminAiIndexTasksView.vue");
const AdminAcceptanceView = () =>
  import("@/views/admin/AdminAcceptanceView.vue");
const NotFoundView = () => import("@/views/admin/NotFoundView.vue");
const WorkspaceShellLayout = () =>
  import("@/components/common/WorkspaceShellLayout.vue");

const ROUTE_LOADING_TITLE_PREFIX = "正在打开 ";
const ROUTE_LOADING_DEFAULT_TITLE = "正在进入页面";
const ROUTE_LOADING_DESCRIPTION = "界面内容正在准备中，请稍候。";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "", redirect: "/login" },
    {
      path: "/login",
      component: LoginView,
      meta: {
        title: "登录",
        description: "进入 Erise 知识工作台。",
      },
    },
    {
      path: "/ai",
      component: AiView,
      props: true,
      meta: {
        title: "项目 AI",
        description: "基于项目上下文和引用发起 AI 对话。",
      },
    },
    {
      path: "/projects/:id/ai",
      component: AiView,
      props: true,
      meta: {
        requiresAuth: true,
        title: "项目 AI",
        description: "围绕当前项目上下文发起 AI 对话并查看引用来源。",
      },
    },
    {
      path: "/settings/profile",
      component: ProfileView,
      meta: {
        requiresAuth: true,
        title: "个人资料",
        description: "管理账号信息、密码和主题偏好。",
      },
    },
    {
      path: "/workspace",
      component: WorkspaceShellLayout,
      meta: {
        requiresAuth: true,
      },
      children: [
        {
          path: "/projects",
          component: ProjectsView,
          meta: {
            title: "项目",
            description: "按项目管理知识内容与协作工作。",
          },
        },
        {
          path: "",
          component: WorkspaceView,
          meta: {
            title: "工作台",
            description: "集中查看最近项目、文档、文件和 AI 会话。",
          },
        },
        {
          path: "/projects/:id",
          component: ProjectDetailView,
          props: true,
          meta: {
            title: "项目概览",
            description: "查看项目概览与项目级二级导航。",
          },
        },
        {
          path: "/projects/:id/files",
          component: FilesView,
          props: true,
          meta: {
            title: "项目文件",
            description: "管理当前项目下的文件与上传。",
          },
        },
        {
          path: "/projects/:id/documents",
          component: DocumentsView,
          props: true,
          meta: {
            title: "项目文档",
            description: "管理当前项目下的文档与浏览流。",
          },
        },
        {
          path: "/projects/:id/contents/:type",
          component: ContentItemsView,
          props: true,
          meta: {
            title: "表格",
            description: "在项目内管理表格、画板和数据表。",
          },
        },
        {
          path: "/knowledge",
          component: KnowledgeBaseView,
          meta: {
            title: "知识库",
            description: "集中查看知识文件与在线文档。",
          },
        },
        {
          path: "/search",
          component: SearchView,
          meta: {
            title: "搜索",
            description: "统一检索文件、文档与表格内容。",
          },
        },
        {
          path: "/workspace/search",
          redirect: (to: any) => ({ path: "/search", query: to.query }),
        },
        {
          path: "/files",
          component: FilesView,
          meta: {
            title: "文件",
            description: "跨项目浏览、筛选和上传文件。",
          },
        },
        {
          path: "/files/:id",
          component: FileDetailView,
          props: true,
          meta: {
            title: "文件详情",
          },
        },
        {
          path: "/files/:id/edit",
          component: OfficeFileEditView,
          props: true,
          meta: {
            title: "文件编辑",
            description: "在线编辑 doc、docx 和 txt 文件。",
          },
        },
        {
          path: "/documents",
          component: DocumentsView,
          meta: {
            title: "文档",
            description: "跨项目浏览、创建和预览文档。",
          },
        },
        {
          path: "/documents/new/edit",
          component: DocumentEditView,
          props: true,
          meta: {
            title: "新建文档",
            description: "在发布前先编辑本地草稿文档。",
          },
        },
        {
          path: "/documents/:id/edit",
          component: DocumentEditView,
          props: true,
          meta: {
            title: "文档编辑",
            description: "编辑、浏览和发布在线文档。",
          },
        },
        {
          path: "/contents/:id/edit",
          component: ContentItemEditView,
          props: true,
          meta: {
            title: "表格编辑",
            description: "编辑表格、画板和数据表。",
          },
        },
      ],
    },
    {
      path: "/admin",
      component: AdminLayout,
      meta: { requiresAuth: true, admin: true },
      children: [
        {
          path: "",
          component: AdminDashboardView,
          meta: {
            title: "仪表盘",
          },
        },
        {
          path: "project-files",
          component: AdminProjectAssetsView,
          meta: {
            title: "项目文件管理",
          },
        },
        {
          path: "files/:id",
          component: FileDetailView,
          props: true,
          meta: {
            title: "文件详情",
            admin: true,
          },
        },
        {
          path: "files/:id/edit",
          component: OfficeFileEditView,
          props: true,
          meta: {
            title: "文件编辑",
            admin: true,
          },
        },
        {
          path: "profile",
          component: ProfileView,
          meta: {
            title: "个人资料",
            admin: true,
          },
        },
        {
          path: "users",
          component: AdminUsersView,
          meta: {
            title: "用户管理",
          },
        },
        {
          path: "logs",
          component: AdminAuditLogsView,
          meta: {
            title: "日志管理",
          },
        },
        {
          path: "ai/infrastructure",
          component: AdminAiInfrastructureView,
          meta: {
            title: "AI 基础设施",
          },
        },
        {
          path: "ai/models",
          component: AdminModelsView,
          meta: {
            title: "AI 模型配置",
            description: "管理模型启停、默认模型、优先级与计费配置。",
          },
        },
        {
          path: "ai/prompts",
          component: AdminAiPromptsView,
          meta: {
            title: "Prompt 模板",
          },
        },
        {
          path: "ai/request-logs",
          component: AdminAiRequestLogsView,
          meta: {
            title: "AI 请求日志",
          },
        },
        {
          path: "ai/feedback",
          component: AdminAiFeedbackView,
          meta: {
            title: "用户反馈",
          },
        },
        {
          path: "ai/index-tasks",
          component: AdminAiIndexTasksView,
          meta: {
            title: "索引任务",
          },
        },
        {
          path: "acceptance",
          component: AdminAcceptanceView,
          meta: {
            title: "前端闭环与验收",
            description: "集中查看关键路径验收入口、回归建议与发布演练清单。",
          },
        },
        {
          path: "tasks",
          component: AdminTasksView,
          meta: {
            title: "任务",
            description: "查看后台任务执行状态与错误信息。",
          },
        },
        {
          path: "documents/:id/edit",
          component: DocumentEditView,
          props: true,
          meta: {
            title: "文档编辑",
            admin: true,
          },
        },
        {
          path: "contents/:id/edit",
          component: ContentItemEditView,
          props: true,
          meta: {
            title: "表格编辑",
            admin: true,
          },
        },
        { path: "models", redirect: "/admin/ai/models" },
        { path: "ai-models", redirect: "/admin/ai/models" },
        { path: "audit-logs", redirect: "/admin/logs" },
      ],
    },
    { path: "/:pathMatch(.*)*", component: NotFoundView },
  ],
});

router.beforeEach(async (to) => {
  startRouteLoading(to.fullPath, {
    title: to.meta.title
      ? `${ROUTE_LOADING_TITLE_PREFIX}${String(to.meta.title)}`
      : ROUTE_LOADING_DEFAULT_TITLE,
    description: ROUTE_LOADING_DESCRIPTION,
  });

  const authStore = useAuthStore();
  if (authStore.accessToken && !authStore.user) {
    await authStore.hydrate();
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return "/login";
  }

  if (authStore.isAdmin && to.path === "/settings/profile") {
    return "/admin/profile";
  }

  if (to.meta.admin && !authStore.isAdmin) {
    return "/workspace";
  }

  if (authStore.isAdmin && to.path === "/workspace") {
    return "/admin";
  }

  if (to.path === "/login" && authStore.isAuthenticated) {
    return authStore.isAdmin ? "/admin" : "/workspace";
  }

  return true;
});

router.onError(() => {
  cancelRouteLoading();
});

export default router;
