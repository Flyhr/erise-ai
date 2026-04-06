<template>
    <nav v-if="items.length > 1" class="app-breadcrumb">
        <ol>
            <li v-for="(item, index) in items" :key="index">
                <router-link v-if="item.to" :to="item.to" class="app-breadcrumb__link">
                    {{ item.label }}
                </router-link>
                <span v-else class="app-breadcrumb__current">
                    {{ item.label }}
                </span>
                <span v-if="index < items.length - 1" class="app-breadcrumb__sep">
                    <el-icon size="14">
                        <ArrowRight />
                    </el-icon>
                </span>
            </li>
        </ol>
    </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElIcon } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'

interface BreadcrumbItem {
    label: string
    to?: string
}

interface Props {
    items?: BreadcrumbItem[]
}

const route = useRoute()
const router = useRouter()

const props = withDefaults(defineProps<Props>(), {
    items: () => [],
})

// 如果没有传入 items，自动从路由生成
const breadcrumbs = computed(() => {
    if (props.items.length > 0) {
        return props.items
    }

    const items: BreadcrumbItem[] = [{ label: '工作台', to: '/workspace' }]
    const path = route.path

    if (path.startsWith('/projects')) {
        items.push({ label: '项目' })
        // 如果是项目详情页，添加项目名称
        if (route.params.id && path !== '/projects') {
            // 这里可以从路由 meta 或 query 中获取项目名称
            const projectName = route.meta.projectName || `项目 #${route.params.id}`
            items.push({ label: projectName as string })
        }
    } else if (path.startsWith('/knowledge')) {
        items.push({ label: '知识库' })
    } else if (path.startsWith('/search')) {
        items.push({ label: '搜索结果' })
    } else if (path.startsWith('/ai')) {
        items.push({ label: 'AI 助理' })
    }

    return items
})
</script>

<style scoped>
.app-breadcrumb {
    margin-bottom: 16px;
    font-size: 13px;
}

.app-breadcrumb ol {
    display: flex;
    align-items: center;
    gap: 4px;
    list-style: none;
    padding: 0;
    margin: 0;
}

.app-breadcrumb li {
    display: flex;
    align-items: center;
    gap: 4px;
}

.app-breadcrumb__link {
    color: var(--link);
    text-decoration: none;
    cursor: pointer;
    transition: color 0.2s ease;
}

.app-breadcrumb__link:hover {
    color: var(--link-hover, var(--primary));
    text-decoration: underline;
}

.app-breadcrumb__current {
    color: var(--text-secondary);
}

.app-breadcrumb__sep {
    display: flex;
    align-items: center;
    color: var(--text-tertiary);
    margin: 0 2px;
}
</style>
