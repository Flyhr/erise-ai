from __future__ import annotations

import re
from dataclasses import dataclass

from src.app.schemas.message import CitationView

TOKEN_PATTERN = re.compile(r'[\u4e00-\u9fff]+|[a-zA-Z0-9_]+')
DOCUMENT_CLAIM_PATTERN = re.compile(
    r'(根据|依据|结合|参考).{0,8}(文档|资料|附件|文件|PDF)|'
    r'(文档|资料|附件|文件|PDF).{0,8}(显示|指出|表明|写到|说明)|'
    r'(as documented|according to the document|the attachment shows)',
    re.IGNORECASE,
)
STOPWORDS = {
    '请', '请问', '一下', '这个', '那个', '这些', '那些', '以及', '并且', '或者', '我们', '你们',
    'the', 'and', 'for', 'with', 'this', 'that', 'from', 'into', 'about',
}


@dataclass(slots=True)
class CitationGuardAssessment:
    evidence_sufficient: bool
    consistency_passed: bool
    downgrade_required: bool
    coverage_ratio: float
    claim_detected: bool
    reason: str | None

    def as_dict(self) -> dict[str, object]:
        return {
            'evidenceSufficient': self.evidence_sufficient,
            'consistencyPassed': self.consistency_passed,
            'downgradeRequired': self.downgrade_required,
            'coverageRatio': round(self.coverage_ratio, 4),
            'claimDetected': self.claim_detected,
            'reason': self.reason,
        }


class CitationGuardService:
    STRICT_CONFIDENCE_THRESHOLD = 0.72
    MIN_CITATION_COUNT = 1
    CONSISTENCY_THRESHOLD = 0.18

    def assess_evidence(
        self,
        citations: list[CitationView],
        confidence: float | None,
        answer_source: str | None,
        strict_enabled: bool,
    ) -> CitationGuardAssessment:
        citation_count = len(citations)
        max_score = max((citation.score or 0.0) for citation in citations) if citations else 0.0
        evidence_sufficient = (
            answer_source != 'PRIVATE_KNOWLEDGE'
            or (
                citation_count >= self.MIN_CITATION_COUNT
                and (confidence or 0.0) >= self.STRICT_CONFIDENCE_THRESHOLD
                and max_score >= self.CONSISTENCY_THRESHOLD
            )
        )
        downgrade_required = strict_enabled and not evidence_sufficient and answer_source == 'PRIVATE_KNOWLEDGE'
        reason = None
        if downgrade_required:
            if citation_count < self.MIN_CITATION_COUNT:
                reason = '当前没有足够的私有资料引用，不能直接依据某份文档下结论。'
            elif (confidence or 0.0) < self.STRICT_CONFIDENCE_THRESHOLD:
                reason = '当前命中的私有资料置信度偏低，不能直接依据某份文档下结论。'
            else:
                reason = '当前引用证据不足以支撑确定性结论。'
        return CitationGuardAssessment(
            evidence_sufficient=evidence_sufficient,
            consistency_passed=True,
            downgrade_required=downgrade_required,
            coverage_ratio=1.0 if evidence_sufficient else max_score,
            claim_detected=False,
            reason=reason,
        )

    def assess_answer_consistency(
        self,
        answer: str,
        citations: list[CitationView],
        answer_source: str | None,
    ) -> CitationGuardAssessment:
        answer_tokens = self._normalize_tokens(answer)
        citation_tokens = self._normalize_tokens(' '.join(self._citation_texts(citations)))
        claim_detected = bool(DOCUMENT_CLAIM_PATTERN.search(answer or ''))
        if not answer_tokens:
            coverage_ratio = 1.0
        elif not citation_tokens:
            coverage_ratio = 0.0
        else:
            matched = {token for token in answer_tokens if token in citation_tokens}
            coverage_ratio = len(matched) / max(1, len(answer_tokens))

        consistency_passed = coverage_ratio >= self.CONSISTENCY_THRESHOLD or (answer_source != 'PRIVATE_KNOWLEDGE' and not claim_detected)
        reason = None
        if not consistency_passed:
            if not citations:
                reason = '回答包含文档式结论，但没有对应引用。'
            elif claim_detected:
                reason = '回答存在明显的文档依据表述，但与引用片段的重合度不足。'
            else:
                reason = '回答与引用片段的语义重合度偏低，需要人工复核。'

        return CitationGuardAssessment(
            evidence_sufficient=bool(citations),
            consistency_passed=consistency_passed,
            downgrade_required=False,
            coverage_ratio=coverage_ratio,
            claim_detected=claim_detected,
            reason=reason,
        )

    def build_downgraded_answer(self, citations: list[CitationView], reason: str | None) -> str:
        lines = [
            '当前检索到的资料证据不足，不能直接依据某份文档给出确定结论。',
        ]
        if reason:
            lines.append(reason)
        if citations:
            lines.append('')
            lines.append('目前只能确认以下相关片段：')
            for citation in citations[:2]:
                snippet = (citation.snippet or '').strip()
                if snippet:
                    lines.append(f'- 《{citation.source_title}》：{snippet}')
                else:
                    lines.append(f'- 《{citation.source_title}》：已命中，但暂无可展示摘录。')
        else:
            lines.append('建议缩小问题范围、补充附件，或换一个更具体的检索问法后重试。')
        return '\n'.join(lines).strip()

    def _citation_texts(self, citations: list[CitationView]) -> list[str]:
        items: list[str] = []
        for citation in citations:
            parts = [citation.source_title, citation.section_path or '', citation.snippet or '']
            items.append(' '.join(part for part in parts if part))
        return items

    def _normalize_tokens(self, text: str) -> set[str]:
        tokens = set()
        for token in TOKEN_PATTERN.findall((text or '').lower()):
            normalized = token.strip()
            if not normalized or normalized in STOPWORDS or len(normalized) <= 1:
                continue
            tokens.add(normalized)
        return tokens


citation_guard_service = CitationGuardService()
