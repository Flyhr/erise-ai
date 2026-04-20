from __future__ import annotations

import re
from dataclasses import dataclass

from src.app.schemas.chat import AttachmentContext
from src.app.services.retrieval_llm_service import QueryRewriteLlmResult, retrieval_llm_service


TOKEN_PATTERN = re.compile(r'[\u4e00-\u9fff]+|[a-zA-Z0-9_./:-]+')
LEADING_FILLER_PATTERN = re.compile(r'^(?:请问|请帮我|帮我|帮忙|麻烦|可以|能否|我想知道|我想了解|告诉我|想问下)\s*', re.IGNORECASE)
ATTACHMENT_REFERENCE_PATTERN = re.compile(r'(这份|这个|这些|上述|刚才上传的|发给你的)(文档|文件|资料|附件|pdf)?', re.IGNORECASE)
ERROR_QUERY_PATTERN = re.compile(
    r'(报错|报异常|错误|失败|异常|堆栈|日志|traceback|exception|error|failed|fatal|stack\s*trace|'
    r'nullpointerexception|sqlsyntaxerrorexception|404|500|502|503|504|code:|err:|warn:|'
    r'line \d+| at [\w.$]+|```)',
    re.IGNORECASE,
)
GUIDE_QUERY_PATTERN = re.compile(r'(怎么|如何|步骤|流程|配置|设置|使用|接入|部署|安装|创建|操作)', re.IGNORECASE)
SUMMARY_QUERY_PATTERN = re.compile(r'(总结|摘要|概述|讲了什么|主要内容|重点|结论)', re.IGNORECASE)
CONCEPT_QUERY_PATTERN = re.compile(r'(是什么|含义|概念|区别|作用|原理|为什么)', re.IGNORECASE)

STOPWORDS = {
    '请', '请问', '帮我', '帮忙', '麻烦', '可以', '能否', '这个', '这些', '这份', '那个',
    '告诉', '说明', '解释', '介绍', '一下', '是否', '怎么', '如何', '为什么', '什么',
    '哪些', 'and', 'the', 'for', 'with', 'from', 'into', 'about', 'this', 'that',
}

SYNONYM_MAP: dict[str, list[str]] = {
    '总结': ['摘要', '概述', '主要内容'],
    '概述': ['摘要', '总结'],
    '摘要': ['总结', '概述'],
    '标题': ['文档标题', '文件标题', '命名'],
    '命名': ['标题', '文件标题'],
    '风险': ['问题', '隐患', '待办'],
    '问题': ['风险', '隐患'],
    '流程': ['步骤', '处理流程'],
    'ocr': ['光学字符识别', '图片文字提取'],
    'pdf': ['pdf 文档', '文档'],
    'docx': ['word 文档', '文档'],
    'doc': ['word 文档', '文档'],
    'txt': ['文本文件', '纯文本'],
    'rag': ['检索增强生成', '知识检索'],
    '报错': ['异常', '错误日志', '故障排查'],
    '失败': ['异常', '错误', '失败原因'],
    '权限': ['授权', '访问控制', '角色'],
}

VALID_RETRIEVAL_PROFILES = {'BALANCED', 'KEYWORD_HEAVY', 'VECTOR_HEAVY'}


@dataclass(slots=True)
class QueryVariant:
    text: str
    kind: str
    boost: float


@dataclass(slots=True)
class QueryRewritePlan:
    original_query: str
    normalized_query: str
    rewritten_query: str | None
    expanded_queries: list[str]
    hints: list[str]
    variants: list[QueryVariant]
    intent: str | None = None
    retrieval_profile: str = 'BALANCED'
    llm_enhanced: bool = False

    @property
    def all_queries(self) -> list[str]:
        return [item.text for item in self.variants]


class QueryRewriteService:
    MAX_VARIANTS = 6

    async def build_plan_async(
        self,
        query: str,
        project_scope_ids: list[int] | None = None,
        attachments: list[AttachmentContext] | None = None,
        mode: str | None = None,
        enabled: bool = True,
    ) -> QueryRewritePlan:
        plan = self.build_plan(
            query,
            project_scope_ids=project_scope_ids,
            attachments=attachments,
            mode=mode,
            enabled=enabled,
        )
        if not enabled or not plan.normalized_query:
            return plan
        llm_result = await retrieval_llm_service.rewrite_query(
            query=plan.original_query or plan.normalized_query,
            project_scope_ids=project_scope_ids or [],
            attachments=attachments or [],
            mode=mode,
            fallback_profile=plan.retrieval_profile,
        )
        if llm_result is None:
            return plan
        return self._merge_llm_result(plan, llm_result)

    def build_plan(
        self,
        query: str,
        project_scope_ids: list[int] | None = None,
        attachments: list[AttachmentContext] | None = None,
        mode: str | None = None,
        enabled: bool = True,
    ) -> QueryRewritePlan:
        original = (query or '').strip()
        normalized = self._normalize_query(original)
        retrieval_profile = self._infer_retrieval_profile(normalized)
        intent = self._infer_intent(normalized)
        if not enabled or not normalized:
            return QueryRewritePlan(
                original_query=original,
                normalized_query=normalized,
                rewritten_query=None,
                expanded_queries=[],
                hints=[],
                variants=[QueryVariant(text=normalized or original, kind='original', boost=1.0)],
                intent=intent,
                retrieval_profile=retrieval_profile,
            )

        attachment_count = len(attachments or [])
        hints: list[str] = []
        if intent:
            hints.append(f'识别问题意图：{intent}')
        if retrieval_profile == 'KEYWORD_HEAVY':
            hints.append('识别为报错/日志/代码类查询，已提高关键词召回优先级')

        seen: set[str] = set()
        variants: list[QueryVariant] = []

        def add_variant(text: str, kind: str, boost: float) -> None:
            candidate = self._normalize_query(text)
            if not candidate or candidate in seen:
                return
            seen.add(candidate)
            variants.append(QueryVariant(text=candidate, kind=kind, boost=boost))

        add_variant(normalized, 'original', 1.0)

        rewritten = self._rewrite_core_query(
            normalized,
            attachment_count=attachment_count,
            has_project_scope=bool(project_scope_ids),
            mode=(mode or 'GENERAL').upper(),
        )
        if rewritten and rewritten != normalized:
            hints.append('生成了更聚焦检索意图的改写查询')
            add_variant(rewritten, 'rewrite', 0.97)

        focus_query = self._focus_query(rewritten or normalized)
        if focus_query and focus_query not in {normalized, rewritten}:
            hints.append('提取了高价值关键词用于召回')
            add_variant(focus_query, 'focus', 0.92)

        expanded_queries = self._expand_queries(rewritten or normalized, attachment_count=attachment_count)
        if expanded_queries:
            hints.append('补充了同义表达和领域术语扩展')
        for index, expanded in enumerate(expanded_queries, start=1):
            add_variant(expanded, 'expansion', max(0.75, 0.9 - (index - 1) * 0.05))
            if len(variants) >= self.MAX_VARIANTS:
                break

        if attachment_count:
            hints.append(f'已纳入附件上下文改写（附件数：{attachment_count}）')
        if project_scope_ids:
            hints.append(f'已纳入项目范围约束（项目数：{len(project_scope_ids)}）')

        return QueryRewritePlan(
            original_query=original,
            normalized_query=normalized,
            rewritten_query=rewritten if rewritten != normalized else None,
            expanded_queries=[item.text for item in variants if item.kind == 'expansion'],
            hints=hints,
            variants=variants[: self.MAX_VARIANTS],
            intent=intent,
            retrieval_profile=retrieval_profile,
        )

    def _merge_llm_result(self, plan: QueryRewritePlan, llm_result: QueryRewriteLlmResult) -> QueryRewritePlan:
        seen = {item.text for item in plan.variants}
        merged_variants = list(plan.variants)
        merged_hints = list(plan.hints)

        def add_variant(text: str, kind: str, boost: float) -> None:
            candidate = self._normalize_query(text)
            if not candidate or candidate in seen or len(merged_variants) >= self.MAX_VARIANTS:
                return
            seen.add(candidate)
            merged_variants.append(QueryVariant(text=candidate, kind=kind, boost=boost))

        if llm_result.intent and llm_result.intent != plan.intent:
            merged_hints.insert(0, f'识别问题意图：{llm_result.intent}')
        if llm_result.retrieval_profile in VALID_RETRIEVAL_PROFILES and llm_result.retrieval_profile != plan.retrieval_profile:
            if llm_result.retrieval_profile == 'KEYWORD_HEAVY':
                merged_hints.append('LLM 判定为精确术语/报错排查类查询，已增强关键词权重')
            elif llm_result.retrieval_profile == 'VECTOR_HEAVY':
                merged_hints.append('LLM 判定为语义理解类查询，已偏向语义召回')

        if llm_result.rewritten_query and llm_result.rewritten_query not in {plan.normalized_query, plan.rewritten_query}:
            merged_hints.append('使用 LLM 补充了专业术语改写查询')
            add_variant(llm_result.rewritten_query, 'llm_rewrite', 0.99)

        for index, text in enumerate(llm_result.keyword_expansions, start=1):
            add_variant(text, 'llm_expansion', max(0.84, 0.92 - (index - 1) * 0.04))
        for index, text in enumerate(llm_result.semantic_expansions, start=1):
            add_variant(text, 'llm_expansion', max(0.8, 0.9 - (index - 1) * 0.05))

        for hint in llm_result.hints:
            if hint not in merged_hints:
                merged_hints.append(hint)

        effective_rewrite = plan.rewritten_query
        if llm_result.rewritten_query:
            normalized_rewrite = self._normalize_query(llm_result.rewritten_query)
            if normalized_rewrite and normalized_rewrite != plan.normalized_query:
                effective_rewrite = normalized_rewrite

        return QueryRewritePlan(
            original_query=plan.original_query,
            normalized_query=plan.normalized_query,
            rewritten_query=effective_rewrite,
            expanded_queries=[item.text for item in merged_variants if item.kind in {'expansion', 'llm_expansion'}],
            hints=merged_hints,
            variants=merged_variants[: self.MAX_VARIANTS],
            intent=llm_result.intent or plan.intent,
            retrieval_profile=llm_result.retrieval_profile or plan.retrieval_profile,
            llm_enhanced=True,
        )

    def _normalize_query(self, query: str) -> str:
        normalized = (query or '').strip()
        normalized = LEADING_FILLER_PATTERN.sub('', normalized)
        normalized = normalized.replace('，', ' ').replace('。', ' ').replace('？', '?').replace('！', '!')
        normalized = re.sub(r'\s+', ' ', normalized).strip(' .!?')
        return normalized

    def _rewrite_core_query(self, query: str, *, attachment_count: int, has_project_scope: bool, mode: str) -> str | None:
        rewritten = ATTACHMENT_REFERENCE_PATTERN.sub('文档', query)
        rewritten = re.sub(r'(帮我|请你|请帮我)\s*', '', rewritten)
        rewritten = re.sub(r'(讲了什么|写了什么|是什么|有哪些|怎么样)$', '主要内容', rewritten)
        rewritten = re.sub(r'(总结一下|概括一下)$', '摘要', rewritten)
        rewritten = re.sub(r'\s+', ' ', rewritten).strip()

        if attachment_count and '文档' in rewritten and not any(term in rewritten for term in ('摘要', '总结', '主要内容', '结论')):
            rewritten = f'{rewritten} 摘要 主要内容'
        if has_project_scope and mode != 'SCOPED' and '项目' not in rewritten:
            rewritten = f'项目 {rewritten}'
        if ERROR_QUERY_PATTERN.search(query) and not any(term in rewritten for term in ('解决方法', '排查', '原因')):
            rewritten = f'{rewritten} 解决方法 排查 原因'

        return rewritten or None

    def _focus_query(self, query: str) -> str | None:
        tokens: list[str] = []
        for token in TOKEN_PATTERN.findall(query.lower()):
            if token in STOPWORDS or len(token) <= 1:
                continue
            tokens.append(token)
        if not tokens:
            return None
        deduped = list(dict.fromkeys(tokens))
        return ' '.join(deduped[:6])

    def _expand_queries(self, query: str, *, attachment_count: int) -> list[str]:
        lowered = query.lower()
        expansions: list[str] = []

        for token, synonyms in SYNONYM_MAP.items():
            if token not in lowered:
                continue
            for synonym in synonyms:
                if synonym.lower() in lowered:
                    continue
                expansions.append(f'{query} {synonym}')

        if attachment_count and '文档' in query and '附件' not in query:
            expansions.append(f'{query} 附件 资料')
        if '总结' in query or '摘要' in query:
            expansions.append(f'{query} 关键结论')
        if '风险' in query or '问题' in query:
            expansions.append(f'{query} 待办 建议')
        if ERROR_QUERY_PATTERN.search(query):
            expansions.append(f'{query} 错误日志 故障排查')

        deduped: list[str] = []
        seen: set[str] = set()
        for item in expansions:
            normalized = self._normalize_query(item)
            if not normalized or normalized in seen:
                continue
            seen.add(normalized)
            deduped.append(normalized)
        return deduped[: self.MAX_VARIANTS]

    def _infer_intent(self, query: str) -> str | None:
        if not query:
            return None
        if ERROR_QUERY_PATTERN.search(query):
            return '报错排查'
        if SUMMARY_QUERY_PATTERN.search(query):
            return '总结摘要'
        if GUIDE_QUERY_PATTERN.search(query):
            return '操作指南'
        if CONCEPT_QUERY_PATTERN.search(query):
            return '概念解释'
        return '知识检索'

    def _infer_retrieval_profile(self, query: str) -> str:
        if not query:
            return 'BALANCED'
        if ERROR_QUERY_PATTERN.search(query):
            return 'KEYWORD_HEAVY'
        if CONCEPT_QUERY_PATTERN.search(query) and not GUIDE_QUERY_PATTERN.search(query):
            return 'VECTOR_HEAVY'
        return 'BALANCED'


query_rewrite_service = QueryRewriteService()
