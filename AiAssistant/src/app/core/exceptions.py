from __future__ import annotations


class AiServiceError(Exception):
    def __init__(
        self,
        error_code: str,
        message: str,
        status_code: int = 400,
        *,
        provider_code: str | None = None,
        model_code: str | None = None,
        upstream_status_code: int | None = None,
    ):
        super().__init__(message)
        self.error_code = error_code
        self.message = message
        self.status_code = status_code
        self.provider_code = provider_code
        self.model_code = model_code
        self.upstream_status_code = upstream_status_code
