# Maven Central Publishing Script (PowerShell)
# Usage: .\release.ps1 <version>
# Example: .\release.ps1 2.0.0

param(
    [Parameter(Mandatory=$false)]
    [string]$Version
)

if (-not $Version) {
    Write-Host "Error: Please provide version number" -ForegroundColor Red
    Write-Host "Usage: .\release.ps1 <version>" -ForegroundColor Yellow
    Write-Host "Example: .\release.ps1 2.0.0" -ForegroundColor Yellow
    exit 1
}

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Green
Write-Host "Preparing to release version: $Version" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# Check for uncommitted changes
$gitStatus = git status --porcelain 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Not a git repository or git not found" -ForegroundColor Red
    exit 1
}

if ($gitStatus) {
    Write-Host "Error: Uncommitted changes detected. Please commit or stash them first." -ForegroundColor Red
    git status --short
    exit 1
}

# 1. Set version
Write-Host "[1/7] Setting version..." -ForegroundColor Cyan
mvn versions:set -DnewVersion=$Version
if ($LASTEXITCODE -ne 0) { exit 1 }
mvn versions:commit
if ($LASTEXITCODE -ne 0) { exit 1 }

# 2. Run tests
Write-Host "[2/7] Running tests..." -ForegroundColor Cyan
mvn clean test
if ($LASTEXITCODE -ne 0) {
    Write-Host "Tests failed. Release aborted." -ForegroundColor Red
    exit 1
}

# 3. Build project
Write-Host "[3/7] Building project..." -ForegroundColor Cyan
mvn clean install
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed. Release aborted." -ForegroundColor Red
    exit 1
}

# 4. Commit version changes
Write-Host "[4/7] Committing version changes..." -ForegroundColor Cyan
git add pom.xml
git commit -m "Release version $Version"
git tag -a "v$Version" -m "Release version $Version"

# 5. Deploy to Maven Central
Write-Host "[5/7] Deploying to Maven Central..." -ForegroundColor Cyan
mvn clean deploy
if ($LASTEXITCODE -ne 0) {
    Write-Host "Deploy failed. Release aborted." -ForegroundColor Red
    exit 1
}

# 6. Push to Git
Write-Host "[6/7] Pushing to Git..." -ForegroundColor Cyan
$currentBranch = git rev-parse --abbrev-ref HEAD
git push origin $currentBranch
git push origin "v$Version"

# 7. Set next development version
$versionParts = $Version -split '\.'
if ($versionParts.Count -ne 3) {
    Write-Host "Error: Invalid version format. Expected: major.minor.patch" -ForegroundColor Red
    exit 1
}

$major = $versionParts[0]
$minor = $versionParts[1]
$patch = [int]$versionParts[2] + 1
$nextVersion = "$major.$minor.$patch-SNAPSHOT"

Write-Host "[7/7] Setting next development version: $nextVersion" -ForegroundColor Cyan
mvn versions:set -DnewVersion=$nextVersion
mvn versions:commit

git add pom.xml
git commit -m "Prepare for next development iteration"
git push origin $currentBranch

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "Release completed successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Version $Version has been published to Maven Central" -ForegroundColor White
Write-Host ""
Write-Host "Check deployment status:" -ForegroundColor Yellow
Write-Host "- Central Portal: https://central.sonatype.com/publishing/deployments"
Write-Host "- Maven Central: https://search.maven.org/artifact/io.github.fluxsce/flux-service-center-sdk/$Version/jar"
Write-Host ""
Write-Host "Notes:" -ForegroundColor Yellow
Write-Host "- Artifacts will sync to Maven Central in 30 minutes - 4 hours"
Write-Host "- Using autoPublish=true, no manual action needed"
Write-Host "- Check Central Portal for detailed logs if needed"

