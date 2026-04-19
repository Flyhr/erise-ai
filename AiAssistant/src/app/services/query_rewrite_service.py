from __future__ import annotations

import re
from dataclasses import dataclass

from src.app.schemas.chat import AttachmentContext

TOKEN_PATTERN = re.compile(r'[\u4e00-\u9fff]+|[a-zA-Z0-9_]+')
LEADING_FILLER_PATTERN = re.compile(r'^(?:请问|请|帮我|帮忙|麻烦|可以|能否|我想知道|我想了解|告诉我|请帮我)\s*', re.IGNORECASE)
ATTACHMENT_REFERENCE_PATTERN = re.compile(r'(这份|这个|这些|该|上述|刚才上传的|发送给你的)(文档|文件|资料|附件|pdf)?', re.IGNORECASE)

STOPWORDS = {
    '请', '请问', '帮我', '帮忙', '麻烦', '可以', '能否', '一下', '一个', '一些', '这些', '这个', '这份', '那个',
    '请你', '告诉', '告诉我', '说明', '解释', '介绍', '一下子', '是否', '怎么', '如何', '为什么', '什么', '哪些',
    '一下', 'and', 'the', 'for', 'with', 'from', 'into', 'about', 'this', 'that',
}

SYNONYM_MAP: dict[str, list[str]] = {
    'release': ['delivery', 'launch', 'ship'],
    'risk': ['issue', 'blocker', 'hazard'],
    'approval': ['review', 'signoff', 'audit'],
    'handoff': ['delivery', 'transfer', 'transition'],
    'dependency': ['blocker', 'upstream', 'prerequisite'],
    'rollback': ['restore', 'revert', 'recovery'],
    'security': ['permission', 'access control', 'audit'],
    'deadline': ['due date', 'schedule', 'milestone'],
    'budget': ['cost', 'expense', 'spend'],
    'report': ['summary', 'weekly update', 'status'],
    '总结': ['摘要', '概述', '主要内容'],
    '概括': ['摘要', '概述'],
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
}


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

    @property
    def all_queries(self) -> list[str]:
        return [item.text for item in self.variants]


class QueryRewriteService:
    MAX_VARIANTS = 6

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
        if not enabled or not normalized:
            return QueryRewritePlan(
                original_query=original,
                normalized_query=normalized,
                rewritten_query=None,
                expanded_queries=[],
                hints=[],
                variants=[QueryVariant(text=normalized or original, kind='original', boost=1.0)],
            )

        attachment_count = len(attachments or [])
        hints: list[str] = []
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
            hints.append('生成了聚焦检索意图的重写查询')
            add_variant(rewritten, 'rewrite', 0.97)

        focus_query = self._focus_query(rewritten or normalized)
        if focus_query and focus_query not in {normalized, rewritten}:
            hints.append('提取了高价值关键词用于召回')
            add_variant(focus_query, 'focus', 0.92)

        expanded_queries = self._expand_queries(rewritten or normalized, attachment_count=attachment_count)
        if expanded_queries:
            hints.append('补充了同义表达和领域扩展词')
        for index, expanded in enumerate(expanded_queries, start=1):
            add_variant(expanded, 'expansion', max(0.75, 0.9 - (index - 1) * 0.05))
            if len(variants) >= self.MAX_VARIANTS:
                break

        if attachment_count:
            hints.append(f'附加资料上下文已纳入查询改写（附件数：{attachment_count}）')
        if project_scope_ids:
            hints.append(f'项目范围已纳入查询改写（项目数：{len(project_scope_ids)}）')

        return QueryRewritePlan(
            original_query=original,
            normalized_query=normalized,
            rewritten_query=rewritten if rewritten != normalized else None,
            expanded_queries=[item.text for item in variants if item.kind == 'expansion'],
            hints=hints,
            variants=variants[: self.MAX_VARIANTS],
        )

    def _normalize_query(self, query: str) -> str:
        normalized = (query or '').strip()
        normalized = LEADING_FILLER_PATTERN.sub('', normalized)
        normalized = normalized.replace('？', '?').replace('！', '!').replace('，', ' ').replace('。', ' ')
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

        return rewritten or None

    def _focus_query(self, query: str) -> str | None:
        tokens = []
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

        deduped: list[str] = []
        seen: set[str] = set()
        for item in expansions:
            normalized = self._normalize_query(item)
            if not normalized or normalized in seen:
                continue
            seen.add(normalized)
            deduped.append(normalized)
        return deduped[: self.MAX_VARIANTS]


query_rewrite_service = QueryRewriteService()
