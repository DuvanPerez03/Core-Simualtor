# ============================================================
#  run.ps1  —  Ejecutar CoreSimulator
#  Uso: .\run.ps1
# ============================================================

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  CoreSimulator - Iniciando..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Compilar con Maven
Write-Host "[1/2] Compilando con Maven..." -ForegroundColor Yellow
& mvn compile -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Fallo la compilacion Maven." -ForegroundColor Red
    exit 1
}
Write-Host "      Compilacion exitosa." -ForegroundColor Green

# 2. Construir el module-path con los JARs de JavaFX (win) del repositorio Maven local
Write-Host "[2/2] Iniciando la aplicacion JavaFX..." -ForegroundColor Yellow

$m2 = "$env:USERPROFILE\.m2\repository\org\openjfx"
$jfxJars = Get-ChildItem $m2 -Filter "*win*.jar" -Recurse `
    | Where-Object { $_.Name -notmatch "sources|javadoc" } `
    | ForEach-Object { $_.FullName }

if (-not $jfxJars) {
    Write-Host "ERROR: No se encontraron los JARs de JavaFX en ~/.m2" -ForegroundColor Red
    Write-Host "       Ejecuta primero: mvn compile  (descarga las dependencias)" -ForegroundColor Yellow
    exit 1
}

$modulePath = ("$root\target\classes" + ";" + ($jfxJars -join ";"))

$jvmArgs = @(
    "--module-path", $modulePath,
    "--add-modules", "javafx.controls,javafx.fxml,javafx.graphics",
    "-Dprism.order=sw",
    "-Dprism.verbose=false",
    "-Djava.awt.headless=false",
    "-m", "com.coresimulator/com.coresimulator.MainApp"
)

Write-Host ""
& java $jvmArgs

Write-Host ""
Write-Host "Aplicacion cerrada." -ForegroundColor Cyan
