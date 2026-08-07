import logging

import requests

logger = logging.getLogger(__name__)


class OpaClient:

    def __init__(self, base_url: str) -> None:
        self._base_url = base_url

    def query(self, package: str, rule: str, input_data: dict) -> dict | None:
        try:
            resp = requests.post(
                f"{self._base_url}/v1/data/{package}/{rule}",
                json={"input": input_data},
                timeout=2,
            )
            if resp.status_code != 200:
                logger.warning("OPA %s returned %s — denying", rule, resp.status_code)
                return None
            return resp.json().get("result")
        except Exception:
            logger.exception("OPA %s failed — denying", rule)
            return None
