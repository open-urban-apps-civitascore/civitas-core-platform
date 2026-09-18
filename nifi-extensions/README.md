# NiFi extensions

The NiFi components the platform installs into its NiFi deployment. The config adapter emits flows
that use them; the components themselves know nothing about the adapter.

| Module | Holds |
|---|---|
| `nifi-frost-processors` | The code: `PutFrostRecord`, the ports and the batch builder |
| `nifi-frost-nar` | The NiFi archive that carries the code into an installation |

## Build

```bash
mvn verify                 # tests, Spotless, PMD, CPD, SpotBugs, the NAR and its bill of materials
mvn spotless:apply         # format before committing
mvn test -Dtest=ThingsPortTest
mvn verify -DskipITs       # skip the integration test when no Docker daemon is running
```

`PutFrostRecordIT` writes with each port against a real FROST-Server that it brings up itself
through Testcontainers. Without a Docker daemon it skips, so `mvn verify` still passes on a
workstation without one — check the failsafe report when you need to know whether it ran.

The NAR is at `nifi-frost-nar/target/nifi-frost-nar-<version>.nar`.

**This tree compiles to Java 21**, while the rest of the repository is on 25. That is the NiFi
extension API level: every NiFi 2.x release, including the newest, compiles to release 21, and the
official image runs a JDK 21. A NAR built to a higher release fails to load, and NiFi skips a
component it cannot load without a message — the queue in front of it then fills up. The version
follows the NiFi baseline, not the repository.

## Install into the development NiFi

`dev-environment/nifi/extensions` is mounted into the NiFi container's extensions directory, and
NiFi loads a NAR dropped there without a restart.

```bash
mvn -q package -DskipTests
cp nifi-frost-nar/target/nifi-frost-nar-*.nar ../dev-environment/nifi/extensions/
```

**Install the NAR before a flow that uses it is deployed.** NiFi accepts a process group holding a
component it does not know, marks it invalid and never says so; the Pipeline then looks healthy and
writes nothing.

## PutFrostRecord

One FlowFile is one record and one atomicity group of a JSON batch request. The processor sends the
batch itself: it holds the FlowFiles and needs the answer to route them, which a downstream request
processor could not give back.

| Property | Means |
|---|---|
| `Port` | The logic. Three values, no default. |
| `FROST Base URL` | The SensorThings service root. |
| `FROST Project Id` | The Dataset's project; it scopes every lookup and every write. |
| `Web Client Service Provider` | The HTTP client. Its properties carry the TLS material, the proxy and the timeouts. |
| `Basic Auth Username` / `Basic Auth Password` | Optional, and either both or neither. |
| `Records per Request` | How many records one batch may carry, at most 100. |

Relationships: `success`, `failure`, `retry`. A record on `failure` carries `frost.error.entity`,
`frost.error.status` and `frost.error.message`. A record on `retry` is unchanged; NiFi's retry
settings on the relationship decide the backoff.

### The ports

| Port | Writes | Resolves |
|---|---|---|
| `Things` | One Thing, upserted on its `reference` | — |
| `Observations` | One measurement | `thingReference` and `datastreamReference`; a miss is a data error |
| `ThingTree` | Thing, Location, Datastream, Sensor, ObservedProperty and one measurement | In an internal chain |

The reference block lives in the entity's `properties` bag, and in `parameters` on an Observation:
SensorThings gives the Observation that bag instead and rejects the other one.

An Observation with a `reference` of its own is upserted, so a second delivery of the same message
updates the measurement. Without one every delivery appends a new measurement.

### The batch idiom

A sub-request identifier is **not** unique, and that is what makes find-or-create work: the lookup
and the create it guards share one, so `$<id>` resolves to the entity that was found or to the one
that was created, and the requests after it do not branch. An identifier is scoped to its record,
because two records must not resolve each other's entities.

The consequence is an order that is not obvious: **the update comes before the create.** `if` names
a preceding request, so an update placed behind the create also fires when the create wrote the
entity, and patches it a second time.

```json
{"id": "r0-thing", "atomicityGroup": "r0", "method": "get",
 "url": "Projects(5)/Things?$select=id&$top=1&$filter=properties/reference%20eq%20'A7'"}
{"id": "r0-thing-update", "atomicityGroup": "r0", "if": "$r0-thing",
 "method": "patch", "url": "Things($r0-thing)", "body": {}}
{"id": "r0-thing", "atomicityGroup": "r0", "if": "not $r0-thing",
 "method": "post", "url": "Projects(5)/Things", "body": {}}
```

Source: [JSON batch requests](https://fraunhoferiosb.github.io/FROST-Server/extensions/JsonBatchRequest.html).

### What the tests prove, and what they do not

The unit tests run against a loopback endpoint that answers what they tell it to, so they prove the
document the processor builds and nothing about how a server reads it. `PutFrostRecordIT` closes
that gap for the three assumptions that are the batch endpoint's behaviour: it asserts entities in
FROST, not sub-requests in a document.

- A back-reference in the path of a PATCH, `Things($r0-thing)`, and in a single-valued navigation,
  `Datastreams($r0-ds)/Sensor` — covered by the upsert tests and the ThingTree test.
- What a sub-request whose `if` did not hold leaves in the response. The division reads an absent
  answer as "did not run"; an answer carrying a status would be read the same way, because it is
  bound by identifier and order together. A defect here shows up as a record on `failure` although
  its entity was written, which the second-delivery tests would catch.

One assumption stays open: **the batch size a server accepts.** The specification names none, and
the cap of 100 here is a guard rather than a measurement. The integration test sends three records
in one batch, which proves the isolation of the groups, not the limit.
