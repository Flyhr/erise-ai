from __future__ import annotations

import argparse
import asyncio
import json
import math
import os
import sys
from time import perf_counter
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

# Offline RAG evaluation should be runnable on a fresh local machine without
# requiring a configured external embedding provider.
os.environ.setdefault('EMBEDDING_LOCAL_FALLBACK_ENABLED', 'true')

from src.app.schemas.chat import AttachmentContext  # noqa: E402
from src.app.schemas.message import CitationView  # noqa: E402
from src.app.schemas.rag import RagQueryHit  # noqa: E402
from src.app.services.citation_guard_service import citation_guard_service  # noqa: E402
from src.app.services.embedding_service import embedding_service  # noqa: E402
from src.app.services.query_rewrite_service import query_rewrite_service  # noqa: E402
from src.app.services.rag_service import rag_service  # noqa: E402


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Run offline RAG quality evaluation for Erise-AI.')
    parser.add_argument(
        '--dataset',
        default=str(ROOT / 'scripts' / 'eval_data' / 'rag_eval_minimal.json'),
        help='Path to the evaluation dataset JSON file.',
    )
    parser.add_argument('--top-k', type=int, default=3, help='Top-K used for aggregate metrics.')
    parser.add_argument('--report-out', help='Optional path to write the JSON evaluation report.')
    return parser.parse_args()


def normalize_tokens(text: str) -> list[str]:
    return [token for token in citation_guard_service._normalize_tokens(text)]


def cosine_similarity(left: list[float], right: list[float]) -> float:
    dot = sum(a * b for a, b in zip(left, right, strict=False))
    left_norm = math.sqrt(sum(a * a for a in left)) or 1.0
    right_norm = math.sqrt(sum(b * b for b in right)) or 1.0
    return dot / (left_norm * right_norm)


def keyword_score(query: str, document: dict[str, object]) -> float:
    query_text = str(query or '')
    query_tokens = normalize_tokens(query_text)
    source_title = str(document.get('sourceTitle') or '')
    section_path = str(document.get('sectionPath') or '')
    snippet = str(document.get('snippet') or '')
    document_text = ' '.join((source_title, section_path, snippet))
    document_tokens = Counter(normalize_tokens(document_text))
    if not query_tokens or not document_tokens:
        return 0.0
    score = 0.0
    for token in query_tokens:
        score += 1.5 if document_tokens[token] else 0.0
    if source_title and source_title in query_text:
        score += 4.0
    if section_path and section_path in query_text:
        score += 3.0
    for token in query_tokens:
        if token in source_title:
            score += 1.5
        if token in section_path:
            score += 1.0
    return score / max(1, len(query_tokens))


def to_hit(document: dict[str, object], score: float) -> RagQueryHit:
    return RagQueryHit(
        score=score,
        source_type=str(document['sourceType']),
        source_id=int(document['sourceId']),
        source_title=str(document['sourceTitle']),
        snippet=str(document['snippet']),
        page_no=int(document['pageNo']) if document.get('pageNo') is not None else None,
        section_path=str(document.get('sectionPath') or '') or None,
        url=str(document.get('url') or '') or None,
    )


async def rank_case(case: dict[str, object], documents: list[dict[str, object]], top_k: int) -> dict[str, object]:
    started_at = perf_counter()
    attachments = [
        AttachmentContext(
            attachment_type=str(item.get('attachmentType', 'DOCUMENT')),
            source_id=int(item.get('sourceId', 0)),
            project_id=item.get('projectId'),
            session_id=item.get('sessionId'),
            title=item.get('title'),
        )
        for item in case.get('attachments', [])
    ]
    plan = query_rewrite_service.build_plan(
        str(case['query']),
        project_scope_ids=[int(item) for item in case.get('projectScopeIds', [])],
        attachments=attachments,
        mode=str(case.get('mode') or 'GENERAL'),
        enabled=bool(case.get('queryRewriteEnabled', True)),
    )

    variant_embeddings = await embedding_service.embed([variant.text for variant in plan.variants])
    document_texts = [' '.join(str(document.get(key) or '') for key in ('sourceTitle', 'sectionPath', 'snippet')) for document in documents]
    document_embeddings = await embedding_service.embed(document_texts)

    vector_hits: list[RagQueryHit] = []
    keyword_hits: list[RagQueryHit] = []

    for variant, variant_vector in zip(plan.variants, variant_embeddings, strict=False):
        scored_vectors = []
        for document, doc_vector in zip(documents, document_embeddings, strict=False):
            scored_vectors.append((cosine_similarity(variant_vector, doc_vector) * variant.boost, document))
        for score, document in sorted(scored_vectors, key=lambda item: item[0], reverse=True)[: max(top_k, 6)]:
            vector_hits.append(to_hit(document, score))

        scored_keywords = []
        for document in documents:
            scored_keywords.append((keyword_score(variant.text, document) * variant.boost, document))
        for score, document in sorted(scored_keywords, key=lambda item: item[0], reverse=True)[: max(top_k, 6)]:
            if score <= 0:
                continue
            keyword_hits.append(to_hit(document, score))

    fused_hits = rag_service._fuse_hits(vector_hits, keyword_hits)
    reranked_hits = rag_service._fallback_hits(fused_hits)[:top_k]
    citations = [rag_service._to_citation(hit) for hit in reranked_hits]
    confidence = reranked_hits[0].score if reranked_hits else 0.0
    evidence = citation_guard_service.assess_evidence(
        citations=citations,
        confidence=confidence,
        answer_source='PRIVATE_KNOWLEDGE',
        strict_enabled=True,
    )
    consistency = citation_guard_service.assess_answer_consistency(
        answer=str(case['referenceAnswer']),
        citations=citations,
        answer_source='PRIVATE_KNOWLEDGE',
    )

    expected_source_ids = {int(item) for item in case.get('expectedSourceIds', [])}
    top_hit_id = reranked_hits[0].source_id if reranked_hits else None
    topk_hit = any(hit.source_id in expected_source_ids for hit in reranked_hits[:top_k])
    citation_source_ids = {citation.source_id for citation in citations}
    citation_accurate = bool(expected_source_ids and citation_source_ids & expected_source_ids and consistency.consistency_passed)
    latency_ms = max(1, int((perf_counter() - started_at) * 1000))

    return {
        'caseId': case['id'],
        'query': case['query'],
        'rewrittenQueries': plan.all_queries,
        'rewriteHints': plan.hints,
        'topHitSourceId': top_hit_id,
        'topHitSourceTitle': reranked_hits[0].source_title if reranked_hits else None,
        'hitAt1': top_hit_id in expected_source_ids if top_hit_id is not None else False,
        'hitAtTopK': topk_hit,
        'citationAccurate': citation_accurate,
        'latencyMs': latency_ms,
        'confidence': round(confidence, 4) if confidence else 0.0,
        'citationCoverageRatio': round(consistency.coverage_ratio, 4),
        'weakEvidence': evidence.downgrade_required or not consistency.consistency_passed,
        'evidenceGuard': evidence.as_dict(),
        'consistencyCheck': consistency.as_dict(),
        'citations': [citation.model_dump(by_alias=True) for citation in citations],
    }


async def run_evaluation(dataset_path: Path, top_k: int) -> dict[str, object]:
    payload = json.loads(dataset_path.read_text(encoding='utf-8'))
    documents = payload['documents']
    cases = payload['cases']

    results = [await rank_case(case, documents, int(case.get('topK', top_k))) for case in cases]
    total = len(results) or 1
    hit_at_1 = sum(1 for item in results if item['hitAt1']) / total
    hit_at_top_k = sum(1 for item in results if item['hitAtTopK']) / total
    avg_citation_coverage = sum(float(item['citationCoverageRatio']) for item in results) / total
    weak_evidence_ratio = sum(1 for item in results if item['weakEvidence']) / total
    citation_accuracy = sum(1 for item in results if item['citationAccurate']) / total
    latencies = sorted(int(item['latencyMs']) for item in results)
    p95_index = min(len(latencies) - 1, max(0, math.ceil(len(latencies) * 0.95) - 1)) if latencies else 0

    return {
        'dataset': str(dataset_path),
        'caseCount': len(results),
        'metrics': {
            'hitRateAt1': round(hit_at_1, 4),
            'hitRateAtTopK': round(hit_at_top_k, 4),
            'citationAccuracy': round(citation_accuracy, 4),
            'averageCitationCoverage': round(avg_citation_coverage, 4),
            'weakEvidenceRatio': round(weak_evidence_ratio, 4),
            'averageLatencyMs': round(sum(latencies) / total, 2) if latencies else 0,
            'p95LatencyMs': latencies[p95_index] if latencies else 0,
        },
        'cases': results,
    }


def main() -> int:
    args = parse_args()
    report = asyncio.run(run_evaluation(Path(args.dataset), args.top_k))
    output = json.dumps(report, ensure_ascii=False, indent=2)
    print(output)
    if args.report_out:
        Path(args.report_out).write_text(output, encoding='utf-8')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
