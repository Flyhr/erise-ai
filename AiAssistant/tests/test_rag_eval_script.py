from __future__ import annotations

import json
import os
import subprocess
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'scripts' / 'rag_eval.py'
DATASET = ROOT / 'scripts' / 'eval_data' / 'rag_eval_minimal.json'


class RagEvalScriptTest(unittest.TestCase):
    def test_rag_eval_runs_with_local_embedding_fallback(self) -> None:
        env = os.environ.copy()
        env.update({
            'OPENAI_API_KEY': '',
            'DEEPSEEK_API_KEY': '',
            'EMBEDDING_API_KEY': '',
        })
        env.pop('EMBEDDING_LOCAL_FALLBACK_ENABLED', None)

        completed = subprocess.run(
            [sys.executable, str(SCRIPT), '--dataset', str(DATASET)],
            cwd=ROOT,
            env=env,
            capture_output=True,
            text=True,
            check=False,
        )

        self.assertEqual(0, completed.returncode, completed.stderr or completed.stdout)
        payload = json.loads(completed.stdout)
        self.assertEqual(str(DATASET), payload['dataset'])
        self.assertEqual(4, payload['caseCount'])
        self.assertIn('hitRateAtTopK', payload['metrics'])
        self.assertEqual(4, len(payload['cases']))
