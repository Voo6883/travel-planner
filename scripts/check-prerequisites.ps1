#!/usr/bin/env pwsh
$ErrorActionPreference = "Stop"
node (Join-Path $PSScriptRoot "check-prerequisites.mjs")
