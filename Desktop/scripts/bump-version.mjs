import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const version = process.argv[2];
if (!/^\d+\.\d+\.\d+$/.test(version ?? "")) {
	console.error("Usage: bun run bump <major.minor.patch>");
	process.exit(1);
}

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");

function updateJson(relativePath) {
	const path = resolve(root, relativePath);
	const json = JSON.parse(readFileSync(path, "utf8"));
	json.version = version;
	writeFileSync(path, JSON.stringify(json, null, 2) + "\n");
}

function updateCargoToml() {
	const path = resolve(root, "src-tauri/Cargo.toml");
	const content = readFileSync(path, "utf8");
	writeFileSync(path, content.replace(/^version = "[^"]*"/m, `version = "${version}"`));
}

function updateCargoLock() {
	const path = resolve(root, "src-tauri/Cargo.lock");
	const content = readFileSync(path, "utf8");
	writeFileSync(
		path,
		content.replace(
			/(\[\[package\]\]\r?\nname = "gloom"\r?\nversion = ")[^"]*(")/,
			`$1${version}$2`
		)
	);
}

function updateInnoSetup() {
	const paths = [
		resolve(root, "../installer/gloom.iss"),
		resolve(root, "installer/gloom.iss"),
	];
	for (const path of paths) {
		try {
			const content = readFileSync(path, "utf8");
			writeFileSync(path, content.replace(/#define MyAppVersion "[^"]*"/, `#define MyAppVersion "${version}"`));
		} catch {
			// Ignore if not present
		}
	}
}

function updateInstallerApp() {
	const paths = [
		resolve(root, "../installer-app/package.json"),
		resolve(root, "../installer-app/src-tauri/tauri.conf.json"),
	];
	for (const path of paths) {
		try {
			const json = JSON.parse(readFileSync(path, "utf8"));
			json.version = version;
			writeFileSync(path, JSON.stringify(json, null, 2) + "\n");
		} catch {
			// Ignore if not present
		}
	}
	const cargoPath = resolve(root, "../installer-app/src-tauri/Cargo.toml");
	try {
		const content = readFileSync(cargoPath, "utf8");
		writeFileSync(cargoPath, content.replace(/^version = "[^"]*"/m, `version = "${version}"`));
	} catch {
		// Ignore if not present
	}
}

updateJson("package.json");
updateJson("src-tauri/tauri.conf.json");
updateCargoToml();
updateCargoLock();
updateInnoSetup();
updateInstallerApp();

console.log(`Bumped package.json, tauri.conf.json, Cargo.toml, Cargo.lock, installer-app and gloom.iss to ${version}`);
