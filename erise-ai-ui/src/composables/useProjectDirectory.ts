import { computed, ref } from 'vue'
import { getProjects } from '@/api/project'
import type { ProjectDetailView } from '@/types/models'

export const useProjectDirectory = () => {
  const projects = ref<ProjectDetailView[]>([])
  const loadingProjects = ref(false)

  const projectMap = computed(() => new Map(projects.value.map((project) => [project.id, project])))

  const projectOptions = computed(() =>
    projects.value.map((project) => ({
      label: project.name,
      value: project.id,
      description: project.description || '',
    })),
  )

  const loadProjects = async (pageSize = 100) => {
    if (loadingProjects.value) return
    loadingProjects.value = true
    try {
      const page = await getProjects({ pageNum: 1, pageSize })
      projects.value = page.records
    } finally {
      loadingProjects.value = false
    }
  }

  const projectLabel = (projectId?: number) => {
    if (!projectId) {
      return '未选择项目'
    }
    return projectMap.value.get(projectId)?.name || `项目 #${projectId}`
  }

  return {
    projects,
    loadingProjects,
    projectMap,
    projectOptions,
    loadProjects,
    projectLabel,
  }
}
