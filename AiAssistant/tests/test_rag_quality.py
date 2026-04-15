from __future__ import annotations

import unittest

from src.app.schemas.chat import AttachmentContext
from src.app.schemas.message import CitationView
from src.app.services.citation_guard_service import citation_guard_service
from src.app.services.query_rewrite_service import query_rewrite_service


class QueryRewriteServiceTest(unittest.TestCase):
    def test_build_plan_generates_rewrite_and_expansion(self) -> None:
        plan = query_rewrite_service.build_plan(
            '请帮我总结这份 PDF 文档讲了什么',
            project_scope_ids=[88],
            attachments=[AttachmentContext(attachment_type='FILE', source_id=1, project_id=88, title='测试资料')],
            mode='SCOPED',
            enabled=True,
        )

        self.assertGreaterEqual(len(plan.variants), 2)
        self.assertEqual('请帮我总结这份 PDF 文档讲了什么', plan.original_query)
        self.assertTrue(any('总结' in query for query in plan.all_queries))
        self.assertTrue(any(item.kind == 'rewrite' for item in plan.variants))
        self.assertTrue(any('主要内容' in query or '摘要' in query for query in plan.all_queries))
        self.assertTrue(any('pdf 文档' in query.lower() or '文档' in query for query in plan.expanded_queries))


class CitationGuardServiceTest(unittest.TestCase):
    def test_assess_evidence_requires_downgrade_for_weak_private_citations(self) -> None:
        assessment = citation_guard_service.assess_evidence(
            citations=[],
            confidence=0.42,
            answer_source='PRIVATE_KNOWLEDGE',
            strict_enabled=True,
        )
        self.assertFalse(assessment.evidence_sufficient)
        self.assertTrue(assessment.downgrade_required)
        self.assertIsNotNone(assessment.reason)

    def test_assess_answer_consistency_detects_low_overlap(self) -> None:
        citations = [
            CitationView(
                source_type='DOCUMENT',
                source_id=1,
                source_title='员工请假制度',
                snippet='年假申请需至少提前三个工作日提交。',
                page_no=1,
                section_path='请假流程',
                score=0.91,
                url=None,
            )
        ]
        assessment = citation_guard_service.assess_answer_consistency(
            answer='根据文档，报销审批需要 CTO 审批。',
            citations=citations,
            answer_source='PRIVATE_KNOWLEDGE',
        )
        self.assertFalse(assessment.consistency_passed)
        self.assertGreaterEqual(assessment.coverage_ratio, 0.0)
        self.assertTrue(assessment.claim_detected)
