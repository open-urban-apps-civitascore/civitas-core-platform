#!/usr/bin/env node
const fs = require('fs');

const reportPath = process.argv[2] || 'depcheck-report.json';
if (!fs.existsSync(reportPath)) {
  console.error(`Depcheck report not found at ${reportPath}`);
  process.exit(2);
}

const report = JSON.parse(fs.readFileSync(reportPath, 'utf8'));
const unused = [...(report.dependencies || []), ...(report.devDependencies || [])];
const missing = Object.keys(report.missing || {});

if (unused.length || missing.length) {
  if (unused.length) {
    console.error('Unused dependencies:', unused.join(', '));
  }
  if (missing.length) {
    console.error('Missing dependencies:', missing.join(', '));
  }
  process.exit(1);
}

console.log('Dependency graph looks clean.');
