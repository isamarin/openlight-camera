#!/usr/bin/env node
/**
 * openlight-camera MCP server.
 *
 * Drives a Light L16 over adb: shutter, focus and the manual controls the app
 * already exposes through its CI test-intent surface, plus logs and capture
 * retrieval.
 *
 * Control path (reverse-engineered from TestIntentManager / TestIntentReceiver):
 *   broadcast openlight.co.intent.CITEST_INTENT with string extras whose keys
 *   come from the CITest enum. startTest() walks the bundle keys, matches each
 *   against CITest.getTestKey() and dispatches the value.
 *
 * The receiver is registered at runtime by ImagePreviewFragment, so the camera
 * app has to be in the foreground for control calls to land. enable_test_mode
 * covers the other precondition: TestIntentManager's constructor requires
 * /sdcard/fkitten.txt or /sdcard/lightest.txt to exist, gated by the
 * feature.cli_support flag (default true).
 *
 * Transport: MCP over stdio, newline-delimited JSON-RPC 2.0. No dependencies.
 *
 * Env:
 *   OPENLIGHT_ADB_SERIAL  target device (default: the only connected one)
 *   OPENLIGHT_PACKAGE     app package (default: openlight.co.lightcamera)
 */

import { execFile } from "node:child_process";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

const PROTOCOL_VERSION = "2025-06-18";
const SERVER_INFO = { name: "openlight-camera", version: "1.0.0" };

const BETA_PACKAGE = process.env.OPENLIGHT_PACKAGE || "openlight.co.lightcamera";
const STOCK_PACKAGE = "light.co.lightcamera";
const LAUNCH_ACTIVITY = "openlight.co.camera.CameraActivity";
const CITEST_ACTION = "openlight.co.intent.CITEST_INTENT";
const DCIM = "/sdcard/DCIM/Camera";

/** CITest enum → bundle key, with the value each dispatch expects. */
const CONTROLS = {
  iso: { key: "iso_test", hint: "ISO index, or 'auto'" },
  exposure_time: { key: "exposure_test", hint: "shutter, e.g. '1/60' or '0.5'" },
  ev: { key: "ev_test", hint: "exposure compensation index" },
  flash: { key: "flash_test", hint: "on | off | auto" },
  metering: { key: "metering_test", hint: "center | center-weighted | spot | touch-weighted" },
  mode: { key: "mode_test", hint: "camera mode name" },
  timer: { key: "timer_test", hint: "3 | 5 | 10 | 20 | off" },
  burst: { key: "burst_test", hint: "on | off" },
  caf: { key: "caf_test", hint: "continuous AF mode" },
  audio: { key: "audio_test", hint: "shutter audio setting" },
  focal_length: { key: "focal_length_test", hint: "focal length in mm, e.g. '28'" },
};

// ---------------------------------------------------------------- adb helpers

function adbArgs(args) {
  const serial = process.env.OPENLIGHT_ADB_SERIAL;
  return serial ? ["-s", serial, ...args] : args;
}

async function adb(args, { timeout = 60000, maxBuffer = 32 * 1024 * 1024 } = {}) {
  try {
    const { stdout, stderr } = await execFileAsync("adb", adbArgs(args), { timeout, maxBuffer });
    return { ok: true, stdout: stdout ?? "", stderr: stderr ?? "" };
  } catch (err) {
    return {
      ok: false,
      stdout: err.stdout ?? "",
      stderr: err.stderr ?? String(err.message ?? err),
    };
  }
}

const sh = (script, opts) => adb(["shell", script], opts);

/** Quote a value for safe interpolation into an adb shell command line. */
const q = (value) => `'${String(value).replace(/'/g, `'\\''`)}'`;

// ------------------------------------------------------------------ tool impl

async function requireDevice() {
  const { stdout } = await adb(["devices"]);
  const lines = stdout
    .split("\n")
    .slice(1)
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith("*"));
  const ready = lines.filter((l) => l.endsWith("\tdevice") || / device$/.test(l));
  if (ready.length === 0) {
    throw new Error(
      lines.length
        ? `No device in 'device' state:\n${lines.join("\n")}`
        : "No device connected. Check the USB cable and that adb sees the L16."
    );
  }
  return ready.map((l) => l.split(/\s+/)[0]);
}

async function deviceInfo() {
  const serials = await requireDevice();
  const props = [
    "ro.product.model",
    "ro.build.version.release",
    "ro.build.display.id",
    "ro.build.type",
    // ro.boot.flash.locked is unset on this device; verified boot and dm-verity
    // are what actually establish that /system cannot be modified.
    "ro.boot.verifiedbootstate",
    "ro.boot.veritymode",
  ];
  // Emit key=value per line: a property with an empty value must not shift the
  // rest of the list, which a bare `getprop` sequence would do.
  const { stdout: propOut } = await sh(props.map((p) => `echo "${p}=$(getprop ${p})"`).join("; "));
  const values = new Map(
    propOut
      .split("\n")
      .map((l) => l.trim())
      .filter(Boolean)
      .map((l) => {
        const eq = l.indexOf("=");
        return [l.slice(0, eq), l.slice(eq + 1)];
      })
  );
  const { stdout: dfOut } = await sh("df /sdcard | tail -1");

  const lines = [`serial: ${serials.join(", ")}`];
  props.forEach((p) => lines.push(`${p}: ${values.get(p) || "(unset)"}`));
  lines.push(`storage: ${dfOut.trim()}`);
  return lines.join("\n");
}

async function appStatus() {
  await requireDevice();
  const out = [];
  for (const [label, pkg] of [["beta", BETA_PACKAGE], ["stock", STOCK_PACKAGE]]) {
    const { stdout: listed } = await sh(`pm list packages ${q(pkg)}`);
    const installed = listed.includes(pkg);
    let detail = "not installed";
    if (installed) {
      const { stdout: dump } = await sh(`dumpsys package ${q(pkg)} | grep -E 'versionName|codePath'`);
      const { stdout: ps } = await sh(`ps | grep ${q(pkg)} | grep -v grep`);
      detail = `${dump.trim().replace(/\s+/g, " ")} | ${ps.trim() ? "running" : "not running"}`;
    }
    out.push(`${label} (${pkg}): ${detail}`);
  }
  const { stdout: focus } = await sh("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'");
  out.push(`foreground: ${focus.trim() || "unknown"}`);
  return out.join("\n");
}

async function enableTestMode({ debug_logs = true }) {
  await requireDevice();
  const steps = [];

  const marker = await sh("touch /sdcard/lightest.txt && ls -l /sdcard/lightest.txt");
  steps.push(`marker: ${marker.ok ? marker.stdout.trim() : `FAILED ${marker.stderr.trim()}`}`);

  if (debug_logs) {
    // FeatureManager reads /sdcard/features.prop at startup; debug_on_user
    // flips CommonConstants.IS_USER_BUILD, which gates LogUtil entirely.
    const props = ["debug_on_user=true", "feature.cli_support=true"].join("\\n");
    const write = await sh(`printf '%b\\n' ${q(props)} > /sdcard/features.prop && cat /sdcard/features.prop`);
    steps.push(`features.prop: ${write.ok ? write.stdout.trim() : `FAILED ${write.stderr.trim()}`}`);
  }

  steps.push("NOTE: restart the camera app for these to take effect.");
  return steps.join("\n");
}

async function launchApp({ target = "beta" }) {
  await requireDevice();
  const pkg = target === "stock" ? STOCK_PACKAGE : BETA_PACKAGE;
  const activity =
    target === "stock" ? "light.co.camera.CameraActivity" : `${pkg}/${LAUNCH_ACTIVITY}`;
  const component = target === "stock" ? `${pkg}/${activity}` : activity;
  const res = await sh(`am start -n ${q(component)}`);
  return res.ok ? res.stdout.trim() || "started" : `FAILED: ${res.stderr.trim()}`;
}

async function sendTestIntent(extras) {
  await requireDevice();
  const pairs = Object.entries(extras)
    .map(([k, v]) => `--es ${k} ${q(v)}`)
    .join(" ");
  const res = await sh(`am broadcast -a ${CITEST_ACTION} ${pairs}`);
  if (!res.ok) return `FAILED: ${res.stderr.trim()}`;
  const out = res.stdout.trim();
  // A delivered broadcast reports result=0; nothing here proves the app acted
  // on it, so surface the raw result and let the caller check logs.
  return `${out}\n(sent ${JSON.stringify(extras)}; app must be in foreground to receive)`;
}

async function capture() {
  return sendTestIntent({ capture_test: "1" });
}

async function focus({ x, y }) {
  const value = x != null && y != null ? `${x},${y}` : "1";
  return sendTestIntent({ focus_test: value });
}

async function setControl({ control, value }) {
  const spec = CONTROLS[control];
  if (!spec) {
    throw new Error(`Unknown control '${control}'. Known: ${Object.keys(CONTROLS).join(", ")}`);
  }
  return sendTestIntent({ [spec.key]: value });
}

async function logs({ lines = 200, filter, clear = false, since_app_only = true }) {
  await requireDevice();
  if (clear) await adb(["logcat", "-c"]);

  const { stdout } = await adb(["logcat", "-d", "-v", "time"], { maxBuffer: 64 * 1024 * 1024 });
  let out = stdout.split("\n");

  if (since_app_only) {
    // App tags carry no package prefix, so match on the PID of our process.
    const { stdout: ps } = await sh(`ps | grep ${q(BETA_PACKAGE)} | grep -v grep`);
    const pid = ps.trim().split(/\s+/)[1];
    if (pid) out = out.filter((l) => l.includes(`(${pid.padStart(5)})`) || l.includes(`( ${pid})`));
  }
  if (filter) {
    const re = new RegExp(filter, "i");
    out = out.filter((l) => re.test(l));
  }
  const tail = out.slice(-lines).join("\n");
  return tail.trim() || "(no matching log lines — is debug_on_user set and the app restarted?)";
}

async function listCaptures({ limit = 30 }) {
  await requireDevice();
  const { stdout } = await sh(`ls -l ${DCIM} | tail -${Number(limit) + 1}`);
  const { stdout: counts } = await sh(
    `for e in lri jpg mp4; do printf '%s: ' "$e"; find ${DCIM} -maxdepth 1 -name "*.$e" | wc -l; done`
  );
  return `${counts.trim()}\n\n${stdout.trim()}`;
}

async function pullCapture({ name, dest }) {
  await requireDevice();
  if (!name || /[/\s]/.test(name)) throw new Error("name must be a bare filename in DCIM/Camera");
  const target = dest || `./${name}`;
  const res = await adb(["pull", `${DCIM}/${name}`, target], { timeout: 600000 });
  return res.ok ? res.stdout.trim() : `FAILED: ${res.stderr.trim()}`;
}

// ------------------------------------------------------------------ tool table

const TOOLS = [
  {
    name: "device_info",
    description: "Model, Android build, bootloader lock state and /sdcard usage of the connected L16.",
    inputSchema: { type: "object", properties: {} },
    handler: deviceInfo,
  },
  {
    name: "app_status",
    description:
      "Whether the openlight beta and the stock camera are installed and running, plus what is in the foreground.",
    inputSchema: { type: "object", properties: {} },
    handler: appStatus,
  },
  {
    name: "enable_test_mode",
    description:
      "Precondition for every control call: writes the /sdcard/lightest.txt marker TestIntentManager requires, and optionally a features.prop with debug_on_user=true to turn on logging. Requires an app restart.",
    inputSchema: {
      type: "object",
      properties: {
        debug_logs: { type: "boolean", description: "Also enable logging via features.prop (default true)" },
      },
    },
    handler: enableTestMode,
  },
  {
    name: "launch_app",
    description: "Start the camera app. Controls only work while it is in the foreground.",
    inputSchema: {
      type: "object",
      properties: { target: { type: "string", enum: ["beta", "stock"], description: "default beta" } },
    },
    handler: launchApp,
  },
  {
    name: "capture",
    description: "Trigger the shutter.",
    inputSchema: { type: "object", properties: {} },
    handler: capture,
  },
  {
    name: "focus",
    description: "Trigger autofocus, optionally at a screen point.",
    inputSchema: {
      type: "object",
      properties: {
        x: { type: "number", description: "x coordinate; omit for a plain AF trigger" },
        y: { type: "number", description: "y coordinate" },
      },
    },
    handler: focus,
  },
  {
    name: "set_control",
    description:
      `Set one manual control. Available: ${Object.entries(CONTROLS)
        .map(([name, s]) => `${name} (${s.hint})`)
        .join("; ")}.`,
    inputSchema: {
      type: "object",
      properties: {
        control: { type: "string", enum: Object.keys(CONTROLS) },
        value: { type: "string" },
      },
      required: ["control", "value"],
    },
    handler: setControl,
  },
  {
    name: "logs",
    description:
      "Read logcat, by default narrowed to the beta app's PID. The app logs nothing unless debug_on_user is set (see enable_test_mode).",
    inputSchema: {
      type: "object",
      properties: {
        lines: { type: "number", description: "tail length, default 200" },
        filter: { type: "string", description: "case-insensitive regex" },
        clear: { type: "boolean", description: "clear the buffer before reading" },
        since_app_only: { type: "boolean", description: "restrict to the app's PID (default true)" },
      },
    },
    handler: logs,
  },
  {
    name: "list_captures",
    description: "Counts of .lri/.jpg/.mp4 in DCIM/Camera and the most recent entries.",
    inputSchema: {
      type: "object",
      properties: { limit: { type: "number", description: "entries to show, default 30" } },
    },
    handler: listCaptures,
  },
  {
    name: "pull_capture",
    description: "Copy one file out of DCIM/Camera to the host. .lri files are 160-180 MB.",
    inputSchema: {
      type: "object",
      properties: {
        name: { type: "string", description: "bare filename, e.g. L16_00104.jpg" },
        dest: { type: "string", description: "host path, default ./<name>" },
      },
      required: ["name"],
    },
    handler: pullCapture,
  },
];

const TOOLS_BY_NAME = new Map(TOOLS.map((t) => [t.name, t]));

// -------------------------------------------------------------- JSON-RPC loop

function send(message) {
  process.stdout.write(JSON.stringify(message) + "\n");
}

function respond(id, result) {
  send({ jsonrpc: "2.0", id, result });
}

function respondError(id, code, message) {
  send({ jsonrpc: "2.0", id, error: { code, message } });
}

async function handle(request) {
  const { id, method, params } = request;

  switch (method) {
    case "initialize":
      return respond(id, {
        protocolVersion: params?.protocolVersion || PROTOCOL_VERSION,
        capabilities: { tools: {} },
        serverInfo: SERVER_INFO,
      });

    case "notifications/initialized":
      return; // notification: no reply

    case "ping":
      return respond(id, {});

    case "tools/list":
      return respond(id, {
        tools: TOOLS.map(({ name, description, inputSchema }) => ({ name, description, inputSchema })),
      });

    case "tools/call": {
      const tool = TOOLS_BY_NAME.get(params?.name);
      if (!tool) return respondError(id, -32602, `Unknown tool: ${params?.name}`);
      try {
        const text = await tool.handler(params.arguments ?? {});
        return respond(id, { content: [{ type: "text", text: String(text) }] });
      } catch (err) {
        return respond(id, {
          content: [{ type: "text", text: `Error: ${err.message ?? err}` }],
          isError: true,
        });
      }
    }

    default:
      if (id !== undefined) respondError(id, -32601, `Method not found: ${method}`);
  }
}

let buffer = "";
let inFlight = 0;
let stdinClosed = false;

/** Exit only once stdin is done AND every dispatched request has answered. */
function exitWhenIdle() {
  if (stdinClosed && inFlight === 0) process.exit(0);
}

process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => {
  buffer += chunk;
  let newline;
  while ((newline = buffer.indexOf("\n")) !== -1) {
    const line = buffer.slice(0, newline).trim();
    buffer = buffer.slice(newline + 1);
    if (!line) continue;
    let request;
    try {
      request = JSON.parse(line);
    } catch {
      respondError(null, -32700, "Parse error");
      continue;
    }
    inFlight++;
    handle(request)
      .catch((err) => {
        if (request.id !== undefined) respondError(request.id, -32603, String(err.message ?? err));
      })
      .finally(() => {
        inFlight--;
        exitWhenIdle();
      });
  }
});

process.stdin.on("end", () => {
  stdinClosed = true;
  exitWhenIdle();
});
