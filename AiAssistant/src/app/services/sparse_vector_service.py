from __future__ import annotations

import hashlib
import math
import re
from collections import OrderedDict

from qdrant_client.models import SparseVector


ASCII_TOKEN_PATTERN = re.compile(r'[a-z0-9][a-z0-9_./:-]{1,63}', re.IGNORECASE)
HAN_BLOCK_PATTERN = re.compile(r'[\u4e00-\u9fff]{2,}')
STOPWORDS = {
    '的', '了', '和', '是', '在', '中', '及', '或', '就', '都', '而', '与', '以及', '一个',
    '这个', '那个', '这些', '请问', '帮我', '如何', '怎么', '什么', '一下',
    'the', 'and', 'for', 'with', 'from', 'into', 'that', 'this', 'are', 'was',
}


class SparseVectorService:
    SPARSE_HASH_BUCKETS = 1_048_576
    TITLE_WEIGHT = 6.0
    SECTION_WEIGHT = 3.0
    BODY_WEIGHT = 1.0
    MAX_UNIQUE_TERMS = 160
    MAX_QUERY_TERMS = 32

    def build_document_vector(self, *, source_title: str | None, section_path: str | None, chunk_text: str | None) -> SparseVector:
        term_weights: OrderedDict[str, float] = OrderedDict()
        self._merge_weighted_terms(term_weights, self._extract_terms(source_title, include_ngrams=True), self.TITLE_WEIGHT)
        self._merge_weighted_terms(term_weights, self._extract_terms(section_path, include_ngrams=True), self.SECTION_WEIGHT)
        self._merge_weighted_terms(term_weights, self._extract_terms(chunk_text, include_ngrams=False), self.BODY_WEIGHT)
        return self._to_sparse_vector(term_weights, self.MAX_UNIQUE_TERMS)

    def build_query_vector(self, query: str) -> SparseVector:
        term_weights: OrderedDict[str, float] = OrderedDict()
        self._merge_weighted_terms(term_weights, self._extract_terms(query, include_ngrams=True), 1.0)
        return self._to_sparse_vector(term_weights, self.MAX_QUERY_TERMS)

    def _extract_terms(self, text: str | None, *, include_ngrams: bool) -> list[str]:
        normalized = self._normalize(text)
        if not normalized:
            return []
        tokens: list[str] = []
        for match in ASCII_TOKEN_PATTERN.findall(normalized):
            token = match.lower()
            if token in STOPWORDS:
                continue
            tokens.append(token)
        for block in HAN_BLOCK_PATTERN.findall(normalized):
            if block in STOPWORDS:
                continue
            tokens.append(block)
            if include_ngrams:
                tokens.extend(self._han_ngrams(block))
        deduped: list[str] = []
        seen: set[str] = set()
        for token in tokens:
            cleaned = token.strip().lower()
            if not cleaned or cleaned in STOPWORDS or cleaned in seen:
                continue
            seen.add(cleaned)
            deduped.append(cleaned)
        return deduped

    def _han_ngrams(self, block: str) -> list[str]:
        ngrams: list[str] = []
        upper_bound = min(len(block), 10)
        for size in (2, 3):
            if upper_bound < size:
                continue
            for start in range(0, upper_bound - size + 1):
                ngram = block[start:start + size]
                if ngram not in STOPWORDS:
                    ngrams.append(ngram)
        return ngrams

    def _merge_weighted_terms(self, accumulator: OrderedDict[str, float], terms: list[str], weight: float) -> None:
        for term in terms:
            accumulator[term] = accumulator.get(term, 0.0) + weight

    def _to_sparse_vector(self, term_weights: OrderedDict[str, float], limit: int) -> SparseVector:
        if not term_weights:
            return SparseVector(indices=[], values=[])
        ranked_terms = list(term_weights.items())[:limit]
        norm = math.sqrt(sum(weight * weight for _, weight in ranked_terms)) or 1.0
        index_to_value: dict[int, float] = {}
        for term, weight in ranked_terms:
            index = self._term_index(term)
            index_to_value[index] = index_to_value.get(index, 0.0) + (weight / norm)
        indices = sorted(index_to_value.keys())
        values = [index_to_value[index] for index in indices]
        return SparseVector(indices=indices, values=values)

    def _term_index(self, term: str) -> int:
        digest = hashlib.md5(term.encode('utf-8')).hexdigest()
        return int(digest[:8], 16) % self.SPARSE_HASH_BUCKETS

    def _normalize(self, text: str | None) -> str:
        if text is None:
            return ''
        return ' '.join(
            text.replace('\u3000', ' ').replace('\u00a0', ' ').replace('\r', ' ').replace('\n', ' ').split()
        )


sparse_vector_service = SparseVectorService()
