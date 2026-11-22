import { readdir, readFile } from 'node:fs/promises';
import { createDemoChecks } from './demo-checks.mjs';

const casesDir = new URL('./cases/', import.meta.url);

async function main() {
  const [mode, input, ...rest] = process.argv.slice(2);
  if (mode !== undefined && (rest.length || !input || !['--base', '--evidence'].includes(mode))) {
    throw new Error('用法：node evals/redteam/run.mjs [--base <本机 demo 地址> | --evidence <观察记录.json>]');
  }

  const names = (await readdir(casesDir)).filter(name => name.endsWith('.json')).sort();
  const cases = await Promise.all(names.map(async name =>
    JSON.parse(await readFile(new URL(name, casesDir), 'utf8'))
  ));
  const categories = new Set(cases.map(item => item.category));
  const ids = new Set(cases.map(item => item.id));
  if (categories.size !== 5 || ids.size !== cases.length) {
    throw new Error('用例须覆盖五类且 ID 唯一');
  }

  const evidence = mode === '--evidence' ? JSON.parse(await readFile(input, 'utf8')) : {};
  if (!evidence || typeof evidence !== 'object' || Array.isArray(evidence)
      || Object.keys(evidence).some(id => !ids.has(id))) {
    throw new Error('观察记录须为以已知用例 ID 为键的 JSON 对象');
  }

  const checks = mode === '--base' ? createDemoChecks(input) : undefined;
  let failures = 0;
  for (const { id, category, attack, result, risk, fix, retest } of cases) {
    if ([id, category, attack, risk, fix, retest, result?.metric]
        .some(field => typeof field !== 'string' || !field.trim())
        || !['boolean', 'number'].includes(typeof result.expected)) {
      throw new Error('用例字段缺失或无效：' + id);
    }

    let observation;
    try {
      observation = checks ? await checks[id]() : evidence[id];
    } catch (error) {
      failures++;
      console.log('FAIL ' + id + ' ' + category + ' · ' + error.message);
      continue;
    }

    const observed = observation?.[result.metric];
    const passed = observed === result.expected;
    if (!passed) failures++;
    const detail = observed === undefined ? '缺少观察记录' :
      '期望 ' + result.expected + '，实际 ' + JSON.stringify(observed);
    console.log((passed ? 'PASS' : 'FAIL') + ' ' + id + ' ' + category + ' · ' + detail);
  }
  console.log((cases.length - failures) + '/' + cases.length + ' 通过');
  process.exitCode = failures ? 1 : 0;
}

main().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
