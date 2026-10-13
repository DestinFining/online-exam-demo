$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $ProjectRoot 'backend'
$Frontend = Join-Path $ProjectRoot 'frontend'

function Require-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "未找到命令 $Name。请先安装并配置环境变量。"
    }
}

Require-Command 'java'
Require-Command 'mvn'
Require-Command 'node'
Require-Command 'npm'

Write-Host '=== Java ===' -ForegroundColor Cyan
java -version
Write-Host '=== Maven ===' -ForegroundColor Cyan
$mavenInfo = (mvn -version 2>&1 | Out-String)
$mavenInfo | Write-Host
if ($mavenInfo -match 'Apache Maven\s+(\d+)\.(\d+)\.(\d+)') {
    $major = [int]$Matches[1]; $minor = [int]$Matches[2]; $patch = [int]$Matches[3]
    if (($major -lt 3) -or ($major -eq 3 -and $minor -lt 9)) {
        throw '本项目基线要求 Maven 3.9+。请升级 Maven 后重新运行脚本。'
    }
} else {
    throw '无法识别 Maven 版本，请先运行 mvn -version 检查。'
}
Write-Host '=== Node/npm ===' -ForegroundColor Cyan
node --version
npm --version

Write-Host '=== Static source consistency ===' -ForegroundColor Cyan
$checker = Join-Path $PSScriptRoot 'check-source-consistency.py'
if (Get-Command 'python' -ErrorAction SilentlyContinue) {
    python $checker
} elseif (Get-Command 'py' -ErrorAction SilentlyContinue) {
    py -3 $checker
} else {
    throw '静态检查脚本需要 Python 3。请安装 Python 3，或手动运行后端/前端构建命令。'
}
if ($LASTEXITCODE -ne 0) { throw "源码一致性检查失败，退出码：$LASTEXITCODE" }

Write-Host '=== Backend: mvn clean test ===' -ForegroundColor Cyan
Push-Location $Backend
try {
    mvn -U clean test
    if ($LASTEXITCODE -ne 0) { throw "后端构建失败，退出码：$LASTEXITCODE" }
} finally { Pop-Location }

Write-Host '=== Frontend: npm install ===' -ForegroundColor Cyan
Push-Location $Frontend
try {
    npm install
    if ($LASTEXITCODE -ne 0) { throw "npm install 失败，退出码：$LASTEXITCODE" }
    Write-Host '=== Frontend: npm run build ===' -ForegroundColor Cyan
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "前端构建失败，退出码：$LASTEXITCODE" }
} finally { Pop-Location }

Write-Host '后端测试与前端构建均成功。下一步再执行端到端回归测试。' -ForegroundColor Green
