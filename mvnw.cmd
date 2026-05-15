@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET "BASE_DIR=%~dp0") ELSE SET "BASE_DIR=%__MVNW_ARG0_NAME__%"

@SET MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%
@IF NOT "%MAVEN_PROJECTBASEDIR%"=="" GOTO endDetectBaseDir

@SET EXEC_DIR=%CD%
@SET WDIR=%EXEC_DIR%
:findBaseDir
@IF EXIST "%WDIR%\"".mvn" GOTO baseDirFound
@CD ..
@IF "%WDIR%"=="%CD%" GOTO baseDirNotFound
@SET "WDIR=%CD%"
@GOTO findBaseDir

:baseDirFound
@SET "MAVEN_PROJECTBASEDIR=%WDIR%"
@CD "%EXEC_DIR%"
@GOTO endDetectBaseDir

:baseDirNotFound
@SET "MAVEN_PROJECTBASEDIR=%EXEC_DIR%"
@CD "%EXEC_DIR%"

:endDetectBaseDir

@IF NOT EXIST "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties" (
  @ECHO Could not find .mvn\wrapper\maven-wrapper.properties >&2
  @EXIT /B 1
)

@SET /P DISTRIBUTION_URL= <"%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties"

@FOR /F "usebackq tokens=1,* delims==" %%A IN ("%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties") DO (
  @IF "%%A"=="distributionUrl" SET "DISTRIBUTION_URL=%%B"
)

@FOR %%i IN ("%DISTRIBUTION_URL%") DO SET "DISTRIBUTION_FILENAME=%%~nxi"
@FOR %%i IN ("%DISTRIBUTION_FILENAME%") DO SET "DISTRIBUTION_NAME=%%~ni"

@SET "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\%DISTRIBUTION_NAME%"

@IF EXIST "%MAVEN_HOME%\bin\mvn.cmd" GOTO init

@IF NOT EXIST "%MAVEN_HOME%" MD "%MAVEN_HOME%"

@IF EXIST "%TEMP%\%DISTRIBUTION_FILENAME%" DEL /F /Q "%TEMP%\%DISTRIBUTION_FILENAME%"

@WHERE curl >NUL 2>&1
@IF %ERRORLEVEL% EQU 0 (
  curl -fL -o "%TEMP%\%DISTRIBUTION_FILENAME%" "%DISTRIBUTION_URL%"
) ELSE (
  powershell -Command "& { Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%TEMP%\%DISTRIBUTION_FILENAME%' }"
)

powershell -Command "& { Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory('%TEMP%\%DISTRIBUTION_FILENAME%', '%USERPROFILE%\.m2\wrapper') }"

:init
@SET MAVEN_CMD_LINE_ARGS=%*
"%MAVEN_HOME%\bin\mvn.cmd" %MAVEN_CMD_LINE_ARGS%
