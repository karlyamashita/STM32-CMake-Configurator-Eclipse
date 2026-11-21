@echo off
setlocal
cd /d "%~dp0"
where keytool >nul 2>&1
if errorlevel 1 (
  echo ERROR: JDK keytool not found. Add your JDK bin folder to PATH.
  exit /b 1
)
if exist "KTYLabs-Signing.p12" (
  echo Certificate already exists. Refusing to overwrite it.
  exit /b 1
)
echo Generating a self-signed KTYLabs JAR signing certificate.
echo Keep KTYLabs-Signing.p12 and its password PRIVATE. Do not include it in the ZIP.
keytool -genkeypair -alias ktylabs -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 3650 -storetype PKCS12 -keystore "KTYLabs-Signing.p12" -dname "CN=KTYLabs, O=KTYLabs, C=US" -ext "KU=digitalSignature" -ext "EKU=codeSigning"
if errorlevel 1 exit /b 1
echo.
echo Certificate created. Record the SHA-256 fingerprint below and publish it on ktylabs.com:
keytool -list -v -keystore "KTYLabs-Signing.p12" -alias ktylabs | findstr /C:"SHA256:"
endlocal
