from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from src.app.core.constants import DEFAULT_SYSTEM_PROMPTS, SCENE_GENERAL
from src.app.models.ai_prompt_template import AiPromptTemplate


def get_system_prompt(db: Session, scene: str) -> str:
    template = db.execute(
        select(AiPromptTemplate)
        .where(AiPromptTemplate.scene == scene, AiPromptTemplate.enabled.is_(True))
        .order_by(AiPromptTemplate.version_no.desc())
    ).scalar_one_or_none()
    if template:
        return template.system_prompt
    return DEFAULT_SYSTEM_PROMPTS.get(scene, DEFAULT_SYSTEM_PROMPTS[SCENE_GENERAL])
