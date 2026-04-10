from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from src.app.core.config import get_settings
from src.app.db.session import init_database
from src.app.services.model_registry import bootstrap_defaults


def main() -> None:
    settings = get_settings()
    init_database()
    bootstrap_defaults()
    print(f'Validated AI chat schema and bootstrapped defaults for {settings.mysql_dsn}')


if __name__ == '__main__':
    main()
