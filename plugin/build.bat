@echo off
setlocal

set HYTALE_JAR=libs\HytaleServer.jar
set MHUD_JAR=libs\MultipleHUD-1.0.1.jar
set SRC_DIR=src/main/java
set OUT_DIR=build

echo Compilando LincerosLevelTools...

if exist build rmdir /s /q build
mkdir build

REM Compile all Java files including hud package
javac -source 21 -target 21 -cp "%HYTALE_JAR%;%MHUD_JAR%" -d %OUT_DIR% %SRC_DIR%/com/linceros/leveltools/*.java %SRC_DIR%/com/linceros/leveltools/hud/*.java %SRC_DIR%/com/linceros/leveltools/command/*.java 2> compile_errors.txt

if %ERRORLEVEL% neq 0 (
    echo Error de compilacion. Ver compile_errors.txt para detalles.
    type compile_errors.txt
    exit /b 1
)

xcopy /s /e /y "src\main\resources\*" "build\"
cd build
jar -cf "Linceros.LevelTools-1.0.0.jar" .

echo.
echo ===================================
echo Exito! JAR creado: Linceros.LevelTools-1.0.0.jar
echo ===================================
