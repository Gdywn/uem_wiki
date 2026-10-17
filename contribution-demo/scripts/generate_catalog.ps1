[CmdletBinding()]
param(
    [string]$Source = (Join-Path $PSScriptRoot '..\..\docs\资料库'),
    [string]$Output = (Join-Path $PSScriptRoot '..\src\main\resources\catalog.json')
)

$ErrorActionPreference = 'Stop'
$generalCollegeId = 'general'
$generalCollegeName = '通用资料'
$generalMajorId = 'general-all'
$generalMajorName = '通用'
$sourcePath = [IO.Path]::GetFullPath($Source)
$outputPath = [IO.Path]::GetFullPath($Output)
if (-not (Test-Path -LiteralPath $sourcePath -PathType Container)) {
    throw "分类目录不存在：$sourcePath"
}

function Get-StableId([string]$Value) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
        $hash = ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') }) -join ''
        return $hash.Substring(0, 20)
    } finally {
        $sha.Dispose()
    }
}
function Copy-OldMajor($Major, [bool]$Active) {
    return [ordered]@{ id = [string]$Major.id; name = [string]$Major.name; active = $Active }
}
function Copy-OldCollege($College) {
    $majors = @()
    foreach ($major in @($College.majors)) { $majors += (Copy-OldMajor $major $false) }
    return [ordered]@{ id = [string]$College.id; name = [string]$College.name; active = $false; majors = $majors }
}

$old = $null
if (Test-Path -LiteralPath $outputPath -PathType Leaf) {
    try { $old = Get-Content -LiteralPath $outputPath -Raw -Encoding UTF8 | ConvertFrom-Json }
    catch { throw "现有 catalog.json 格式错误，未覆盖：$($_.Exception.Message)" }
    if ($null -eq $old -or $old.version -ne 1 -or $null -eq $old.colleges) {
        throw '现有 catalog.json 缺少 version: 1 或 colleges，未覆盖。'
    }
}

$oldColleges = @{}
if ($null -ne $old) {
    foreach ($college in @($old.colleges)) {
        if ([string]::IsNullOrWhiteSpace([string]$college.name) -or [string]::IsNullOrWhiteSpace([string]$college.id)) {
            throw '现有 catalog.json 中存在缺少 name/id 的学院，未覆盖。'
        }
        if ($oldColleges.ContainsKey([string]$college.name)) { throw "现有 catalog.json 学院重名：$($college.name)" }
        $oldColleges[[string]$college.name] = $college
    }
}

$newColleges = @()
$seenIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$currentCollegeNames = @{}
$collegeDirectories = Get-ChildItem -LiteralPath $sourcePath -Directory -Force | Where-Object {
    $_.Name -ne $generalCollegeName -and @(Get-ChildItem -LiteralPath $_.FullName -Directory -Force).Count -gt 0
} | Sort-Object Name
foreach ($collegeDir in $collegeDirectories) {
    $collegeName = $collegeDir.Name
    $currentCollegeNames[$collegeName] = $true
    $oldCollege = $oldColleges[$collegeName]
    if ($null -ne $oldCollege) { $collegeId = [string]$oldCollege.id }
    else {
        $collegeId = 'college-' + (Get-StableId ('college|' + $collegeName))
        Write-Output "新增学院目录：$collegeName（ID: $collegeId）"
    }
    if (-not $seenIds.Add($collegeId)) { throw "分类 ID 重复：$collegeId" }

    $oldMajors = @{}
    if ($null -ne $oldCollege) {
        foreach ($major in @($oldCollege.majors)) {
            if ([string]::IsNullOrWhiteSpace([string]$major.name) -or [string]::IsNullOrWhiteSpace([string]$major.id)) {
                throw "学院 $collegeName 的既有专业缺少 name/id，未覆盖。"
            }
            if ($oldMajors.ContainsKey([string]$major.name)) { throw "学院 $collegeName 的既有专业重名：$($major.name)" }
            $oldMajors[[string]$major.name] = $major
        }
    }

    $majors = @()
    $currentMajorNames = @{}
    foreach ($majorDir in (Get-ChildItem -LiteralPath $collegeDir.FullName -Directory -Force | Sort-Object Name)) {
        $majorName = $majorDir.Name
        $currentMajorNames[$majorName] = $true
        $oldMajor = $oldMajors[$majorName]
        if ($null -ne $oldMajor) { $majorId = [string]$oldMajor.id }
        else {
            $majorId = 'major-' + (Get-StableId ('major|' + $collegeName + '|' + $majorName))
            Write-Output "新增专业目录：$collegeName / $majorName（ID: $majorId）"
        }
        if (-not $seenIds.Add($majorId)) { throw "分类 ID 重复：$majorId" }
        $majors += [ordered]@{ id = $majorId; name = $majorName; active = $true }
    }

    if ($null -ne $oldCollege) {
        foreach ($major in @($oldCollege.majors | Sort-Object name)) {
            if (-not $currentMajorNames.ContainsKey([string]$major.name)) {
                if ($major.active -eq $true) { Write-Warning "目录已消失或改名，旧分类保留并标记停用：$collegeName / $($major.name)（ID: $($major.id)）" }
                if (-not $seenIds.Add([string]$major.id)) { throw "分类 ID 重复：$($major.id)" }
                $majors += (Copy-OldMajor $major $false)
            }
        }
    }
    $newColleges += [ordered]@{ id = $collegeId; name = $collegeName; active = $true; majors = @($majors) }
}

if ($null -ne $old) {
    foreach ($oldCollege in @($old.colleges | Sort-Object name)) {
        if ([string]$oldCollege.name -eq $generalCollegeName -or [string]$oldCollege.id -eq $generalCollegeId) { continue }
        if (-not $currentCollegeNames.ContainsKey([string]$oldCollege.name)) {
            if ($oldCollege.active -eq $true) { Write-Warning "学院目录已消失、改名或不再包含专业子目录，旧分类保留并标记停用：$($oldCollege.name)（ID: $($oldCollege.id)）" }
            if (-not $seenIds.Add([string]$oldCollege.id)) { throw "分类 ID 重复：$($oldCollege.id)" }
            foreach ($major in @($oldCollege.majors)) {
                if (-not $seenIds.Add([string]$major.id)) { throw "分类 ID 重复：$($major.id)" }
            }
            $newColleges += (Copy-OldCollege $oldCollege)
        }
    }
}

# 通用资料是固定的特殊分类：目录中没有学院/专业两级子目录，因此不从文件夹名派生 ID。
# 如果现有 catalog 已含这项，校验并复用固定 ID；历史额外专业保留为停用项。
$oldGeneral = $oldColleges[$generalCollegeName]
if ($null -ne $oldGeneral -and [string]$oldGeneral.id -ne $generalCollegeId) {
    throw "通用资料学院 ID 必须为 $generalCollegeId，现有 ID 为 $($oldGeneral.id)，未覆盖。"
}
$generalMajors = @([ordered]@{ id = $generalMajorId; name = $generalMajorName; active = $true })
if ($null -ne $oldGeneral) {
    if (-not $seenIds.Add($generalCollegeId)) { throw "分类 ID 重复：$generalCollegeId" }
    if (-not $seenIds.Add($generalMajorId)) { throw "分类 ID 重复：$generalMajorId" }
    $oldGeneralMajor = @($oldGeneral.majors | Where-Object { [string]$_.id -eq $generalMajorId }) | Select-Object -First 1
    if ($null -ne $oldGeneralMajor -and -not [string]::IsNullOrWhiteSpace([string]$oldGeneralMajor.name)) {
        $generalMajors[0].name = [string]$oldGeneralMajor.name
    }
    foreach ($major in @($oldGeneral.majors)) {
        if ([string]$major.id -eq $generalMajorId) { continue }
        if ([string]::IsNullOrWhiteSpace([string]$major.id) -or [string]::IsNullOrWhiteSpace([string]$major.name)) {
            throw '通用资料下的既有专业缺少 name/id，未覆盖。'
        }
        if (-not $seenIds.Add([string]$major.id)) { throw "分类 ID 重复：$($major.id)" }
        $generalMajors += (Copy-OldMajor $major $false)
    }
} else {
    if (-not $seenIds.Add($generalCollegeId)) { throw "分类 ID 重复：$generalCollegeId" }
    if (-not $seenIds.Add($generalMajorId)) { throw "分类 ID 重复：$generalMajorId" }
}
$newColleges += [ordered]@{ id = $generalCollegeId; name = $generalCollegeName; active = $true; majors = @($generalMajors) }

$document = [ordered]@{ version = 1; colleges = @($newColleges | Sort-Object name) }
$json = ConvertTo-Json -InputObject $document -Depth 30
$outputDir = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$tempPath = $outputPath + '.tmp'
[IO.File]::WriteAllText($tempPath, ($json + [Environment]::NewLine), [Text.UTF8Encoding]::new($false))
Move-Item -LiteralPath $tempPath -Destination $outputPath -Force
Write-Output "分类文件已生成：$outputPath"
