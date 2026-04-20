from __future__ import annotations

from dataclasses import asdict, dataclass


@dataclass(frozen=True, slots=True)
class N8nWorkflowDefinition:
    workflow_hint: str
    workflow_name: str
    version: str
    domain: str
    owner: str
    asset_file: str

    def as_metadata(self) -> dict[str, str]:
        payload = asdict(self)
        payload['workflow_version'] = payload.pop('version')
        return payload


DEFAULT_WORKFLOW_VERSION = '2026.04.1'
DEFAULT_WORKFLOW_OWNER = 'platform-ops'


WORKFLOW_REGISTRY: dict[str, N8nWorkflowDefinition] = {
    'approval-pending': N8nWorkflowDefinition(
        workflow_hint='approval-pending',
        workflow_name='approval-pending',
        version=DEFAULT_WORKFLOW_VERSION,
        domain='approval',
        owner=DEFAULT_WORKFLOW_OWNER,
        asset_file='approval-pending.json',
    ),
    'approval-applied': N8nWorkflowDefinition(
        workflow_hint='approval-applied',
        workflow_name='approval-applied',
        version=DEFAULT_WORKFLOW_VERSION,
        domain='approval',
        owner=DEFAULT_WORKFLOW_OWNER,
        asset_file='approval-applied.json',
    ),
    'approval-failed': N8nWorkflowDefinition(
        workflow_hint='approval-failed',
        workflow_name='approval-failed',
        version=DEFAULT_WORKFLOW_VERSION,
        domain='approval',
        owner=DEFAULT_WORKFLOW_OWNER,
        asset_file='approval-failed.json',
    ),
    'approval-rejected': N8nWorkflowDefinition(
        workflow_hint='approval-rejected',
        workflow_name='approval-rejected',
        version=DEFAULT_WORKFLOW_VERSION,
        domain='approval',
        owner=DEFAULT_WORKFLOW_OWNER,
        asset_file='approval-rejected.json',
    ),
    'notification-fanout': N8nWorkflowDefinition(
        workflow_hint='notification-fanout',
        workflow_name='notification-fanout',
        version=DEFAULT_WORKFLOW_VERSION,
        domain='notification',
        owner=DEFAULT_WORKFLOW_OWNER,
        asset_file='notification-fanout.json',
    ),
    'health-inspection-alert': N8nWorkflowDefinition(
        workflow_hint='health-inspection-alert',
        workflow_name='health-inspection-alert',
        version=DEFAULT_WORKFLOW_VERSION,
        domain='operations',
        owner=DEFAULT_WORKFLOW_OWNER,
        asset_file='health-inspection-alert.json',
    ),
    'weekly-report-created': N8nWorkflowDefinition(
        workflow_hint='weekly-report-created',
        workflow_name='weekly-report-created',
        version=DEFAULT_WORKFLOW_VERSION,
        domain='reporting',
        owner='knowledge-ops',
        asset_file='weekly-report-distribution.json',
    ),
}


def resolve_workflow_definition(workflow_hint: str | None) -> N8nWorkflowDefinition | None:
    normalized = (workflow_hint or '').strip()
    if not normalized:
        return None
    return WORKFLOW_REGISTRY.get(normalized)
