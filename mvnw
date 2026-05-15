#!/bin/bash
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
# ----------------------------------------------------------------------------

set -euf
[ "${MVNW_VERBOSE-}" != debug ] || set -x

# OS specific support.
native_path() { printf %s\\n "$1"; }
case "$(uname)" in
CYGWIN* | MINGW*)
  [ -z "${JAVA_HOME-}" ] || JAVA_HOME="$(cygpath --unix "$JAVA_HOME")"
  native_path() { cygpath --path --windows "$1"; }
  ;;
esac

# set JAVACMD and JAVACCMD
set_java_home() {
  # For Cygwin and MinGW, ensure paths are in Unix format before anything is touched
  if [ -n "${JAVA_HOME-}" ]; then
    if [ -x "$JAVA_HOME/jre/sh/java" ]; then
      # IBM's JDK on AIX uses strange locations for the executables
      JAVACMD="$JAVA_HOME/jre/sh/java"
      JAVACCMD="$JAVA_HOME/jre/sh/javac"
    else
      JAVACMD="$JAVA_HOME/bin/java"
      JAVACCMD="$JAVA_HOME/bin/javac"
      if [ ! -x "$JAVACMD" ] || [ ! -x "$JAVACCMD" ]; then
        echo "The JAVA_HOME environment variable is not defined correctly, so mvnw cannot run." >&2
        echo "JAVA_HOME is set to \"$JAVA_HOME\", but \"\$JAVA_HOME/bin/java\" or \"\$JAVA_HOME/bin/javac\" does not exist." >&2
        return 1
      fi
    fi
  else
    JAVACMD="$(
      'set' +e
      'unset' -f command 2>/dev/null
      'command' -v java
    )" || :
    JAVACCMD="$(
      'set' +e
      'unset' -f command 2>/dev/null
      'command' -v javac
    )" || :
    if [ ! -x "${JAVACMD-}" ] || [ ! -x "${JAVACCMD-}" ]; then
      echo "The java/javac command does not exist in PATH nor is JAVA_HOME set, so mvnw cannot run." >&2
      return 1
    fi
  fi
}

# hash string like Java String::hashCode
hash_string() {
  str="${1:-}" h=0
  while [ -n "$str" ]; do
    char="${str%"${str#?}"}"
    h=$(( ( h * 31 + $(LC_CTYPE=C printf %d "'$char") ) % 4294967296 ))
    str="${str#?}"
  done
  printf %x\\n $h
}

verbose() { :; }
[ "${MVNW_VERBOSE-}" != true ] || verbose() { printf %s\\n "${1-}"; }

die() {
  printf %s\\n "$1" >&2
  exit 1
}

trim() {
  # MWRAPPER-139:
  #   Trims trailing and leading whitespace, carriage returns, tabs, and linefeeds.
  #   Needed for removing poorly-formatted comments from maven-wrapper.properties
  printf "%s" "${1}" | tr -d '\r\n\t' | sed -e 's/[[:space:]]*$//' -e 's/^[[:space:]]*//'
}

# parse distributionUrl and optional distributionSha256Sum, wrapperUrl from maven-wrapper.properties
while IFS="=" read -r key value; do
  case "${key-}" in
  distributionUrl) distributionUrl=$(trim "${value-}") ;;
  distributionSha256Sum) distributionSha256Sum=$(trim "${value-}") ;;
  wrapperUrl) wrapperUrl=$(trim "${value-}") ;;
  esac
done <"${0%/*}/.mvn/wrapper/maven-wrapper.properties"
[ -n "${distributionUrl-}" ] || die "cannot read distributionUrl property in ${0%/*}/.mvn/wrapper/maven-wrapper.properties"

case "${distributionUrl##*/}" in
maven-mvnd-*)
  MVN_CMD=mvnd.sh
  distributionUrlName="${distributionUrl##*/}"
  distributionUrlNameMain="${distributionUrlName%%-bin.*}"
  distributionUrlNameMain="${distributionUrlNameMain%%-src.*}"
  ;;
*)
  MVN_CMD=mvn
  distributionUrlName="${distributionUrl##*/}"
  distributionUrlNameMain="${distributionUrlName%%-bin.*}"
  distributionUrlNameMain="${distributionUrlNameMain%%-src.*}"
  ;;
esac

# find MVNW_REPODIR
if [ "${MVNW_REPODIR-}" ]; then
  [ -d "${MVNW_REPODIR}" ] || die "MVNW_REPODIR is not a directory: ${MVNW_REPODIR}"
  distributionRepository="${MVNW_REPODIR}"
else
  distributionRepository="${HOME}/.m2/wrapper"
fi

distributionPath="${distributionRepository}/${distributionUrlNameMain}"
distributionFile="${distributionPath}/${distributionUrlName}"
distributionTmpFile="${distributionFile}.tmp"

verbose "Checking for MVN ${distributionFile}"

if [ -z "${MVNW_SKIP_DOWNLOAD-}" ]; then
  if [ ! -e "${distributionFile}" ]; then
    verbose "Downloading ${distributionUrl} to ${distributionTmpFile}"
    mkdir -p "${distributionPath}" || die "cannot create ${distributionPath}"
    set_java_home
    if command -v wget >/dev/null; then
      wget ${MVNW_VERBOSE:+--verbose} --no-config -O "${distributionTmpFile}" "${distributionUrl}" || die "wget: Could not download ${distributionUrl}"
    elif command -v curl >/dev/null; then
      curl ${MVNW_VERBOSE:+--verbose} -Lo "${distributionTmpFile}" "${distributionUrl}" || die "curl: Could not download ${distributionUrl}"
    else
      set_java_home
      javaSource="${0%/*}/.mvn/wrapper/MavenWrapperDownloader.java"
      javaClass="${0%/*}/.mvn/wrapper/MavenWrapperDownloader.class"
      [ -e "$javaSource" ] || die "$javaSource does not exist"
      if [ ! -e "$javaClass" ]; then
        verbose "Compiling MavenWrapperDownloader"
        compilation_result=$("$JAVACCMD" "$javaSource" 2>&1) &&
          verbose "$compilation_result" ||
          die "Failed to compile MavenWrapperDownloader.java: ${compilation_result}"
      fi
      verbose "Running MavenWrapperDownloader"
      download_result=$("$JAVACMD" -cp "${0%/*}/.mvn/wrapper" MavenWrapperDownloader "${distributionUrl}" "${distributionTmpFile}" 2>&1) &&
        verbose "$download_result" ||
        die "Failed to execute MavenWrapperDownloader: ${download_result}"
    fi
    if [ "${distributionSha256Sum-}" ]; then
      distributionSha256Result=false
      if command -v sha256sum >/dev/null; then
        if echo "${distributionSha256Sum}  ${distributionTmpFile}" | sha256sum --check --status; then
          distributionSha256Result=true
        fi
      elif command -v shasum >/dev/null; then
        if echo "${distributionSha256Sum}  ${distributionTmpFile}" | shasum --check --algorithm=256 --status; then
          distributionSha256Result=true
        fi
      else
        echo "Checksum validation was requested but neither 'sha256sum' nor 'shasum' are available." >&2
        echo "Please install either command, or set MVNW_SKIP_DOWNLOAD to skip validation." >&2
      fi
      [ "${distributionSha256Result}" = true ] ||
        die "Error: Failed to validate Maven distribution SHA-256, your Maven distribution might be compromised. If you updated your Maven version, you need to update the specified distributionSha256Sum property."
    fi
    mv "${distributionTmpFile}" "${distributionFile}" || die "cannot move ${distributionTmpFile} to ${distributionFile}"
  fi

  if [ ! -d "${distributionPath}/apache-maven-${distributionUrlNameMain#apache-maven-}" ]; then
    verbose "Unziping ${distributionFile}"
    case "${distributionFile##*.}" in
    zip) unzip ${MVNW_VERBOSE:+"-v"} -d "${distributionPath}" "${distributionFile}" ;;
    *) tar xzf${MVNW_VERBOSE:+"v"} "${distributionFile}" -C "${distributionPath}" ;;
    esac
  fi
fi

exec "${distributionPath}/${distributionUrlNameMain}/bin/${MVN_CMD}" "$@"
