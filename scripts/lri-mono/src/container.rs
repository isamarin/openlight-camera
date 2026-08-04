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
