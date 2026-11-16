#!/usr/bin/env node
/**
 * 把 fixtures/*.json 变成 ArkTS 里的字符串常量，供静态演示页直接渲染。
 *
 * 跑法（在 CampusBridge 目录）：node contract/tools/gen-fixtures.mjs
 * 产物：app/entry/src/main/ets/fixtures/Fixtures.ets —— 不要手改。
 */

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';

const FIXTURES = [
  ['CARD_CLARIFICATION_JSON', 'card-clarification'],
  ['CARD_COMPARISON_JSON', 'card-comparison'],
  ['CARD_CONFIRMATION_JSON', 'card-confirmation'],
  ['CARD_PROGRESS_JSON', 'card-progress'],
  ['BAD_CARD_JSON', 'bad-card-script']
];

let output = '';
output += '/**\n';
output += ' * 自动生成，不要手改。\n';
output += ' * 来源：contract/fixtures/*.json\n';
output += ' * 重新生成：node contract/tools/gen-fixtures.mjs\n';
output += ' */\n\n';

for (const [constantName, fixtureName] of FIXTURES) {
  const raw = readFileSync(new URL('../fixtures/' + fixtureName + '.json', import.meta.url), 'utf8');
  const compact = JSON.stringify(JSON.parse(raw));
  output += 'export const ' + constantName + ': string = ' + JSON.stringify(compact) + ';\n\n';
}

const target = new URL('../../app/entry/src/main/ets/fixtures/Fixtures.ets', import.meta.url);
mkdirSync(new URL('../../app/entry/src/main/ets/fixtures/', import.meta.url), { recursive: true });
writeFileSync(target, output, 'utf8');
console.log('已生成 app/entry/src/main/ets/fixtures/Fixtures.ets');
