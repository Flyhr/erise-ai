from __future__ import annotations

from src.app.actions.builtin_actions import build_builtin_action_definitions
from src.app.actions.protocol import ActionDefinition


class ActionRegistry:
    def __init__(self) -> None:
        self._definitions: list[ActionDefinition] = []

    def register(self, definition: ActionDefinition) -> None:
        self._definitions.append(definition)

    @property
    def definitions(self) -> list[ActionDefinition]:
        return list(self._definitions)


def build_action_registry() -> ActionRegistry:
    registry = ActionRegistry()
    for definition in build_builtin_action_definitions():
        registry.register(definition)
    return registry
