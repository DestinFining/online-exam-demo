#!/usr/bin/env python3
"""Lightweight local source checks; this is not a substitute for Maven/npm build."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'backend' / 'src' / 'main' / 'java'
errors = []

required = [
    JAVA / 'com/example/exam/question/QuestionController.java',
    JAVA / 'com/example/exam/question/QuestionService.java',
    JAVA / 'com/example/exam/question/BatchQuestionRequest.java',
    JAVA / 'com/example/exam/question/BatchDeleteQuestionsRequest.java',
    JAVA / 'com/example/exam/grade/GradeController.java',
    JAVA / 'com/example/exam/grade/GradeService.java',
    JAVA / 'com/example/exam/grade/GradeSummary.java',
    JAVA / 'com/example/exam/grade/BatchPublishResult.java',
]
for path in required:
    if not path.exists():
        errors.append(f'Missing required source: {path.relative_to(ROOT)}')

# Internal Java imports must resolve inside this source tree.
for source in JAVA.rglob('*.java'):
    text = source.read_text(encoding='utf-8')
    for imported in re.findall(r'^import\s+(com\.example\.exam\.[A-Za-z0-9_.]+);', text, re.M):
        if imported.endswith('.*'):
            continue
        relative = Path(*imported.split('.')).with_suffix('.java')
        if not (JAVA / relative).exists():
            errors.append(f'Unresolved internal Java import: {source.relative_to(ROOT)} -> {imported}')

# Basic config syntax checks.
try:
    import json
    package_data = json.loads((ROOT / 'frontend' / 'package.json').read_text(encoding='utf-8'))
    if not package_data.get('scripts', {}).get('build'):
        errors.append('frontend/package.json does not define a build script')
except Exception as ex:
    errors.append(f'Invalid frontend/package.json: {ex}')
try:
    import xml.etree.ElementTree as ET
    ET.parse(ROOT / 'backend' / 'pom.xml')
except Exception as ex:
    errors.append(f'Invalid backend/pom.xml: {ex}')

# Controller's DTO references must resolve to source files in the same package.
question_controller = (JAVA / 'com/example/exam/question/QuestionController.java')
if question_controller.exists():
    text = question_controller.read_text(encoding='utf-8')
    for dto in ('BatchQuestionRequest', 'BatchDeleteQuestionsRequest'):
        dto_file = question_controller.parent / f'{dto}.java'
        if dto not in text:
            errors.append(f'QuestionController does not reference expected DTO {dto}')
        if not dto_file.exists() or not re.search(rf'\b(record|class)\s+{dto}\b', dto_file.read_text(encoding='utf-8')):
            errors.append(f'DTO definition missing or name mismatch: {dto_file.relative_to(ROOT)}')

# Count record components and new GradeSummary(...) arguments at top-level.
def matching_paren_end(text: str, open_idx: int) -> int:
    depth = 0
    in_string = None
    escape = False
    for i in range(open_idx, len(text)):
        ch = text[i]
        if in_string:
            if escape:
                escape = False
            elif ch == '\\':
                escape = True
            elif ch == in_string:
                in_string = None
            continue
        if ch in ('"', "'"):
            in_string = ch
        elif ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0:
                return i
    return -1

def count_top_args(text: str) -> int:
    if not text.strip():
        return 0
    depth = 0
    in_string = None
    escape = False
    commas = 0
    for ch in text:
        if in_string:
            if escape:
                escape = False
            elif ch == '\\':
                escape = True
            elif ch == in_string:
                in_string = None
            continue
        if ch in ('"', "'"):
            in_string = ch
        elif ch in '([{':
            depth += 1
        elif ch in ')]}':
            depth -= 1
        elif ch == ',' and depth == 0:
            commas += 1
    return commas + 1

summary_file = JAVA / 'com/example/exam/grade/GradeSummary.java'
service_file = JAVA / 'com/example/exam/grade/GradeService.java'
if summary_file.exists() and service_file.exists():
    summary_text = summary_file.read_text(encoding='utf-8')
    m = re.search(r'record\s+GradeSummary\s*\((.*?)\)\s*\{\s*\}', summary_text, re.S)
    if not m:
        errors.append('Could not parse GradeSummary record declaration')
    else:
        component_count = count_top_args(m.group(1))
        svc = service_file.read_text(encoding='utf-8')
        found = []
        start = 0
        while True:
            idx = svc.find('new GradeSummary(', start)
            if idx < 0:
                break
            op = svc.find('(', idx)
            end = matching_paren_end(svc, op)
            if end < 0:
                errors.append('Unclosed new GradeSummary(...) call in GradeService')
                break
            found.append(count_top_args(svc[op + 1:end]))
            start = end + 1
        if not found:
            errors.append('No new GradeSummary(...) calls found in GradeService')
        for n in found:
            if n != component_count:
                errors.append(f'GradeSummary constructor arity mismatch: record has {component_count} components, call has {n} arguments')

# Ensure frontend references use local source files that exist (common extension variants).
frontend_src = ROOT / 'frontend' / 'src'
for path in frontend_src.rglob('*'):
    if not path.is_file() or path.suffix not in ('.ts', '.vue'):
        continue
    content = path.read_text(encoding='utf-8')
    for spec in re.findall(r"from\s*['\"](@/[^'\"]+|\./[^'\"]+|\.\./[^'\"]+)['\"]", content):
        target = (frontend_src / spec[2:]) if spec.startswith('@/') else (path.parent / spec)
        candidates = [target, target.with_suffix('.ts'), target.with_suffix('.vue'), target / 'index.ts', target / 'index.vue']
        if not any(c.exists() for c in candidates):
            errors.append(f'Unresolved local frontend import: {path.relative_to(ROOT)} -> {spec}')

if errors:
    print('STATIC CONSISTENCY CHECK FAILED')
    for item in errors:
        print(f' - {item}')
    sys.exit(1)

print('STATIC CONSISTENCY CHECK PASSED')
print(f'Java source files: {sum(1 for _ in JAVA.rglob("*.java"))}')
print(f'Frontend source files: {sum(1 for p in frontend_src.rglob("*") if p.is_file())}')
print('This check does not replace `mvn clean test` or `npm run build`.')
