@echo off
setlocal
set PROJECT_ROOT=%~1
if "%PROJECT_ROOT%"=="" set PROJECT_ROOT=.
set MODULE=%~2
if "%MODULE%"=="" set MODULE=app
set VARIANT=%~3
if "%VARIANT%"=="" set VARIANT=debug
set OUT_DIR=%~4
if "%OUT_DIR%"=="" set OUT_DIR=uml-out
set SCRIPT_DIR=%~dp0
pushd "%SCRIPT_DIR%"
javac --release 17 AndroidUmlExtractor.java || goto :error
java AndroidUmlExtractor --project "%PROJECT_ROOT%" --module "%MODULE%" --variant "%VARIANT%" --out "%OUT_DIR%" || goto :error
popd
exit /b 0
:error
popd
exit /b 1
