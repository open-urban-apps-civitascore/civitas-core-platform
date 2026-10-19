# Vendored GeoJSON geometry schemas

Third-party documents, bundled so a geometry reference in a model resolves without the server
reaching the network. Loaded by `validation.VendoredSchemaLoader`; not CORE artifacts and not part of
any CORE contract.

## Provenance

| | |
|---|---|
| Origin | `https://geojson.org/schema/<Type>.json` |
| Project | `geojson/schema` |
| Licence | MIT |
| Retrieved | 2026-08-28 |

The `geojson/schema` repository does **not** contain these documents. It holds JavaScript sources
(`src/schema/*.js`) plus shared fragments (`src/schema/ref/*.js`), which its build flattens and
publishes to geojson.org. Take the published documents; do not copy from the repository.

There is no upstream release or tag to pin against — the published documents are a rolling artefact.
The checksums below stand in for a version, so a refresh is a reviewable diff.

```
35dd9cc5537e3a02a58ec63c22001508bd0c26036803f86004bb7cad0e9ad8b1  Point.json
38089f2d5b0ff4b14a3252266d12b589f5f0e7bcb6cf49558801065c9ed02ecb  LineString.json
abff591832563d1343e8cacb5faaafe7b104560ac83d33d47b158446775c464a  Polygon.json
402f60cda7dd391e4f1f596143a243a7360d2b7e6f01a7f26a32a773267ffb95  MultiPoint.json
1ed66d341ae8220a0f969accf6783ce356bade856ab61691ee8c6c5b951e3764  MultiLineString.json
3d6e592c494264394e7252361b3e65acf4c31716a3d2106338dbcfb6f5fde55a  MultiPolygon.json
f33321639349bcb02300f560e5bb46bfc786cec2d6a5195778c5d480856e2e71  GeometryCollection.json
```

All seven declare draft-07 and reference no other document, which is what lets them be resolved from
here. `GeometryCollection.json` is the largest because it inlines the other geometries rather than
referencing them. `GeometrySchemaResolutionTest` asserts the self-containment for every vendored
entry, so a refresh that reintroduced an external reference fails the build.

The four remaining published documents — `Feature`, `FeatureCollection`, `Geometry`, `GeoJSON` — are
not referenced by any model and are deliberately absent.

## Refreshing

Re-download all seven from the origin above, update the checksums and the retrieval date, and run
the module build. Review the diff: an upstream change to a geometry definition changes what existing
stored models validate against.
