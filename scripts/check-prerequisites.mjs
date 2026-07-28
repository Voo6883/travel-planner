#!/usr/bin/env node

import { execSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

function run(command) {
  return execSync(command, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();
}

function parseMajorMinor(version) {
  const match = version.match(/(\d+)\.(\d+)/);
  if (!match) {
    return null;
  }
  return { major: Number(match[1]), minor: Number(match[2]) };
}

function checkNode() {
  const version = run("node -v").replace(/^v/, "");
  const parsed = parseMajorMinor(version);
  if (!parsed || parsed.major < 22) {
    fail("Node.js 22.x required", `Detected ${version}. Install Node 22 from https://nodejs.org/`);
  }
  pass(`Node.js ${version}`);
}

function checkNpm() {
  const version = run("npm -v");
  const parsed = parseMajorMinor(version);
  if (!parsed || parsed.major < 10) {
    fail("npm 10+ required", `Detected ${version}. Upgrade npm with: npm install -g npm@latest`);
  }
  pass(`npm ${version}`);
}

function checkJava() {
  const output = run("java -version 2>&1");
  const match = output.match(/version "(\d+)/);
  const major = match ? Number(match[1]) : 0;
  if (major < 21) {
    fail("Java JDK 21 required", output.split("\n")[0]);
  }
  pass(`Java ${match[1]}`);
}

function checkGit() {
  const version = run("git --version").replace("git version ", "");
  pass(`Git ${version}`);
}

function checkDocker() {
  try {
    const version = run("docker --version");
    pass(version);
  } catch {
    warn("Docker not found — hybrid DB mode and full Compose stack unavailable");
  }
}

function checkDockerCompose() {
  try {
    const version = run("docker compose version");
    pass(version);
  } catch {
    warn("Docker Compose v2 not found — use host dev without Postgres, or install Docker");
  }
}

function checkGradleWrapper() {
  const isWindows = process.platform === "win32";
  const wrapperCmd = isWindows ? "gradlew.bat" : "./gradlew";
  const wrapperPath = path.join(root, "apps", "backend", wrapperCmd.replace("./", ""));
  if (!fs.existsSync(wrapperPath)) {
    warn("Gradle wrapper missing in apps/backend — backend dev will fail until Task 02 lands");
    return;
  }
  const version = run(`cd ${path.join(root, "apps", "backend")} && ${wrapperCmd} --version | head -1`);
  pass(`Gradle wrapper (${version})`);
}

function pass(message) {
  console.log(`✓ ${message}`);
}

function warn(message) {
  console.warn(`! ${message}`);
}

function fail(title, detail) {
  console.error(`✗ ${title}`);
  if (detail) {
    console.error(`  ${detail}`);
  }
  process.exit(1);
}

console.log("Travel Planner prerequisite check\n");
checkNode();
checkNpm();
checkJava();
checkGit();
checkDocker();
checkDockerCompose();
checkGradleWrapper();
console.log("\nPrerequisite check passed.");
