@echo off
setlocal EnableExtensions
cd /d "%~dp0"
set "SITE=%~dp0com.ktylabs.stm32configurator.updatesite"
if not exist "KTYLabs-Signing.p12" (
  echo ERROR: Run GenerateCertificate.bat first.
  exit /b 1
)
where jarsigner >nul 2>&1
if errorlevel 1 (
  echo ERROR: jarsigner not found. Add the JDK bin folder to PATH.
  exit /b 1
)
where py >nul 2>&1
if errorlevel 1 (
  echo ERROR: Python launcher 'py' not found. Install Python 3.
  exit /b 1
)
for /f "usebackq delims=" %%V in (`py -3 ReleaseTools.py version`) do set "VERSION=%%V"
if not defined VERSION exit /b 1
set "PLUGIN=%SITE%\plugins\com.ktylabs.stm32configurator_%VERSION%.jar"
set "FEATURE=%SITE%\features\com.ktylabs.stm32configurator.feature_%VERSION%.jar"
if not exist "%PLUGIN%" (
 echo ERROR: Plugin JAR not found: "%PLUGIN%"
 echo In Eclipse: open the update-site project, click Build All, then retry.
 exit /b 1
)
if not exist "%FEATURE%" (
 echo ERROR: Feature JAR not found: "%FEATURE%"
 exit /b 1
)
echo Signing KTYLabs version %VERSION% ...
for %%F in ("%PLUGIN%" "%FEATURE%") do (
  jarsigner -keystore "KTYLabs-Signing.p12" -storetype PKCS12 -sigalg SHA256withRSA -digestalg SHA-256 "%%~fF" ktylabs
  if errorlevel 1 exit /b 1
  jarsigner -verify "%%~fF"
  if errorlevel 1 exit /b 1
)
py -3 ReleaseTools.py package
if errorlevel 1 exit /b 1
echo.
echo Signed update site: "%~dp0STM32-CMake-Configurator.zip"
echo SHA-256 checksum: "%~dp0STM32-CMake-Configurator.sha256.txt"
echo Do NOT rebuild the update site after signing without rerunning SignRelease.bat.
endlocal
