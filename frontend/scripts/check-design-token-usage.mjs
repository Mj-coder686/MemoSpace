import { execFileSync } from 'node:child_process'

const diff = execFileSync('git', [
  'diff', '--unified=0', '--',
  'frontend/src/views',
  'frontend/src/components',
  'frontend/src/styles',
], { cwd: new URL('../..', import.meta.url), encoding: 'utf8' })

const checks = [
  { name: 'raw hexadecimal color', pattern: /#[0-9a-f]{3,8}\b/i },
  { name: 'raw box shadow', pattern: /box-shadow\s*:\s*(?!var\()/i },
  { name: '!important override', pattern: /!important/i },
]

const violations = []
let file = ''
for (const line of diff.split(/\r?\n/)) {
  if (line.startsWith('+++ b/')) {
    file = line.slice(6)
    continue
  }
  if (!line.startsWith('+') || line.startsWith('+++')) continue
  if (file.includes('/styles/tokens/')) continue
  if (file.includes('/styles/foundations/')) continue
  for (const check of checks) {
    if (check.pattern.test(line.slice(1))) violations.push(`${file}: ${check.name}: ${line.slice(1).trim()}`)
  }
}

if (violations.length) {
  console.error('New UI code must use design tokens:')
  for (const violation of violations) console.error(`- ${violation}`)
  process.exit(1)
}

console.log('Design token guard passed for added lines.')
