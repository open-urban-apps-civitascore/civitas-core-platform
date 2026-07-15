/**
 * CIVITAS/CORE JSON Schema → Zod generator
 *
 * Reads the CORE-IR JSON Schema files from model-forge-runtime/src/main/resources/
 * and emits TypeScript modules with idiomatic Zod schemas to src/generated/.
 *
 * Handles standard JSON Schema Draft 2020-12 constructs plus CORE extensions:
 *   x-core-ref    — semantic reference annotation  → @coreRef JSDoc tag
 *   x-ui-position — visual canvas position hint   → preserved as typed field
 *   URN patterns  — regex + human-readable message
 *   allOf base+ext — PipelineNodeBase.extend({})  → discriminated union ready
 *   oneOf with const discriminator → z.discriminatedUnion when possible
 */

import { readFileSync, writeFileSync, mkdirSync } from "fs";
import { join, dirname } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const SCHEMA_DIR = join(__dirname, "../model-forge-runtime/src/main/resources");
const OUT_DIR    = join(__dirname, "src/generated");

// Cross-module imports needed by dataset.ts
const CROSS_MODULE_IMPORTS = {
  "DataSet": [
    `import { MappingSchema }    from "./mapping.js";`,
    `import { PipelineSchema }   from "./pipeline.js";`,
    `import { DataSourceSchema } from "./datasource.js";`,
    `import { DataSinkSchema }   from "./datasink.js";`,
  ],
};

const SCHEMAS = [
  { file: "datasource.schema.json",        name: "DataSource",       out: "datasource.ts"        },
  { file: "datasink.schema.json",          name: "DataSink",         out: "datasink.ts"          },
  { file: "mapping.schema.json",           name: "Mapping",          out: "mapping.ts"           },
  { file: "pipeline.schema.json",          name: "Pipeline",         out: "pipeline.ts"          },
  { file: "dataset.schema.json",           name: "DataSet",          out: "dataset.ts"           },
  { file: "datastructure.schema.json",     name: "DataStructure",    out: "datastructure.ts"     },
  { file: "artifact-envelope.schema.json", name: "ArtifactEnvelope", out: "artifact-envelope.ts" },
];

// ─────────────────────────────────────────────────────────────────────────────

class ModuleGenerator {
  constructor(schema, rootName, extraImports = []) {
    this.schema       = schema;
    this.rootName     = rootName;
    this.defs         = schema.$defs ?? {};
    this.extraImports = extraImports;
    this.exports      = []; // { schemaName, typeName } collected while walking
  }

  generate() {
    const defsCode  = this._generateDefs();
    const rootCode  = this._generateRoot();
    const typesCode = this._generateTypeExports();

    const header = [
      `// Generated from ${this.schema.$id ?? "?"} — run \`node generate.mjs\` to regenerate`,
      `import { z } from "zod";`,
      ...this.extraImports,
    ].join("\n");

    const sections = [header];
    if (defsCode) {
      sections.push(
        "// ── $defs ──────────────────────────────────────────────────────────────────\n\n" + defsCode
      );
    }
    sections.push(
      "// ── Root schema ────────────────────────────────────────────────────────────\n\n" + rootCode
    );
    sections.push(
      "// ── Inferred TypeScript types ──────────────────────────────────────────────\n\n" + typesCode
    );

    return sections.join("\n\n") + "\n";
  }

  // ── $defs ─────────────────────────────────────────────────────────────────

  _generateDefs() {
    const order = this._topoSort(this.defs);
    const parts = [];
    for (const name of order) {
      const def      = this.defs[name];
      const isBase   = name === "PipelineNodeBase"; // kept internal; node subtypes are exported
      const constKw  = isBase ? "const" : "export const";
      const jsDoc    = this._jsDoc(def.description);
      const expr     = this._toZod(def, { required: [] });
      parts.push(`${jsDoc}${constKw} ${name}Schema = ${expr};`);
      if (!isBase) this.exports.push({ schemaName: `${name}Schema`, typeName: name });
    }
    return parts.join("\n\n");
  }

  _generateRoot() {
    const { schema, rootName } = this;
    const jsDoc = this._jsDoc(schema.description);
    const expr  = this._toZod(schema, { required: schema.required ?? [] });
    this.exports.push({ schemaName: `${rootName}Schema`, typeName: rootName });
    return `${jsDoc}export const ${rootName}Schema = ${expr};`;
  }

  _generateTypeExports() {
    return this.exports
      .map(({ schemaName, typeName }) => `export type ${typeName} = z.infer<typeof ${schemaName}>;`)
      .join("\n");
  }

  // ── Core recursive converter ───────────────────────────────────────────────

  _toZod(node, ctx = {}) {
    if (!node || typeof node !== "object") return "z.unknown()";

    if (node.$ref) return `${node.$ref.replace(/^#\/\$defs\//, "")}Schema`;
    if (node.oneOf) return this._oneOf(node.oneOf, ctx);
    if (node.allOf) return this._allOf(node.allOf, ctx);
    if (node.anyOf) return `z.union([${node.anyOf.map(s => this._toZod(s, ctx)).join(", ")}])`;

    const { type } = node;
    if (type === "string")  return this._stringSchema(node);
    if (type === "integer") return this._numericSchema(node, true);
    if (type === "number")  return this._numericSchema(node, false);
    if (type === "boolean") return "z.boolean()";
    if (type === "array")   return this._arraySchema(node, ctx);
    if (type === "object" || node.properties || node.additionalProperties) {
      return this._objectSchema(node, ctx);
    }
    if (node.const !== undefined) return `z.literal(${JSON.stringify(node.const)})`;
    if (node.enum)                return `z.enum([${node.enum.map(v => JSON.stringify(v)).join(", ")}])`;

    return "z.unknown()";
  }

  _stringSchema(node) {
    if (node.const !== undefined) return `z.literal(${JSON.stringify(node.const)})`;
    if (node.enum)  return `z.enum([${node.enum.map(v => JSON.stringify(v)).join(", ")}])`;
    let s = "z.string()";
    if (node.pattern) {
      const msg = this._urnMessage(node.pattern) ?? `Must match ${node.pattern}`;
      s += `.regex(/${node.pattern}/, ${JSON.stringify(msg)})`;
    }
    if (node.minLength) s += `.min(${node.minLength})`;
    if (node.maxLength) s += `.max(${node.maxLength})`;
    return s;
  }

  _urnMessage(pattern) {
    if (pattern.includes("datasource"))   return "Must be a versioned CORE DataSource URN";
    if (pattern.includes("datasink"))     return "Must be a versioned CORE DataSink URN";
    if (pattern.includes("mapping"))      return "Must be a versioned CORE Mapping URN";
    if (pattern.includes("pipeline"))     return "Must be a versioned CORE Pipeline URN";
    if (pattern.includes("dataset"))      return "Must be a versioned CORE DataSet URN";
    if (pattern.includes("datastructure")) return "Must be a versioned CORE DataStructure URN";
    if (pattern.includes("element"))      return "Must be a versioned CORE Element URN";
    if (pattern.startsWith("^urn:"))      return "Must be a CORE URN";
    return null;
  }

  _numericSchema(node, integer) {
    let s = integer ? "z.number().int()" : "z.number()";
    if (node.minimum  !== undefined) s += `.min(${node.minimum})`;
    if (node.maximum  !== undefined) s += `.max(${node.maximum})`;
    if (node.exclusiveMinimum !== undefined) s += `.gt(${node.exclusiveMinimum})`;
    if (node.exclusiveMaximum !== undefined) s += `.lt(${node.exclusiveMaximum})`;
    return s;
  }

  _arraySchema(node, ctx) {
    const items = node.items ? this._toZod(node.items, ctx) : "z.unknown()";
    let s = `z.array(${items})`;
    if (node.minItems) s += `.min(${node.minItems})`;
    if (node.maxItems) s += `.max(${node.maxItems})`;
    return s;
  }

  _objectSchema(node, ctx) {
    // Pure record: additionalProperties but no properties.
    // Zod 4 requires an explicit key type: z.record(keyType, valueType).
    if (!node.properties && node.additionalProperties) {
      const val = node.additionalProperties === true
        ? "z.unknown()"
        : this._toZod(node.additionalProperties, ctx);
      return `z.record(z.string(), ${val})`;
    }
    if (!node.properties) return "z.record(z.string(), z.unknown())";

    const required = node.required ?? ctx.required ?? [];
    const lines = Object.entries(node.properties).map(([key, propSchema]) => {
      const isReq    = required.includes(key);
      const jsDoc    = this._propJsDoc(propSchema);
      const expr     = this._toZod(propSchema, { required: propSchema.required ?? [] });
      const opt      = isReq ? "" : ".optional()";
      const keyStr   = /^[$\w]+$/.test(key) ? key : JSON.stringify(key);
      return `${jsDoc}  ${keyStr}: ${expr}${opt},`;
    });

    const strict = node.additionalProperties === false || node.unevaluatedProperties === false;
    const obj    = `z.object({\n${lines.join("\n")}\n})`;
    return strict ? `${obj}.strict()` : obj;
  }

  _oneOf(schemas, ctx) {
    const discKey = this._findDiscriminator(schemas);
    if (discKey) {
      const members = schemas.map(s => this._toZod(s, ctx));
      return `z.discriminatedUnion(${JSON.stringify(discKey)}, [\n  ${members.join(",\n  ")}\n])`;
    }
    return `z.union([${schemas.map(s => this._toZod(s, ctx)).join(", ")}])`;
  }

  _findDiscriminator(schemas) {
    let candidates = null;
    for (const s of schemas) {
      const keys = this._constKeysOf(s);
      if (keys.size === 0) return null;
      candidates = candidates === null
        ? new Set(keys)
        : new Set([...candidates].filter(k => keys.has(k)));
      if (candidates.size === 0) return null;
    }
    return candidates ? [...candidates][0] : null;
  }

  _constKeysOf(schema) {
    const keys = new Set();
    if (schema.properties) {
      for (const [k, v] of Object.entries(schema.properties)) {
        if (v.const !== undefined || (v.type === "string" && v.enum?.length === 1)) keys.add(k);
      }
    }
    if (schema.allOf) schema.allOf.forEach(p => this._constKeysOf(p).forEach(k => keys.add(k)));
    if (schema.$ref) {
      const name = schema.$ref.replace(/^#\/\$defs\//, "");
      if (this.defs[name]) this._constKeysOf(this.defs[name]).forEach(k => keys.add(k));
    }
    return keys;
  }

  _allOf(schemas, ctx) {
    if (schemas.length === 2) {
      const [base, ext] = schemas;
      if (base.$ref && (ext.type === "object" || ext.properties)) {
        const baseName = base.$ref.replace(/^#\/\$defs\//, "");
        const req      = ext.required ?? [];
        const extLines = Object.entries(ext.properties ?? {}).map(([key, propSchema]) => {
          // const-valued properties are discriminators — always required
          const isConst = propSchema.const !== undefined;
          const isReq  = isConst || req.includes(key);
          const jsDoc  = this._propJsDoc(propSchema);
          const expr   = this._toZod(propSchema, {});
          const opt    = isReq ? "" : ".optional()";
          const keyStr = /^[$\w]+$/.test(key) ? key : JSON.stringify(key);
          return `${jsDoc}  ${keyStr}: ${expr}${opt},`;
        });
        const body = extLines.length > 0 ? `\n${extLines.join("\n")}\n` : "";
        return `${baseName}Schema.extend({${body}})`;
      }
    }
    const [first, ...rest] = schemas.map(s => this._toZod(s, ctx));
    return rest.reduce((acc, s) => `${acc}.and(${s})`, first);
  }

  // ── JSDoc helpers ─────────────────────────────────────────────────────────

  _jsDoc(description) {
    if (!description) return "";
    return `/** ${description.split("\n")[0].trim()} */\n`;
  }

  _propJsDoc(propSchema) {
    const parts = [];
    if (propSchema.description)    parts.push(propSchema.description.split("\n")[0].trim());
    if (propSchema["x-core-ref"]) {
      const { type, scope } = propSchema["x-core-ref"];
      parts.push(`@coreRef { type: "${type}"${scope ? `, scope: "${scope}"` : ""} }`);
    }
    if (parts.length === 0) return "";
    if (parts.length === 1) return `  /** ${parts[0]} */\n`;
    return `  /**\n${parts.map(p => `   * ${p}`).join("\n")}\n   */\n`;
  }

  // ── Topological sort of $defs ─────────────────────────────────────────────

  _topoSort(defs) {
    const visited = new Set();
    const order   = [];
    const visit   = (name) => {
      if (visited.has(name)) return;
      visited.add(name);
      this._collectRefs(defs[name]).forEach(dep => { if (defs[dep]) visit(dep); });
      order.push(name);
    };
    Object.keys(defs).forEach(visit);
    return order;
  }

  _collectRefs(node, refs = new Set()) {
    if (!node || typeof node !== "object") return refs;
    if (node.$ref) refs.add(node.$ref.replace(/^#\/\$defs\//, ""));
    Object.values(node).forEach(v => { if (typeof v === "object" && v !== null) this._collectRefs(v, refs); });
    return refs;
  }
}

// ─────────────────────────────────────────────────────────────────────────────

mkdirSync(OUT_DIR, { recursive: true });

for (const { file, name, out } of SCHEMAS) {
  const schema = JSON.parse(readFileSync(join(SCHEMA_DIR, file), "utf-8"));
  const code   = new ModuleGenerator(schema, name, CROSS_MODULE_IMPORTS[name] ?? []).generate();
  writeFileSync(join(OUT_DIR, out), code, "utf-8");
  console.log(`✓  ${file.padEnd(40)} → src/generated/${out}`);
}
