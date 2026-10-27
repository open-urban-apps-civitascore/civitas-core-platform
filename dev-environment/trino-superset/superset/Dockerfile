ARG SUPERSET_BASE_IMAGE=apache/superset:7.0.0rc2
FROM ${SUPERSET_BASE_IMAGE}

USER root

ENV PATH="/app/.venv/bin:${PATH}"

RUN uv pip install --python /app/.venv/bin/python \
  'trino[sqlalchemy]==0.337.0'

USER superset
