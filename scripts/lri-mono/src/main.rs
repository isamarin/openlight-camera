//! lri-mono — monochrome frames out of an L16 `.lri` container.
//!
//! The L16 writes every module that took part in a shot into one `.lri`: a chain
//! of "LELR" blocks with protobuf headers and packed 10-bit sensor planes. One of
//! the modules, A2, carries an AR1335 **without** a colour filter array, so its
//! plane is already panchromatic — no demosaic, no interpolation, full 4160x3120.
//! The colour modules need one of the CFA methods below.
//!
//! Methods:
//!   native  — the plane as the sensor read it. Only for the mono module.
//!   bayer   — colour plane straight to grey, CFA left unnormalised. Green sites
//!             sit brighter than red and blue, so the mosaic shows as a fixed
//!             diagonal weave. A texture, not grain: it repeats every 2 px.
//!   bin     — 2x2 CFA quads averaged into one panchromatic sample. Half the
//!             linear resolution, noise sigma halved (one stop).
//!   r, b    — one CFA phase, half resolution. The classic filter looks: red
//!             darkens sky and clears skin, blue is the orthochromatic one.
//!   g       — green sites kept, gaps filled from the four orthogonal neighbours.
//!             Full resolution off a quincunx lattice, so diagonal detail is
//!             softer than the pixel count suggests.
//!
//! Everything works on black-subtracted linear values and only then gets a
//! transfer curve, so `--linear` output is safe to stack or measure.

use std::fs::File;
use std::io::BufWriter;
use std::path::{Path, PathBuf};
use std::process::ExitCode;

use lri_rs::{LriFile, RawImage, SensorModel};

mod container;
use container::{AdbSource, LocalSource, ModuleCalibration, ModulePlane, Source};

/// Sensor characterization as the container itself reports it. Camera2 metadata
/// on the device says 64/1023 for the logical camera; the container wins here
/// because it describes this exact plane. Override with --black / --white.
const BLACK: f32 = 42.0;
const WHITE: f32 = 1023.0;

#[derive(Copy, Clone, PartialEq)]
enum Method {
    Native,
    Bayer,
    Bin,
    Red,
    Green,
    Blue,
}

impl Method {
    fn parse(s: &str) -> Option<Self> {
        Some(match s {
            "native" => Method::Native,
            "bayer" => Method::Bayer,
            "bin" => Method::Bin,
            "r" | "red" => Method::Red,
            "g" | "green" => Method::Green,
            "b" | "blue" => Method::Blue,
            _ => return None,
        })
    }

    fn name(self) -> &'static str {
        match self {
            Method::Native => "native",
            Method::Bayer => "bayer",
            Method::Bin => "bin",
            Method::Red => "r",
            Method::Green => "g",
            Method::Blue => "b",
        }
    }
}

struct Args {
    input: PathBuf,
    out: PathBuf,
    methods: Option<Vec<Method>>,
    modules: Option<Vec<String>>,
    black: f32,
    white: f32,
    linear: bool,
    stretch: bool,
    stats: bool,
    census: bool,
    calib: bool,
    hotpixels: bool,
    raw: bool,
    fingerprint: bool,
    peek: Option<String>,
    device: Option<String>,
}

const USAGE: &str = "\
lri-mono <file.lri> [options]

  --out DIR          where the PNGs go (default: alongside the .lri)
  --methods LIST     native,bayer,bin,r,g,b  (default: native for the mono
                     module, bin for the colour ones; 'all' for everything)
  --modules LIST     camera ids, e.g. A2,A1  (default: every module in the file)
  --black N          black level (default 42, from the container)
  --white N          saturation level (default 1023)
  --linear           skip the sRGB curve — linear 16-bit, for stacking
  --stretch          scale to the 99.9th percentile instead of the white level
  --stats            per-module numbers, including the CFA phase test
  --census           one line per module: sensor, mosaic, light collected, colour profile
  --calib            dump the factory calibration: geometry, mirrors, per module
  --hotpixels        read hotpixel.rec: the factory sweep of hot pixels per module
  --raw              also write the untouched sensor values as 16-bit little-endian
  --fingerprint      identify the camera a frame came from, by its factory geometry
  --peek CAM         read only that module's plane instead of the whole file
  --device PATH      peek at a file on the camera over adb, e.g.
                     --peek A2 --device /sdcard/DCIM/Camera/L16_00145.lri
";

fn main() -> ExitCode {
    let args = match parse_args() {
        Ok(Some(a)) => a,
        Ok(None) => {
            print!("{USAGE}");
            return ExitCode::SUCCESS;
        }
        Err(e) => {
            eprintln!("lri-mono: {e}\n\n{USAGE}");
            return ExitCode::FAILURE;
        }
    };

    if args.fingerprint {
        return run_fingerprint(&args);
    }

    if args.hotpixels {
        return run_hotpixels(&args);
    }

    if args.calib {
        return run_calib(&args);
    }

    if let Some(cam) = args.peek.clone() {
        return run_peek(&args, &cam);
    }

    let bytes = match std::fs::read(&args.input) {
        Ok(b) => b,
        Err(e) => {
            eprintln!("lri-mono: cannot read {}: {e}", args.input.display());
            return ExitCode::FAILURE;
        }
    };

    let lri = LriFile::decode(&bytes);
    let stem = args
        .input
        .file_stem()
        .map(|s| s.to_string_lossy().into_owned())
        .unwrap_or_else(|| "lri".into());

    println!(
        "{}: {} modules, focal {} mm, integration {:?}, gain {:?}, tripod {:?}",
        args.input.display(),
        lri.image_count(),
        lri.focal_length.map(|f| f.to_string()).unwrap_or("?".into()),
        lri.image_integration_time,
        lri.image_gain,
        lri.on_tripod,
    );

    // The factory record of what sensor sits behind each of the 16 camera ids.
    // Also present in lightcal/calibration.lri, which carries the map without
    // any image data.
    if args.stats {
        for info in &lri.camera_infos {
            println!("  hw_info: {info:?}");
        }
    }

    if let Err(e) = std::fs::create_dir_all(&args.out) {
        eprintln!("lri-mono: cannot create {}: {e}", args.out.display());
        return ExitCode::FAILURE;
    }

    // Exposure and gain are recorded per module, and lri-rs only surfaces the
    // file-wide values — so the census reads them straight out of the container.
    let index = if args.census {
        container::index(&LocalSource::new(args.input.to_string_lossy().into_owned()))
            .ok()
            .map(|(planes, _)| planes)
    } else {
        None
    };

    let mut written = 0usize;
    for img in lri.images() {
        let cam = img.camera.to_string();
        if let Some(wanted) = &args.modules {
            if !wanted.iter().any(|w| w.eq_ignore_ascii_case(&cam)) {
                continue;
            }
        }

        let Some(raw) = img.unpack() else {
            eprintln!("  {cam}: not a packed 10-bit plane, skipped");
            continue;
        };
        let mono = img.sensor == SensorModel::Ar1335Mono;
        let cfa = img.cfa_string();

        println!(
            "  {cam} {:?} {}x{} cfa {}",
            img.sensor,
            img.width,
            img.height,
            cfa.unwrap_or("none (panchromatic)")
        );

        if args.census {
            let plane = index
                .as_ref()
                .and_then(|planes| planes.iter().find(|p| p.camera.eq_ignore_ascii_case(&cam)));
            print_census(img, &raw, args.black, plane);
        }

        if args.stats {
            print_stats(img, &raw, args.black, args.white);
        }

        for method in methods_for(&args, mono) {
            let plane = match render(img.width, img.height, &raw, method, cfa, args.black, args.white) {
                Ok(p) => p,
                Err(why) => {
                    if args.methods.is_some() {
                        eprintln!("    {}: {why}", method.name());
                    }
                    continue;
                }
            };

            let path = args.out.join(format!("{stem}_{cam}_{}.png", method.name()));
            match write_png(&path, &plane, args.linear, args.stretch) {
                Ok(()) => {
                    println!("    {} -> {}x{}  {}", method.name(), plane.w, plane.h, path.display());
                    written += 1;
                }
                Err(e) => eprintln!("    {}: {e}", method.name()),
            }
        }
    }

    println!("{written} image(s) written");
    ExitCode::SUCCESS
}

/// Pull one module's plane instead of the whole shot.
///
/// A frame is 160–230 MB; a single plane is 16. Over a link that manages 2–3 MB/s that
/// is ten seconds against two minutes — the difference between checking a monochrome
/// frame while still standing at the tripod and checking it back at the desk.
fn run_peek(args: &Args, cam: &str) -> ExitCode {
    let source: Box<dyn Source> = match &args.device {
        Some(path) => Box::new(AdbSource::new(path.clone())),
        None => Box::new(LocalSource::new(args.input.to_string_lossy().into_owned())),
    };

    let (planes, walked) = match container::index(source.as_ref()) {
        Ok(v) => v,
        Err(e) => {
            eprintln!("lri-mono: cannot index {}: {e}", source.name());
            return ExitCode::FAILURE;
        }
    };

    println!("{}: {} modules, {:.0} MB", source.name(), planes.len(), walked as f64 / 1.0e6);
    for p in &planes {
        println!(
            "  {:<3} {}x{}  cfa {:<5}  exposure {:>9.3} ms  gain {:.2}x  {}°  mirror {:>4}  plane {:.1} MB at {}",
            p.camera,
            p.width,
            p.height,
            p.cfa().unwrap_or("none"),
            p.exposure_ns as f64 / 1.0e6,
            p.analog_gain,
            p.temperature,
            p.mirror_position,
            p.length as f64 / 1.0e6,
            p.offset
        );
    }

    let Some(plane) = planes.iter().find(|p| p.camera.eq_ignore_ascii_case(cam)) else {
        eprintln!("lri-mono: no module {cam} in this frame");
        return ExitCode::FAILURE;
    };

    let bytes = match source.read_at(plane.offset, plane.length) {
        Ok(b) => b,
        Err(e) => {
            eprintln!("lri-mono: cannot read the plane: {e}");
            return ExitCode::FAILURE;
        }
    };
    if (bytes.len() as u64) < plane.length {
        eprintln!(
            "lri-mono: plane is short — got {} of {} bytes",
            bytes.len(),
            plane.length
        );
        return ExitCode::FAILURE;
    }

    let raw = container::unpack_tenbit(&bytes, plane.width * plane.height);
    let cfa = plane.cfa();

    // Sensor values as they came off the ASIC — no black level, no scaling, no curve.
    // Stacking and defect work want the numbers, not a picture of them.
    if args.raw {
        if let Err(e) = std::fs::create_dir_all(&args.out) {
            eprintln!("lri-mono: cannot create {}: {e}", args.out.display());
            return ExitCode::FAILURE;
        }
        let stem = args.input.file_stem().unwrap_or_default().to_string_lossy().into_owned();
        let path = args.out.join(format!("{stem}_{}_{}x{}_u16le.raw", plane.camera, plane.width, plane.height));
        let mut bytes = Vec::with_capacity(raw.len() * 2);
        for v in raw.iter() {
            bytes.extend_from_slice(&v.to_le_bytes());
        }
        match std::fs::write(&path, &bytes) {
            Ok(()) => println!("  raw -> {}", path.display()),
            Err(e) => eprintln!("lri-mono: cannot write {}: {e}", path.display()),
        }
    }

    // Worth knowing before walking away from the tripod: a panchromatic module collects
    // about a stop more than its colour neighbours, so metering done on them clips it.
    let mut sum = 0f64;
    let mut clipped = 0usize;
    for &v in raw.iter() {
        sum += (v as f32 - args.black).max(0.0) as f64;
        if v >= 1020 {
            clipped += 1;
        }
    }
    println!(
        "  level {:.1}  clipped {:.2}%",
        sum / raw.len() as f64,
        clipped as f64 / raw.len() as f64 * 100.0
    );
    print_tiled_cfa_test(plane.width, plane.height, &raw, args.black);
    let methods = match &args.methods {
        Some(list) => list.clone(),
        None if cfa.is_none() => vec![Method::Native],
        None => vec![Method::Bin],
    };

    if let Err(e) = std::fs::create_dir_all(&args.out) {
        eprintln!("lri-mono: cannot create {}: {e}", args.out.display());
        return ExitCode::FAILURE;
    }
    let stem = args
        .input
        .file_stem()
        .map(|s| s.to_string_lossy().into_owned())
        .unwrap_or_else(|| "lri".into());

    for method in methods {
        let rendered = match render(
            plane.width,
            plane.height,
            &raw,
            method,
            cfa,
            args.black,
            args.white,
        ) {
            Ok(p) => p,
            Err(why) => {
                eprintln!("  {}: {why}", method.name());
                continue;
            }
        };
        let path = args.out.join(format!("{stem}_{}_{}.png", plane.camera, method.name()));
        match write_png(&path, &rendered, args.linear, args.stretch) {
            Ok(()) => println!(
                "  {} -> {}x{}  {}",
                method.name(),
                rendered.w,
                rendered.h,
                path.display()
            ),
            Err(e) => eprintln!("  {}: {e}", method.name()),
        }
    }

    println!(
        "read {:.1} MB of {:.0} MB",
        plane.length as f64 / 1.0e6,
        walked as f64 / 1.0e6
    );
    ExitCode::SUCCESS
}

/// Identify the camera a frame came from.
///
/// An `.lri` names no serial and no uuid, so a frame separated from its camera looks
/// anonymous. It is not: every frame carries the factory geometry, and that geometry is
/// per-unit — between our two cameras the intrinsics differ by 160 px on average. Hashing
/// them gives a stable identifier, so an archive of orphaned frames can be sorted by the
/// camera that shot them, and matched to the right calibration set.
fn run_fingerprint(args: &Args) -> ExitCode {
    let source: Box<dyn Source> = match &args.device {
        Some(path) => Box::new(AdbSource::new(path.clone())),
        None => Box::new(LocalSource::new(args.input.to_string_lossy().into_owned())),
    };

    let cals = match container::read_calibration(source.as_ref()) {
        Ok(c) => c,
        Err(e) => {
            eprintln!("lri-mono: cannot read {}: {e}", source.name());
            return ExitCode::FAILURE;
        }
    };
    if cals.is_empty() {
        eprintln!("lri-mono: no factory calibration in {}", source.name());
        return ExitCode::FAILURE;
    }

    // One entry per module, in a fixed order, so the same camera always hashes the same.
    let mut rows: Vec<(String, [f32; 4])> = Vec::new();
    for c in &cals {
        let Some(k) = c.k_mat else { continue };
        if rows.iter().any(|(m, _)| *m == c.camera) {
            continue;
        }
        rows.push((c.camera.clone(), [k[0], k[4], k[2], k[5]]));
    }
    rows.sort_by(|a, b| a.0.cmp(&b.0));

    // FNV-1a over the intrinsics: no dependency, and stable across runs and machines.
    let mut hash: u64 = 0xcbf29ce484222325;
    for (m, v) in &rows {
        for byte in m.as_bytes().iter().copied().chain(v.iter().flat_map(|f| f.to_le_bytes())) {
            hash ^= byte as u64;
            hash = hash.wrapping_mul(0x100000001b3);
        }
    }

    println!("{}: camera fingerprint {:016x}  ({} modules)", source.name(), hash, rows.len());
    for (m, v) in &rows {
        println!("  {:<4} fx {:>9.1}  fy {:>9.1}  cx {:>8.1}  cy {:>8.1}", m, v[0], v[1], v[2], v[3]);
    }
    ExitCode::SUCCESS
}

/// Report the factory hot-pixel sweep.
///
/// The camera knows which of its pixels lie, and it knows it as a function of exposure,
/// temperature and gain — the factory measured all three. For long exposures that is the
/// difference between stars and sensor defects, so it is worth having outside the camera.
fn run_hotpixels(args: &Args) -> ExitCode {
    let source: Box<dyn Source> = match &args.device {
        Some(path) => Box::new(AdbSource::new(path.clone())),
        None => Box::new(LocalSource::new(args.input.to_string_lossy().into_owned())),
    };

    let (runs, walked) = match container::read_hot_pixels(source.as_ref()) {
        Ok(r) => r,
        Err(e) => {
            eprintln!("lri-mono: cannot read {}: {e}", source.name());
            return ExitCode::FAILURE;
        }
    };
    if runs.is_empty() {
        eprintln!("lri-mono: no hot pixel measurements in {}", source.name());
        return ExitCode::FAILURE;
    }

    let payload: u64 = runs.iter().map(|r| r.data_size as u64).sum();
    let mut ids: Vec<&str> = runs.iter().map(|r| r.camera.as_str()).collect();
    ids.sort_unstable();
    ids.dedup();
    println!(
        "{}: {} measurements over {} modules, {:.1} MB of {:.1} MB is measurement data",
        source.name(),
        runs.len(),
        ids.len(),
        payload as f64 / 1.0e6,
        walked as f64 / 1.0e6
    );

    println!(
        "\n  {:<4} {:>12} {:>7} {:>7} {:>9} {:>11}",
        "mod", "exposure", "temp", "gain", "thresh", "size"
    );
    for r in &runs {
        println!(
            "  {:<4} {:>9.1} ms {:>6}C {:>7.2} {:>9} {:>8.1} KB",
            r.camera,
            r.exposure_us as f64 / 1000.0,
            r.temperature,
            r.gain,
            r.threshold.map(|t| format!("{t:.1}")).unwrap_or_else(|| "-".into()),
            r.data_size as f64 / 1000.0
        );
    }

    // Inflate them all: the useful number is how many photosites the factory gave up on.
    println!("\n  {:<4} {:>12} {:>12} {:>10} {:>9}", "mod", "map", "flagged", "severe", "dead");
    for r in &runs {
        match container::inflate_run(source.as_ref(), r) {
            Ok((map, w, h)) => {
                let flagged = map.iter().filter(|v| **v > 0).count();
                let severe = map.iter().filter(|v| **v >= 16).count();
                let dead = map.iter().filter(|v| **v == 255).count();
                println!(
                    "  {:<4} {:>5}x{:<6} {:>7} {:>4.1}% {:>7} {:>4.2}% {:>8}",
                    r.camera,
                    w,
                    h,
                    flagged,
                    flagged as f64 / map.len() as f64 * 100.0,
                    severe,
                    severe as f64 / map.len() as f64 * 100.0,
                    dead
                );
            }
            Err(e) => eprintln!("  {}: inflate failed: {e}", r.camera),
        }
    }

    ExitCode::SUCCESS
}

/// Print what the factory measured, module by module.
///
/// Works on `/lightcal/calibration.lri` and on any ordinary shot — every frame carries
/// the same block. Eight of the sixteen modules steer a mirror, and for those the model
/// that says where the module is actually pointing lives here: rotation axis, mirror
/// normal, and the mapping from the Hall reading to an angle.
fn run_calib(args: &Args) -> ExitCode {
    let source: Box<dyn Source> = match &args.device {
        Some(path) => Box::new(AdbSource::new(path.clone())),
        None => Box::new(LocalSource::new(args.input.to_string_lossy().into_owned())),
    };

    let cals = match container::read_calibration(source.as_ref()) {
        Ok(c) => c,
        Err(e) => {
            eprintln!("lri-mono: cannot read {}: {e}", source.name());
            return ExitCode::FAILURE;
        }
    };
    if cals.is_empty() {
        eprintln!("lri-mono: no factory calibration in {}", source.name());
        return ExitCode::FAILURE;
    }

    // The same module is described in several blocks; report each one once, keeping
    // the entry that actually carries the mirror model.
    let mut seen: Vec<&ModuleCalibration> = Vec::new();
    for c in &cals {
        match seen.iter().position(|s| s.camera == c.camera) {
            Some(i) => {
                if seen[i].mirror_angle_range.is_none() && c.mirror_angle_range.is_some() {
                    seen[i] = c;
                }
            }
            None => seen.push(c),
        }
    }

    println!(
        "{}: calibration for {} modules ({} entries in the file)",
        source.name(),
        seen.len(),
        cals.len()
    );
    for c in seen {
        println!(
            "\n  {:<3} mirror {:<8} focus bundles {:<2} colour {} {}{}",
            c.camera,
            c.mirror_type,
            c.focus_bundles,
            c.colour_profiles,
            if c.has_vignetting { "vignetting " } else { "" },
            if c.has_hot_pixels { "hot-pixels" } else { "" }
        );
        if !c.focus_distances.is_empty() {
            let d: Vec<String> = c.focus_distances.iter().map(|f| format!("{f:.2}")).collect();
            println!("      focus distances  {}", d.join(" "));
        }
        if let Some(k) = c.k_mat {
            println!(
                "      intrinsics       fx {:.1}  fy {:.1}  cx {:.1}  cy {:.1}{}",
                k[0], k[4], k[2], k[5],
                if c.has_distortion { "  + distortion" } else { "" }
            );
        }
        if let Some((lo, hi)) = c.mirror_angle_range {
            println!("      mirror angles    {lo:.2}° … {hi:.2}°");
        }
        if let Some((lo, hi)) = c.hall_code_range {
            println!("      hall codes       {lo:.0} … {hi:.0}   pairs {}", c.actuator_pairs);
        }
        if let Some((x, y, z)) = c.rotation_axis {
            println!("      rotation axis    ({x:.4}, {y:.4}, {z:.4})");
        }
        if let Some((x, y, z)) = c.mirror_normal {
            println!("      mirror normal    ({x:.4}, {y:.4}, {z:.4})");
        }
        if !c.quadratic_coeffs.is_empty() {
            let q: Vec<String> = c.quadratic_coeffs.iter().map(|f| format!("{f:.6}")).collect();
            println!("      quadratic model  {}", q.join(" "));
        }
        if let Some(e) = c.reprojection_error {
            println!("      reprojection     {e:.4}");
        }
    }
    ExitCode::SUCCESS
}

fn methods_for(args: &Args, mono: bool) -> Vec<Method> {
    match &args.methods {
        Some(list) => list.clone(),
        // An audit run reports and writes nothing; ask for a method to get images.
        None if args.census || args.stats => Vec::new(),
        None if mono => vec![Method::Native],
        None => vec![Method::Bin],
    }
}

// ------------------------------------------------------------------ rendering

/// A single-channel image in linear 0..1, black already subtracted.
struct Plane {
    w: usize,
    h: usize,
    px: Vec<f32>,
}

fn render(
    w: usize,
    h: usize,
    raw: &[u16],
    method: Method,
    cfa: Option<&str>,
    black: f32,
    white: f32,
) -> Result<Plane, String> {
    let norm = |v: u16| ((v as f32 - black) / (white - black)).clamp(0.0, 1.0);

    let plane = match method {
        Method::Native => {
            if cfa.is_some() {
                return Err("colour sensor — native is for the mono module".into());
            }
            Plane { w, h, px: raw.iter().map(|&v| norm(v)).collect() }
        }

        Method::Bayer => {
            if cfa.is_none() {
                return Err("mono sensor — use native".into());
            }
            Plane { w, h, px: raw.iter().map(|&v| norm(v)).collect() }
        }

        // Works on either sensor: on a CFA plane it averages one Bayer quad,
        // on the mono plane it averages four neighbouring photosites.
        Method::Bin => {
            let (bw, bh) = (w / 2, h / 2);
            let mut px = vec![0.0; bw * bh];
            for y in 0..bh {
                for x in 0..bw {
                    let (sx, sy) = (x * 2, y * 2);
                    let sum = norm(raw[sy * w + sx])
                        + norm(raw[sy * w + sx + 1])
                        + norm(raw[(sy + 1) * w + sx])
                        + norm(raw[(sy + 1) * w + sx + 1]);
                    px[y * bw + x] = sum / 4.0;
                }
            }
            Plane { w: bw, h: bh, px }
        }

        Method::Red | Method::Blue => {
            let cfa = cfa.ok_or("mono sensor has no colour channels")?;
            let want = if method == Method::Red { b'R' } else { b'B' };
            let idx = cfa
                .bytes()
                .position(|c| c == want)
                .ok_or("channel not present in this CFA")?;
            let (ox, oy) = (idx % 2, idx / 2);

            let (cw, ch) = (w / 2, h / 2);
            let mut px = vec![0.0; cw * ch];
            for y in 0..ch {
                for x in 0..cw {
                    px[y * cw + x] = norm(raw[(y * 2 + oy) * w + x * 2 + ox]);
                }
            }
            Plane { w: cw, h: ch, px }
        }

        // Green sits on a quincunx: half the sites are samples, the gaps are
        // filled from the four orthogonal neighbours, all of which are green.
        Method::Green => {
            let cfa = cfa.ok_or("mono sensor has no colour channels")?;
            let is_green: Vec<bool> = cfa.bytes().map(|c| c == b'G').collect();
            if is_green.iter().filter(|g| **g).count() != 2 {
                return Err("unexpected CFA: not two green sites".into());
            }

            let mut px = vec![0.0; w * h];
            for y in 0..h {
                for x in 0..w {
                    if is_green[(y % 2) * 2 + (x % 2)] {
                        px[y * w + x] = norm(raw[y * w + x]);
                        continue;
                    }
                    let mut sum = 0.0;
                    let mut n = 0.0;
                    if x > 0 {
                        sum += norm(raw[y * w + x - 1]);
                        n += 1.0;
                    }
                    if x + 1 < w {
                        sum += norm(raw[y * w + x + 1]);
                        n += 1.0;
                    }
                    if y > 0 {
                        sum += norm(raw[(y - 1) * w + x]);
                        n += 1.0;
                    }
                    if y + 1 < h {
                        sum += norm(raw[(y + 1) * w + x]);
                        n += 1.0;
                    }
                    px[y * w + x] = sum / n;
                }
            }
            Plane { w, h, px }
        }
    };

    Ok(rotate_180(plane))
}

/// The packed plane comes off the sensor upside down; every consumer of these
/// files rotates it. Doing it after the CFA work keeps the phase honest.
fn rotate_180(mut plane: Plane) -> Plane {
    plane.px.reverse();
    plane
}

// --------------------------------------------------------------------- output

fn write_png(path: &Path, plane: &Plane, linear: bool, stretch: bool) -> std::io::Result<()> {
    let ceiling = if stretch { percentile(&plane.px, 0.999).max(1e-6) } else { 1.0 };

    let mut bytes = Vec::with_capacity(plane.px.len() * 2);
    for v in &plane.px {
        let v = (v / ceiling).clamp(0.0, 1.0);
        let v = if linear { v } else { srgb(v) };
        bytes.extend_from_slice(&((v * 65535.0).round() as u16).to_be_bytes());
    }

    let file = BufWriter::new(File::create(path)?);
    let mut enc = png::Encoder::new(file, plane.w as u32, plane.h as u32);
    enc.set_color(png::ColorType::Grayscale);
    enc.set_depth(png::BitDepth::Sixteen);
    let mut writer = enc.write_header()?;
    writer.write_image_data(&bytes)?;
    Ok(())
}

fn srgb(v: f32) -> f32 {
    if v <= 0.0031308 {
        12.92 * v
    } else {
        1.055 * v.powf(1.0 / 2.4) - 0.055
    }
}

fn percentile(px: &[f32], p: f32) -> f32 {
    let mut sorted: Vec<f32> = px.to_vec();
    sorted.sort_by(|a, b| a.partial_cmp(b).unwrap());
    let idx = ((sorted.len() - 1) as f32 * p).round() as usize;
    sorted[idx]
}

// ---------------------------------------------------------------------- stats

/// One line per module: what the sensor is, how its mosaic is oriented, how much
/// light it actually collected on this frame, and what the factory measured for its
/// colour response. Run it over a wide frame and a telephoto one and every camera id
/// is covered — that is the whole array audited from two shots.
fn print_census(img: &RawImage, raw: &[u16], black: f32, plane: Option<&ModulePlane>) {
    let (w, h) = (img.width, img.height);
    let mut sum = 0f64;
    let mut clipped = 0usize;
    for &v in raw.iter() {
        sum += (v as f32 - black).max(0.0) as f64;
        if v >= 1020 {
            clipped += 1;
        }
    }
    let level = sum / (w * h) as f64;

    let colour = img
        .daylight()
        .map(|c| format!("rg {:.3}  bg {:.3}", c.rg, c.bg))
        .unwrap_or_else(|| "no daylight profile".into());
    let whitepoints: Vec<String> = img.color.iter().map(|c| format!("{:?}", c.whitepoint)).collect();

    println!(
        "    census  level {level:7.1}  clipped {:5.2}%  {colour}  profiles [{}]",
        clipped as f64 / (w * h) as f64 * 100.0,
        whitepoints.join(" ")
    );

    // Same scene, same instant, but each module gets its own exposure and gain — so a
    // raw level says nothing about sensitivity until it is divided by what the module
    // was actually given.
    if let Some(p) = plane {
        let product = p.exposure_gain_product();
        let normalised = if product > 0.0 { level / product * 1.0e6 } else { 0.0 };
        println!(
            "            exposure {:.3} ms  gain {:.2}x{}  ->  level per unit exposure {:.2}",
            p.exposure_ns as f64 / 1.0e6,
            p.analog_gain,
            if p.digital_gain > 1.0 { format!(" (digital {:.2}x)", p.digital_gain) } else { String::new() },
            normalised
        );
    }
}

/// Mean of each CFA phase. On a colour sensor the two green phases run well
/// above red and blue under daylight; on a truly panchromatic sensor all four
/// land on the same value, which is what tells the two apart from the data
/// rather than from the metadata.
fn print_stats(img: &RawImage, raw: &[u16], black: f32, white: f32) {
    let (w, h) = (img.width, img.height);
    let mut sums = [0f64; 4];
    let mut counts = [0f64; 4];
    let mut lo = u16::MAX;
    let mut hi = 0u16;
    let mut clipped = 0usize;

    for y in 0..h {
        for x in 0..w {
            let v = raw[y * w + x];
            lo = lo.min(v);
            hi = hi.max(v);
            if v as f32 >= white {
                clipped += 1;
            }
            let phase = (y % 2) * 2 + (x % 2);
            sums[phase] += v as f64;
            counts[phase] += 1.0;
        }
    }

    let means: Vec<f64> = (0..4).map(|i| sums[i] / counts[i]).collect();
    let spread = means.iter().cloned().fold(f64::MIN, f64::max)
        - means.iter().cloned().fold(f64::MAX, f64::min);
    let overall: f64 = sums.iter().sum::<f64>() / counts.iter().sum::<f64>();

    println!(
        "    min {lo} max {hi} mean {overall:.1} (black {black}, clipped {:.3}%)",
        clipped as f64 / (w * h) as f64 * 100.0
    );
    println!(
        "    phase means  (0,0) {:.1}  (1,0) {:.1}  (0,1) {:.1}  (1,1) {:.1}  spread {spread:.1}",
        means[0], means[1], means[2], means[3]
    );

    print_tiled_cfa_test(img.width, img.height, raw, black);
}

/// Whole-frame phase means can be fooled by a neutral scene: average enough of
/// the world together and red, green and blue even out. This runs the same test
/// per 64x64 tile and reports the distribution, so a single coloured patch
/// anywhere in the frame gives a CFA sensor away. Spread is normalised by the
/// tile's own level, so it reads as a fraction of signal rather than counts.
fn print_tiled_cfa_test(w: usize, h: usize, raw: &[u16], black: f32) {
    const TILE: usize = 64;

    let mut spreads: Vec<f32> = Vec::new();
    for ty in 0..h / TILE {
        for tx in 0..w / TILE {
            let mut sums = [0f64; 4];
            let mut counts = [0f64; 4];
            for y in ty * TILE..(ty + 1) * TILE {
                for x in tx * TILE..(tx + 1) * TILE {
                    let v = (raw[y * w + x] as f32 - black).max(0.0);
                    let phase = (y % 2) * 2 + (x % 2);
                    sums[phase] += v as f64;
                    counts[phase] += 1.0;
                }
            }
            let means: Vec<f64> = (0..4).map(|i| sums[i] / counts[i]).collect();
            let level = means.iter().sum::<f64>() / 4.0;
            // Tiles with no signal have nothing to say about the CFA.
            if level < 8.0 {
                continue;
            }
            let hi = means.iter().cloned().fold(f64::MIN, f64::max);
            let lo = means.iter().cloned().fold(f64::MAX, f64::min);
            spreads.push(((hi - lo) / level) as f32);
        }
    }

    if spreads.is_empty() {
        println!("    tiled CFA test: no tile carried enough signal");
        return;
    }

    spreads.sort_by(|a, b| a.partial_cmp(b).unwrap());
    let at = |p: f32| spreads[((spreads.len() - 1) as f32 * p).round() as usize];
    println!(
        "    tiled CFA test ({} tiles): median {:.1}%  p95 {:.1}%  max {:.1}%",
        spreads.len(),
        at(0.5) * 100.0,
        at(0.95) * 100.0,
        at(1.0) * 100.0
    );
}

// ------------------------------------------------------------------- cli glue

fn parse_args() -> Result<Option<Args>, String> {
    let mut input = None;
    let mut out = None;
    let mut methods = None;
    let mut modules = None;
    let mut black = BLACK;
    let mut white = WHITE;
    let mut linear = false;
    let mut stretch = false;
    let mut stats = false;
    let mut census = false;
    let mut calib = false;
    let mut hotpixels = false;
    let mut raw_out = false;
    let mut fingerprint = false;
    let mut peek = None;
    let mut device = None;

    let mut argv = std::env::args().skip(1);
    while let Some(arg) = argv.next() {
        match arg.as_str() {
            "-h" | "--help" => return Ok(None),
            "--out" => out = Some(PathBuf::from(need(&mut argv, "--out")?)),
            "--modules" => {
                modules = Some(
                    need(&mut argv, "--modules")?
                        .split(',')
                        .map(|s| s.trim().to_string())
                        .collect(),
                )
            }
            "--methods" => {
                let raw = need(&mut argv, "--methods")?;
                let list = if raw == "all" {
                    vec![
                        Method::Native,
                        Method::Bayer,
                        Method::Bin,
                        Method::Red,
                        Method::Green,
                        Method::Blue,
                    ]
                } else {
                    raw.split(',')
                        .map(|s| {
                            Method::parse(s.trim())
                                .ok_or_else(|| format!("unknown method '{}'", s.trim()))
                        })
                        .collect::<Result<Vec<_>, _>>()?
                };
                methods = Some(list);
            }
            "--black" => black = need(&mut argv, "--black")?.parse().map_err(|_| "bad --black")?,
            "--white" => white = need(&mut argv, "--white")?.parse().map_err(|_| "bad --white")?,
            "--linear" => linear = true,
            "--stretch" => stretch = true,
            "--stats" => stats = true,
            "--census" => census = true,
            "--calib" => calib = true,
            "--hotpixels" => hotpixels = true,
            "--raw" => raw_out = true,
            "--fingerprint" => fingerprint = true,
            "--peek" => peek = Some(need(&mut argv, "--peek")?),
            "--device" => device = Some(need(&mut argv, "--device")?),
            other if other.starts_with('-') => return Err(format!("unknown option '{other}'")),
            other => input = Some(PathBuf::from(other)),
        }
    }

    let input = match (input, &device) {
        (Some(p), _) => p,
        // With --device the file lives on the camera; the local path is only a stem.
        (None, Some(dev)) => PathBuf::from(dev.rsplit('/').next().unwrap_or("lri")),
        (None, None) => return Err("no .lri file given".into()),
    };
    if white <= black {
        return Err("--white must be above --black".into());
    }
    let out = out.unwrap_or_else(|| {
        input.parent().map(Path::to_path_buf).unwrap_or_else(|| PathBuf::from("."))
    });

    Ok(Some(Args {
        input, out, methods, modules, black, white, linear, stretch, stats, census, calib, hotpixels, raw: raw_out, fingerprint, peek, device,
    }))
}

fn need(argv: &mut impl Iterator<Item = String>, what: &str) -> Result<String, String> {
    argv.next().ok_or_else(|| format!("{what} needs a value"))
}
