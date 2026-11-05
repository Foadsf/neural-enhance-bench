# Automated FLOSS Android Build Script
# Directly builds release/debug APK using verified local Android Command Line Tools & OpenJDK 17
# Zero external network downloads or Gradle daemon locks required.

$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $SCRIPT_DIR

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Building FLOSS CNN & VLM Enhancement Tool APK" -ForegroundColor Cyan
Write-Host " Target Device: Samsung Galaxy Note10 (Exynos 9825)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Resolve JDK and SDK paths
$JAVA_HOME = "C:\Users\FoadS\scoop\apps\openjdk17\current"
if (!(Test-Path $JAVA_HOME)) {
    $JAVA_HOME = $env:JAVA_HOME
}
$env:PATH = "$JAVA_HOME\bin;" + $env:PATH
Write-Host "[1/8] Using Java at: $JAVA_HOME" -ForegroundColor Green

$BUILD_TOOLS = "C:\Users\FoadS\scoop\apps\android-clt\current\build-tools\34.0.0"
$PLATFORM_JAR = "C:\Users\FoadS\scoop\apps\android-clt\current\platforms\android-34\android.jar"
$AAPT2 = "$BUILD_TOOLS\aapt2.exe"
$D8 = "$BUILD_TOOLS\d8.bat"
$ZIPALIGN = "$BUILD_TOOLS\zipalign.exe"
$APKSIGNER = "$BUILD_TOOLS\apksigner.bat"

if (!(Test-Path $PLATFORM_JAR)) {
    Write-Error "android.jar not found at $PLATFORM_JAR"
    exit 1
}

# 2. Setup build directory tree
$BUILD_DIR = "$SCRIPT_DIR\build"
$GEN_DIR = "$BUILD_DIR\gen"
$OBJ_DIR = "$BUILD_DIR\obj"
$BIN_DIR = "$BUILD_DIR\bin"
$INTER_DIR = "$BUILD_DIR\intermediate"

if (Test-Path $BUILD_DIR) {
    Remove-Item -Recurse -Force $BUILD_DIR
}
New-Item -ItemType Directory -Force -Path $GEN_DIR, $OBJ_DIR, $BIN_DIR, $INTER_DIR | Out-Null

# 3. AAPT2 Compile Resources
Write-Host "[2/8] Compiling resources with aapt2..." -ForegroundColor Green
& $AAPT2 compile --dir app\src\main\res -o "$INTER_DIR\res.zip"
if ($LASTEXITCODE -ne 0) {
    Write-Error "aapt2 compile failed with exit code $LASTEXITCODE"
    exit 1
}

# 4. AAPT2 Link Resources & Generate R.java
Write-Host "[3/8] Linking resources and generating R.java..." -ForegroundColor Green
& $AAPT2 link -o "$INTER_DIR\app.unaligned.apk" `
    -I $PLATFORM_JAR `
    --manifest app\src\main\AndroidManifest.xml `
    --version-code 1 `
    --version-name "1.0.0" `
    --min-sdk-version 26 `
    --target-sdk-version 34 `
    --java $GEN_DIR `
    --auto-add-overlay `
    "$INTER_DIR\res.zip"
if ($LASTEXITCODE -ne 0) {
    Write-Error "aapt2 link failed with exit code $LASTEXITCODE"
    exit 1
}

# 5. Compile Java Sources
Write-Host "[4/8] Compiling Java sources with javac..." -ForegroundColor Green
$javaFiles = Get-ChildItem -Recurse -Path "app\src\main\java", $GEN_DIR -Filter *.java | ForEach-Object { $_.FullName }
Write-Host "      Found $($javaFiles.Count) Java source files."

$sourceListFile = "$INTER_DIR\sources.txt"
[System.IO.File]::WriteAllLines($sourceListFile, $javaFiles, [System.Text.Encoding]::ASCII)

& "$JAVA_HOME\bin\javac.exe" -encoding UTF-8 -cp $PLATFORM_JAR -d $OBJ_DIR -source 1.8 -target 1.8 "@$sourceListFile"
if ($LASTEXITCODE -ne 0) {
    Write-Error "javac compilation failed with exit code $LASTEXITCODE"
    exit 1
}

# 6. Dex Class Files with D8
Write-Host "[5/8] Converting bytecode to Dalvik Executable (classes.dex) with d8..." -ForegroundColor Green
$classFiles = Get-ChildItem -Recurse -Path $OBJ_DIR -Filter *.class | ForEach-Object { $_.FullName }

& $D8 --output $BIN_DIR --lib $PLATFORM_JAR --min-api 26 $classFiles
if ($LASTEXITCODE -ne 0) {
    Write-Error "d8 dex conversion failed with exit code $LASTEXITCODE"
    exit 1
}

# 7. Add classes.dex into APK archive
Write-Host "[6/8] Packaging classes.dex into APK with jar..." -ForegroundColor Green
& "$JAVA_HOME\bin\jar.exe" uf "$INTER_DIR\app.unaligned.apk" -C "$BIN_DIR" classes.dex
if ($LASTEXITCODE -ne 0) {
    Write-Error "jar update failed with exit code $LASTEXITCODE"
    exit 1
}

# 8. Align APK with zipalign
Write-Host "[7/8] Aligning APK with zipalign 4-byte boundaries..." -ForegroundColor Green
$alignedApk = "$INTER_DIR\NeuralEnhanceBench.aligned.apk"
& $ZIPALIGN -v -f -p 4 "$INTER_DIR\app.unaligned.apk" $alignedApk
if ($LASTEXITCODE -ne 0) {
    Write-Error "zipalign failed with exit code $LASTEXITCODE"
    exit 1
}

# 9. Sign APK with apksigner
Write-Host "[8/8] Signing APK with apksigner..." -ForegroundColor Green
$keystore = "$SCRIPT_DIR\debug.keystore"
if (!(Test-Path $keystore)) {
    Write-Host "      Generating debug signing keystore..."
    & "$JAVA_HOME\bin\keytool.exe" -genkeypair -validity 10000 `
        -dname "CN=FLOSS_Developer, O=FLOSS, C=US" `
        -keystore $keystore -storepass android -keypass android `
        -alias androiddebugkey -keyalg RSA -keysize 2048
}

$finalApk = "$SCRIPT_DIR\NeuralEnhanceBench.apk"
& $APKSIGNER sign --ks $keystore --ks-pass pass:android --key-pass pass:android `
    --ks-key-alias androiddebugkey --out $finalApk $alignedApk
if ($LASTEXITCODE -ne 0) {
    Write-Error "apksigner failed with exit code $LASTEXITCODE"
    exit 1
}

# Verify final APK
Write-Host "`nVerifying final APK signature:" -ForegroundColor Cyan
& $APKSIGNER verify --verbose $finalApk

$apkItem = Get-Item $finalApk
Write-Host "`nSUCCESS! Generated APK:" -ForegroundColor Green
Write-Host "Path: $($apkItem.FullName)" -ForegroundColor Yellow
Write-Host "Size: $([math]::Round($apkItem.Length / 1024, 2)) KB ($($apkItem.Length) bytes)" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
