//! Walking an `.lri` without reading all of it.
//!
//! A shot is 160–230 MB because it carries the plane of every module that took part.
//! One plane is 16 MB, and over USB that is the difference between ten seconds and two
//! minutes — enough to decide whether pulling a monochrome frame is something you do in
//! the field or something you do afterwards at the desk.
//!
//! The file is a chain of blocks. Each starts with a 32-byte header giving the block's
//! length and where its protobuf message sits; the message names the modules in that
//! block and, for each, the offset of its sensor plane from the block start. So the
//! index costs a few small reads — one header and one message per block — and only the
//! plane you asked for is fetched in full.

use std::io::{self, Read, Seek, SeekFrom};
use std::process::Command;

use lri_proto::{lightheader::LightHeader, Message as PbMessage};

const MAGIC: &[u8; 4] = b"LELR";
const HEADER_LEN: u64 = 32;
const MSG_LIGHT_HEADER: u8 = 0;

/// Somewhere bytes can be read from at an offset: a local file, or a file on the camera.
pub trait Source {
    fn read_at(&self, offset: u64, len: u64) -> io::Result<Vec<u8>>;
    /// Human-readable name, for messages.
    fn name(&self) -> &str;
}

pub struct LocalSource {
    path: String,
}

impl LocalSource {
    pub fn new(path: impl Into<String>) -> Self {
        Self { path: path.into() }
    }
}

impl Source for LocalSource {
    fn read_at(&self, offset: u64, len: u64) -> io::Result<Vec<u8>> {
        let mut file = std::fs::File::open(&self.path)?;
        file.seek(SeekFrom::Start(offset))?;
        let mut buf = vec![0u8; len as usize];
        let mut got = 0usize;
        while got < buf.len() {
            match file.read(&mut buf[got..])? {
                0 => break,
                n => got += n,
            }
        }
        buf.truncate(got);
        Ok(buf)
    }

    fn name(&self) -> &str {
        &self.path
    }
}

/// A file sitting on the camera, read over adb in ranges.
///
/// Toybox `head` has no `-c`, so a byte range cannot be cut with tail and head. `dd`
/// works but counts in blocks, so the read is widened to block boundaries and trimmed
/// on this side — a few kilobytes of slack against a 16 MB plane.
pub struct AdbSource {
    path: String,
}

impl AdbSource {
    pub fn new(path: impl Into<String>) -> Self {
        Self { path: path.into() }
    }
}

impl Source for AdbSource {
    fn read_at(&self, offset: u64, len: u64) -> io::Result<Vec<u8>> {
        const BLOCK: u64 = 65536;

        let first = offset / BLOCK;
        let slack = offset - first * BLOCK;
        let blocks = (slack + len + BLOCK - 1) / BLOCK;

        let script = format!(
            "dd if='{}' bs={} skip={} count={} 2>/dev/null",
            self.path, BLOCK, first, blocks
        );
        let out = Command::new("adb").arg("exec-out").arg(&script).output()?;
        if !out.status.success() {
            return Err(io::Error::new(
                io::ErrorKind::Other,
                format!("adb read failed: {}", String::from_utf8_lossy(&out.stderr).trim()),
            ));
        }

        let mut bytes = out.stdout;
        if (bytes.len() as u64) <= slack {
            return Ok(Vec::new());
        }
        bytes.drain(..slack as usize);
        bytes.truncate(len as usize);
        Ok(bytes)
    }

    fn name(&self) -> &str {
        &self.path
    }
}

/// What one module contributed to the shot, and where its plane lives in the file.
pub struct ModulePlane {
    pub camera: String,
    pub width: usize,
    pub height: usize,
    pub row_stride: u64,
    /// Absolute position of the plane in the file.
    pub offset: u64,
    pub length: u64,
    /// Where red sits in the 2x2 mosaic; (-1, -1) means there is no mosaic at all.
    pub sbro: (i32, i32),
    /// Where the steerable mirror stood for this frame, as a Hall code. Meaningless for
    /// the modules whose mirror is glued or absent — see `--calib` for which is which.
    pub mirror_position: i32,
    /// Sensor temperature in degrees, as the module reported it for this frame.
    pub temperature: i32,
    /// Exposure in nanoseconds and the gains, as recorded for this module alone.
    pub exposure_ns: u64,
    pub analog_gain: f32,
    pub digital_gain: f32,
}

impl ModulePlane {
    /// The mosaic as a string, or None for a panchromatic module. Same mapping lri-rs
    /// uses: the override says where red lands in the 2x2 of an otherwise BGGR sensor.
    pub fn cfa(&self) -> Option<&'static str> {
        match self.sbro {
            (-1, -1) => None,
            (0, 0) => Some("BGGR"),
            (1, 0) => Some("GRBG"),
            (0, 1) => Some("GBRG"),
            (1, 1) => Some("RGGB"),
            _ => None,
        }
    }

    /// Total light-gathering the module was given, relative to the others in the frame.
    /// Level divided by this is what comparing sensitivity actually needs.
    pub fn exposure_gain_product(&self) -> f64 {
        self.exposure_ns as f64 * (self.analog_gain * self.digital_gain.max(1.0)) as f64
    }
}

/// Read every block header and its message, and collect the module planes they describe.
pub fn index(src: &dyn Source) -> io::Result<(Vec<ModulePlane>, u64)> {
    let mut planes = Vec::new();
    let mut pos = 0u64;

    loop {
        let header = src.read_at(pos, HEADER_LEN)?;
        if header.len() < HEADER_LEN as usize || &header[..4] != MAGIC {
            break;
        }

        let block_length = u64::from_le_bytes(header[4..12].try_into().unwrap());
        let message_offset = u64::from_le_bytes(header[12..20].try_into().unwrap());
        let message_length = u32::from_le_bytes(header[20..24].try_into().unwrap()) as u64;
        let kind = header[24];

        if block_length == 0 {
            break;
        }

        if kind == MSG_LIGHT_HEADER && message_length > 0 {
            let msg = src.read_at(pos + message_offset, message_length)?;
            if let Ok(light) = LightHeader::parse_from_bytes(&msg) {
                for module in light.modules {
                    let surface = match module.sensor_data_surface.as_ref() {
                        Some(s) => s,
                        None => continue,
                    };
                    let size = match surface.size.as_ref() {
                        Some(p) => p,
                        None => continue,
                    };
                    let (w, h) = (size.x() as usize, size.y() as usize);
                    let row_stride = surface.row_stride() as u64;
                    // Bayer JPEG surfaces report no stride; only packed planes are
                    // addressable by a plain range, which is all this path serves.
                    if row_stride == 0 || h == 0 {
                        continue;
                    }
                    let sbro = module
                        .sensor_bayer_red_override
                        .as_ref()
                        .map(|p| (p.x(), p.y()))
                        .unwrap_or((-1, -1));
                    planes.push(ModulePlane {
                        camera: format!("{:?}", module.id()),
                        sbro,
                        width: w,
                        height: h,
                        row_stride,
                        offset: pos + surface.data_offset(),
                        length: row_stride * h as u64,
                        mirror_position: module.mirror_position(),
                        temperature: module.sensor_temparature(),
                        exposure_ns: module.sensor_exposure(),
                        analog_gain: module.sensor_analog_gain(),
                        digital_gain: module.sensor_digital_gain(),
                    });
                }
            }
        }

        pos += block_length;
    }

    Ok((planes, pos))
}

/// Unpack a 10-bit packed plane. Mirrors what lri-rs does, including reading the buffer
/// back to front — the packing runs the other way round from what you would guess.
pub fn unpack_tenbit(packed: &[u8], count: usize) -> Vec<u16> {
    const MASK: u64 = 1023;

    let required = (count as f32 * 10.0 / 8.0).ceil() as usize;
    let mut out = vec![0u16; count];
    if packed.len() < required {
        return out;
    }

    let mut buf = packed[..required].to_vec();
    buf.reverse();

    let chunks = buf.chunks_exact(5);
    let remainder = chunks.remainder().to_vec();

    for (idx, chunk) in chunks.enumerate() {
        let long = u64::from_be_bytes([0, 0, 0, chunk[0], chunk[1], chunk[2], chunk[3], chunk[4]]);
        let idx = idx * 4;
        out[idx] = ((long >> 30) & MASK) as u16;
        out[idx + 1] = ((long >> 20) & MASK) as u16;
        out[idx + 2] = ((long >> 10) & MASK) as u16;
        out[idx + 3] = (long & MASK) as u16;
    }

    if !remainder.is_empty() {
        let mut bytes = [0u8; 8];
        bytes[..remainder.len()].copy_from_slice(&remainder);
        let long = u64::from_le_bytes(bytes);
        let count_remain = count % 4;
        let start = count - count_remain;
        for idx in 0..count_remain {
            out[start + idx] = ((long >> (10 * idx)) & MASK) as u16;
        }
    }

    out
}

/// What the factory measured for one module, as far as the geometry goes.
pub struct ModuleCalibration {
    pub camera: String,
    pub mirror_type: String,
    /// Focus bundles: each carries intrinsics for one focus distance.
    pub focus_bundles: usize,
    pub focus_distances: Vec<f32>,
    /// The K matrix of the first bundle, row-major.
    pub k_mat: Option<[f32; 9]>,
    pub has_distortion: bool,
    pub colour_profiles: usize,
    pub has_vignetting: bool,
    pub has_hot_pixels: bool,
    /// Mirror geometry, present only for the modules that steer one.
    pub mirror_angle_range: Option<(f32, f32)>,
    pub hall_code_range: Option<(f32, f32)>,
    pub actuator_pairs: usize,
    pub quadratic_coeffs: Vec<f32>,
    pub mirror_normal: Option<(f32, f32, f32)>,
    pub rotation_axis: Option<(f32, f32, f32)>,
    pub reprojection_error: Option<f32>,
}

/// Read the factory calibration out of a container — the `.lri` in `/lightcal`, or any
/// shot, since every frame carries the same block. This is the geometry an open fusion
/// needs: intrinsics per focus distance, distortion, and for eight of the modules the
/// mirror model that says where the module is actually looking.
pub fn read_calibration(src: &dyn Source) -> io::Result<Vec<ModuleCalibration>> {
    let mut out = Vec::new();
    let mut pos = 0u64;

    loop {
        let header = src.read_at(pos, HEADER_LEN)?;
        if header.len() < HEADER_LEN as usize || &header[..4] != MAGIC {
            break;
        }
        let block_length = u64::from_le_bytes(header[4..12].try_into().unwrap());
        let message_offset = u64::from_le_bytes(header[12..20].try_into().unwrap());
        let message_length = u32::from_le_bytes(header[20..24].try_into().unwrap()) as u64;
        let kind = header[24];
        if block_length == 0 {
            break;
        }

        if kind == MSG_LIGHT_HEADER && message_length > 0 {
            let msg = src.read_at(pos + message_offset, message_length)?;
            if let Ok(light) = LightHeader::parse_from_bytes(&msg) {
                for cal in light.module_calibration {
                    let camera = format!("{:?}", cal.camera_id());
                    let geometry = cal.geometry.as_ref();

                    let mirror_type = geometry
                        .map(|g| format!("{:?}", g.mirror_type()))
                        .unwrap_or_else(|| "—".into());

                    let bundles = geometry.map(|g| g.per_focus_calibration.len()).unwrap_or(0);
                    let focus_distances = geometry
                        .map(|g| {
                            g.per_focus_calibration
                                .iter()
                                .map(|b| b.focus_distance())
                                .collect()
                        })
                        .unwrap_or_default();

                    let k_mat = geometry.and_then(|g| {
                        g.per_focus_calibration.first().and_then(|b| {
                            b.intrinsics.as_ref().and_then(|i| {
                                i.k_mat.as_ref().map(|m| {
                                    [m.x00(), m.x01(), m.x02(),
                                     m.x10(), m.x11(), m.x12(),
                                     m.x20(), m.x21(), m.x22()]
                                })
                            })
                        })
                    });

                    // The mirror model hides two levels down, inside the extrinsics —
                    // and not necessarily in the first focus bundle, so look through all
                    // of them and take the first that carries one.
                    let movable = geometry.and_then(|g| {
                        g.per_focus_calibration
                            .iter()
                            .filter_map(|b| b.extrinsics.as_ref())
                            .filter_map(|e| e.moveable_mirror.as_ref())
                            .next()
                    });
                    let system = movable.and_then(|m| m.mirror_system.as_ref());
                    let mapping = movable.and_then(|m| m.mirror_actuator_mapping.as_ref());

                    out.push(ModuleCalibration {
                        camera,
                        mirror_type,
                        focus_bundles: bundles,
                        focus_distances,
                        k_mat,
                        has_distortion: geometry.map(|g| g.distortion.is_some()).unwrap_or(false),
                        colour_profiles: cal.color.len(),
                        has_vignetting: cal.vignetting.is_some(),
                        has_hot_pixels: cal.hot_pixel_map.is_some(),
                        mirror_angle_range: system
                            .and_then(|s| s.mirror_angle_range.as_ref())
                            .map(|r| (r.min_val(), r.max_val())),
                        hall_code_range: mapping
                            .and_then(|m| m.hall_code_range.as_ref())
                            .map(|r| (r.min_val(), r.max_val())),
                        actuator_pairs: mapping.map(|m| m.actuator_angle_pair_vec.len()).unwrap_or(0),
                        quadratic_coeffs: mapping
                            .and_then(|m| m.quadratic_model.as_ref())
                            .map(|q| q.model_coeffs.clone())
                            .unwrap_or_default(),
                        mirror_normal: system
                            .and_then(|s| s.mirror_normal_at_zero_degrees.as_ref())
                            .map(|p| (p.x(), p.y(), p.z())),
                        rotation_axis: system
                            .and_then(|s| s.rotation_axis.as_ref())
                            .map(|p| (p.x(), p.y(), p.z())),
                        reprojection_error: system.and_then(|s| s.reprojection_error),
                    });
                }
            }
        }
        pos += block_length;
    }

    Ok(out)
}

/// One hot-pixel characterisation: the factory swept exposure, temperature and gain,
/// and recorded where the sensor lied at each setting.
pub struct HotPixelRun {
    pub camera: String,
    pub exposure_us: u32,
    pub temperature: i32,
    pub gain: f32,
    pub variance: Option<f32>,
    pub threshold: Option<f32>,
    pub data_offset: u64,
    pub data_size: u32,
}

/// Read the hot-pixel measurements out of `hotpixel.rec`.
///
/// The file is an ordinary LELR block, but an unusual one: the payload comes first and
/// the protobuf message sits at the very end, which is why the header points 29 MB in.
/// Each measurement names a range of that payload; the ranges are zlib streams.
pub fn read_hot_pixels(src: &dyn Source) -> io::Result<(Vec<HotPixelRun>, u64)> {
    let mut runs = Vec::new();
    let mut pos = 0u64;

    loop {
        let header = src.read_at(pos, HEADER_LEN)?;
        if header.len() < HEADER_LEN as usize || &header[..4] != MAGIC {
            break;
        }
        let block_length = u64::from_le_bytes(header[4..12].try_into().unwrap());
        let message_offset = u64::from_le_bytes(header[12..20].try_into().unwrap());
        let message_length = u32::from_le_bytes(header[20..24].try_into().unwrap()) as u64;
        if block_length == 0 {
            break;
        }

        if header[24] == MSG_LIGHT_HEADER && message_length > 0 {
            let msg = src.read_at(pos + message_offset, message_length)?;
            if let Ok(light) = LightHeader::parse_from_bytes(&msg) {
                for cal in light.module_calibration {
                    let camera = format!("{:?}", cal.camera_id());
                    if let Some(map) = cal.hot_pixel_map.as_ref() {
                        for m in &map.data {
                            runs.push(HotPixelRun {
                                camera: camera.clone(),
                                exposure_us: m.data_exposure(),
                                temperature: m.sensor_temparature(),
                                gain: m.sensor_gain(),
                                variance: m.pixel_variance,
                                threshold: m.threshold,
                                data_offset: pos + m.data_offset(),
                                data_size: m.data_size(),
                            });
                        }
                    }
                }
            }
        }
        pos += block_length;
    }

    Ok((runs, pos))
}

/// Inflate one measurement into a full-resolution defect map.
///
/// Each measurement begins with 20 bytes of its own: a checksum, the compressed length,
/// then the width and height. What follows is a zlib stream that expands to one byte per
/// pixel — a severity per photosite, not a yes/no mask.
pub fn inflate_run(src: &dyn Source, run: &HotPixelRun) -> io::Result<(Vec<u8>, u32, u32)> {
    use flate2::read::ZlibDecoder;
    const REC_HEADER: usize = 20;

    let raw = src.read_at(run.data_offset, run.data_size as u64)?;
    if raw.len() < REC_HEADER {
        return Err(io::Error::new(io::ErrorKind::UnexpectedEof, "hot pixel record is short"));
    }
    let width = u32::from_le_bytes(raw[12..16].try_into().unwrap());
    let height = u32::from_le_bytes(raw[16..20].try_into().unwrap());

    let mut out = Vec::new();
    ZlibDecoder::new(&raw[REC_HEADER..]).read_to_end(&mut out)?;
    Ok((out, width, height))
}
