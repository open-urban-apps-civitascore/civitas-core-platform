#!/usr/bin/env bash
# Provision the SensorThings entities a FROST sink in passthrough mode needs but never creates.
#
# The sink's observation leg looks the Datastream up by
#   properties/reference eq '<ref>'  and  name eq '<name>'  and  Thing/Projects/id eq <project>
# and routes to the error sink when it does not resolve. So a Datastream — with a Sensor, an
# ObservedProperty, and a holder Thing carrying a Location and linked to the dataset's project —
# has to exist first. This creates exactly that, once per station, idempotently.
#
# Usage:
#   PROJECT_ID=4 ./provision-sta-datastream.sh                  # 3 stations, name "Temperature"
#   PROJECT_ID=4 STATIONS=5 ./provision-sta-datastream.sh
#   PROJECT_ID=4 DATASTREAM_NAME=Humidity UNIT=% ./provision-sta-datastream.sh
#
# PROJECT_ID is the dataset's FROST project id, assigned by the saga's create-project step and
# reported on the dataset as projectId. The lookup keys match publish-sta-loop.sh defaults.

set -euo pipefail
export LC_ALL=C

FROST_URL="${FROST_URL:-http://localhost:8085/FROST-Server/v1.1}"
FROST_API_KEY="${FROST_API_KEY:-dev-frost-api-key}"
FROST_API_KEY_HEADER="${FROST_API_KEY_HEADER:-X-API-Key}"
PROJECT_ID="${PROJECT_ID:?PROJECT_ID is required: the projectId reported on the dataset}"
STATIONS="${STATIONS:-3}"
REFERENCE_PREFIX="${REFERENCE_PREFIX:-sta-station}"
DATASTREAM_NAME="${DATASTREAM_NAME:-Temperature}"
UNIT="${UNIT:-degC}"
UNIT_NAME="${UNIT_NAME:-degree Celsius}"

frost() { # method path [body]
  local method="$1" path="$2" body="${3:-}"
  if [[ -n "$body" ]]; then
    curl -sS -X "$method" "${FROST_URL}${path}" \
      -H "${FROST_API_KEY_HEADER}: ${FROST_API_KEY}" \
      -H 'Content-Type: application/json' -d "$body"
  else
    curl -sS -X "$method" "${FROST_URL}${path}" -H "${FROST_API_KEY_HEADER}: ${FROST_API_KEY}"
  fi
}

urlenc() { python3 -c "import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1]))" "$1"; }

echo "Provisioning ${STATIONS} station(s) into FROST project ${PROJECT_ID} at ${FROST_URL}" >&2

for n in $(seq 1 "$STATIONS"); do
  ref=$(printf "%s-%03d" "$REFERENCE_PREFIX" "$n")
  lat=$(awk -v s="$n" 'BEGIN { printf "%.6f", 50.5 + s * 0.5 }')
  lon=$(awk -v s="$n" 'BEGIN { printf "%.6f", 8.5 + s * 0.7 }')

  existing=$(frost GET "/Datastreams?\$filter=$(urlenc "properties/reference eq '${ref}' and name eq '${DATASTREAM_NAME}' and Thing/Projects/id eq ${PROJECT_ID}")&\$select=@iot.id" \
             | python3 -c "import json,sys; v=json.load(sys.stdin).get('value',[]); print(v[0]['@iot.id'] if v else '')" 2>/dev/null || true)
  if [[ -n "$existing" ]]; then
    echo "  = ${ref}: Datastream ${existing} already present" >&2
    continue
  fi

  # The sink's Things leg upserts on properties.reference and PATCHes the FIRST match, so a second
  # Thing carrying the same reference would split the station's identity: the flow would keep
  # updating one Thing while the Datastream — and with it every observation — hangs off the other.
  # Reuse the Thing that already carries this reference in the project; only create one when none
  # does.
  thing=$(frost GET "/Projects(${PROJECT_ID})/Things?\$filter=$(urlenc "properties/reference eq '${ref}'")&\$select=@iot.id" \
          | python3 -c "import json,sys; v=json.load(sys.stdin).get('value',[]); print(v[0]['@iot.id'] if v else '')" 2>/dev/null || true)
  if [[ -n "$thing" ]]; then
    echo "  ~ ${ref}: reusing existing Thing ${thing}" >&2
    thing_body="{ \"@iot.id\": ${thing} }"
    # A Thing the flow created carries the Location from its envelope, but one without a Location
    # makes FROST reject every observation on it ("No FeatureOfInterest provided, and none can be
    # generated") — and the flow cannot add one later, because it sends the Location nested in an
    # update, which FROST refuses. So say so now rather than let the readings fail downstream.
    locs=$(frost GET "/Things(${thing})/Locations?\$count=true&\$top=0" \
           | python3 -c "import json,sys; print(json.load(sys.stdin).get('@iot.count', 0))" 2>/dev/null || echo 0)
    if [[ "$locs" == "0" ]]; then
      echo "  ! ${ref}: Thing ${thing} has no Location — observations on it will be rejected." >&2
      echo "    Give it one, e.g.:" >&2
      echo "    curl -X POST ${FROST_URL}/Things(${thing})/Locations -H 'Content-Type: application/json' \\" >&2
      echo "      -d '{\"name\":\"Location of ${ref}\",\"description\":\"Synthetic location\"," >&2
      echo "           \"encodingType\":\"application/geo+json\"," >&2
      echo "           \"location\":{\"type\":\"Point\",\"coordinates\":[${lon}, ${lat}]}}'" >&2
    fi
  else
    thing_body=$(cat <<JSON
{
    "name": "STA Station ${ref}",
    "description": "Holder Thing for ${ref}",
    "properties": { "reference": "${ref}" },
    "Projects": [ { "@iot.id": ${PROJECT_ID} } ],
    "Locations": [
      {
        "name": "Location of ${ref}",
        "description": "Synthetic location",
        "encodingType": "application/geo+json",
        "location": { "type": "Point", "coordinates": [${lon}, ${lat}] }
      }
    ]
  }
JSON
)
  fi

  # One deep insert: Thing (new, with its Location and project membership — or a reference to the
  # existing one) + Sensor + ObservedProperty + the Datastream itself. The Datastream's
  # properties.reference + name are what the observation leg looks up.
  create_response=$(frost POST "/Datastreams" "$(cat <<JSON
{
  "name": "${DATASTREAM_NAME}",
  "description": "${DATASTREAM_NAME} readings of ${ref}",
  "observationType": "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement",
  "unitOfMeasurement": {
    "name": "${UNIT_NAME}",
    "symbol": "${UNIT}",
    "definition": "http://unitsofmeasure.org/ucum.html#para-30"
  },
  "properties": { "reference": "${ref}" },
  "Thing": ${thing_body},
  "Sensor": {
    "name": "Synthetic sensor ${ref}",
    "description": "Synthetic sensor for pipeline testing",
    "encodingType": "application/pdf",
    "metadata": "https://example.org/sensor/${ref}"
  },
  "ObservedProperty": {
    "name": "${DATASTREAM_NAME}",
    "definition": "https://example.org/observedProperty/${DATASTREAM_NAME}",
    "description": "${DATASTREAM_NAME}"
  }
}
JSON
)")

  # Confirm by the same filter the sink will use, so a create that FROST rejected on a constraint
  # is reported here rather than surfacing later as observations in the error sink.
  created=$(frost GET "/Datastreams?\$filter=$(urlenc "properties/reference eq '${ref}' and name eq '${DATASTREAM_NAME}' and Thing/Projects/id eq ${PROJECT_ID}")&\$select=@iot.id" \
            | python3 -c "import json,sys; v=json.load(sys.stdin).get('value',[]); print(v[0]['@iot.id'] if v else '')" 2>/dev/null || true)
  if [[ -n "$created" ]]; then
    echo "  + ${ref}: Datastream ${created}" >&2
  else
    echo "  ! ${ref}: NOT created — FROST response: ${create_response}" >&2
  fi
done

echo "Done. Publish with: TOPIC=sensors/sta DATASTREAM_NAME=${DATASTREAM_NAME} ./publish-sta-loop.sh" >&2
