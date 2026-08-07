ARG SUPERSET_BASE_IMAGE=apache/superset:6.0.0
FROM ${SUPERSET_BASE_IMAGE}

USER root

ENV PATH="/app/.venv/bin:${PATH}"

RUN uv pip install --python /app/.venv/bin/python \
  Authlib==1.7.1 \
  psycopg2-binary==2.9.12 \
  'trino[sqlalchemy]==0.337.0'

COPY superset/opa_client.py /app/pythonpath/opa_client.py
COPY superset/superset_config.py /app/pythonpath/superset_config.py
COPY superset/civitas_security.py /app/pythonpath/civitas_security.py
COPY superset/start-superset.sh /app/civitas/start-superset.sh
COPY superset/bootstrap_superset.py /app/civitas/bootstrap_superset.py
COPY superset/civitas-logo.svg /app/superset/static/assets/images/civitas-logo.svg

RUN chmod 755 /app/civitas/start-superset.sh \
  && chown -R superset:superset /app/civitas /app/pythonpath

USER superset
