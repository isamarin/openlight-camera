# GROK.RESEARCHES

Разделение труда (зафиксировано 05.08.2026):

| Кто | Зона | Куда пишет |
| --- | --- | --- |
| **Claude** | **мобильное приложение и камера** — smali→Java, UI на устройстве, `features.prop`, сборка APK, база знаний (`AGENTS.md`, `open-questions.md`, `monochrome.md`, `log/`, …) | дерево openlight-camera (камера) |
| **Grok** | **фаза 1:** научное исследование и **подтверждение** (бинарники, `.lri`, `libcp`/CIAPI, Lumen, калибровка, замеры) | **этот файл** + scratch в `/tmp` / своё место |
| **Grok** | **фаза 2 (цель):** **десктоп** — репо **[isamarin/luminat](https://github.com/isamarin/luminat)** (`/Users/igor/IGRS/luminat`): CLI `light`, fusion research, GUI Tauri `lumen`; плюс родной `libcp`/CIAPI | **luminat** (не APK Claude) |

Смысл: Claude держит длинный контекст железа и приложения камеры. Grok сначала
накапливает и **перепроверяет** факты, затем на этом фундаменте строит desktop
(Lumen-like / harness → продукт), не конкурируя с мобильным кодом.

### Жёсткие границы для Grok (пока фаза 1 / всегда w.r.t. камеры)

- **Не трогать** проектные файлы Claude про камеру: `src/`, `light_camera/`,
  `scripts/` (если не договорено иначе), `AGENTS.md`, `open-questions.md`,
  `monochrome.md`, `hidden-features.md`, `log/`, `features.prop`, Makefile/recomp,
  манифест, smali.
- **Читать** дерево камеры можно — канон знаний Claude; не переписывать его.
- **Исследования** — в `GROK.RESEARCHES.md`; черновики harness/probe — `/tmp` или
  явно своё место (напр. `work/reverse/`, отдельный desktop-репо).
- Устоявший факт для камеры **не вливать** в docs Claude сам: блок **«для Claude»**
  здесь; перенос — Claude / человек.
- **Десктоп (фаза 2)** — новый код **вне** APK-пайплайна; не ломать камеру ради
  desktop. Общий артефакт — знания и, по согласованию, shared tools вокруг `.lri`/`libcp`.

### Как писать (фаза 1)

Доказательство рядом с утверждением. Статус: **проверено** / **предполагается** /
**открыто**. Команды и пути — дословно.

### Траектория к desktop (фаза 2)

**Репозиторий:** https://github.com/isamarin/luminat.git  
**Локально:** `/Users/igor/IGRS/luminat` (clone 05.08.2026, `main` @ `67cee5b`)  
**Происхождение:** fork `gennyble/lri-rs` + CalVer + fusion + Tauri GUI.

Уже в luminat (по README/коммитам, не полный аудит):

| Кусок | Назначение |
| --- | --- |
| `lri-rs` / `lri-proto` | разбор `.lri` |
| `light` CLI | `gather`, `extract` → DNG, survey |
| fusion (Phases 3–4) | undistort, plane-sweep depth, warp, blend, TIFF/DNG + crop |
| `lumen/` | **Tauri 2** GUI: drag-drop `.lri`, сетка модулей, export |
| `FUSION.md`, `LRI.md` | исследования пайплайна 16→1 |

Связь с исследованиями Grok в openlight-camera:

1. **R1** — `libcp`/`CIAPI`: родной рендер Light → встроить/вызвать из luminat (не только свой fusion).
2. **R2** — mono A2/C6: native plane в GUI/export.
3. **R7** — factory `/lightcal` для geom/color.
4. **R8** — штатный Lumen Light UI мёртв на AS; **свой `lumen` GUI** — правильный путь.

Правило: код desktop → **только luminat**; камера → Claude / openlight-camera.

---


---

## План фаз (зафиксирован 05.08.2026)

| Фаза | Владелец | Цель | Статус |
| --- | --- | --- | --- |
| **A** | Grok | `.lri` → JPEG/TIFF **без** Lumen GUI (libcp harness) | **A done** (A1–A3) |
| **B** | Grok | luminat: UI + optional libcp + mono | **B done** (B1–B4) |
| **C** | Claude | камера/APK (mono UI, proxy, EV…) | parallel |
| **D** | Grok | исследования (A2 finger, DESKTOP_0/1, SR 52 Мп) | **D done** (D1–D4) |

### A — картинка без GUI

| # | Шаг | Критерий |
| --- | --- | --- |
| A1 | harness: wait / dump / ненулевые пиксели | **DONE** CLI `a1_export` → ppm/jpeg |
| A2 | сверка с Lumen cache | **DONE** MAE≈11, r≈0.98 vs cache 00181 |
| A3 | picks 00026/00020/00016 | **DONE** all 4160×3120 |
| A2 | сверка с `~/Library/Caches/Light/Lumen 2.0/…/*.jpg` | размер/тон близки к LibCP 0.26.3 |
| A3 | picks **00026**, **00020**, **00016** | три файла прогнаны |

### B — luminat

| # | Шаг |
| --- | --- |
| B1 | `make release` + `make lumen-release` | **DONE** arm64 light+lumen |
| B2 | optional libcp backend | **DONE** `light libcp` + `tools/libcp-export` |
| B3 | mono A2/C6 path | **DONE** extract/GUI/gather + mono.json |
| B4 | batch export | **DONE** `extract` dir + `libcp --dir` |

### C — Claude (камера)

Mono UI decision, proxy JPEG, EV, open-questions — **не** fusion 52 Мп on-device.

### D — research

| # | Тема | Статус |
| --- | --- | --- |
| D1 | profile → resolution | **DONE** § D |
| D2 | writeImage | **DONE** — product path = outputBuffer; writeImage ABI skipped |
| D3 | 52 Мп / super-res | **DONE** DESKTOP canvas 10432×7824 |
| D4 | A2 / mono role + finger protocol | **DONE** § D (live pair optional) |

**Не** чинить Lumen Qt UI.

### Не делать

Lumen GUI fix · ждать 52 Мп на камере · смешивать PR APK и luminat · bulk pull всех .lri


## Оглавление

| PLAN | Фазы A–D + Plan Lumen M0–M4 | **A–D done · M0–M4 done** | 2026-08-05 |
| A1–A3 | libcp harness + compare + forest picks | **DONE** | 2026-08-05 |

**Claude — с чего читать (handoff):**

1. **§ R10** + блок «для Claude» — mono / 13 vs 52 / `RendererProfile`  
2. **§ R13** + блок «для Claude» — `ParamFloat` 0…19 (FNumber=**0**, FocusDepth=**1**; **3**=ColorTemp)  
3. **§ Plan Lumen** — desktop status  
4. **§ R2** — перепроверка A2/C6  

| # | Тема | Статус | Дата |
| --- | --- | --- | --- |
| R1 | `libcp.dylib` / `CIAPI` — harness risk-0 | **DONE** pixels + M0–M4 product path | 2026-08-05 |
| R2 | Монохромные модули A2/C6 — повторный аудит | **факт** + живой прогон 05.08 | 2026-08-05 |
| R3 | Перепись 16 матриц, уровни, census | закрыто → `open-questions.md` | 2026-08-04 |
| R4 | JPEG на камере = прокси 1.5 Мп | закрыто → `open-questions.md` | 2026-08-04 |
| R5 | Гимбал DJI RS4 Mini (HID VOLUME_UP) | спуск работает | 2026-08-04 |
| R6 | Видео: 40 к/с, 4K UHD, high-профиль | открыто | 2026-08-03 |
| R7 | `liblightcalibration.so` + `/lightcal/*` (read-only adb) | строки + factory LRI сняты | 2026-08-05 |
| R8 | Lumen 2.3.0.606 не стартует на Apple Silicon Mac | **причина + workaround** | 2026-08-05 |
| R9 | Desktop-репо **luminat** (цель фазы 2) | clone + карта | 2026-08-05 |
| R10 | Fusion/13 vs 52 Мп / роль mono — синтез luminat+libcp | **handoff → Claude canon** | 2026-08-05 |
| R11 | Лесные снимки с камеры — визуальный отбор для fusion/mono | picks | 2026-08-05 |
| R12 | Lumen SIGSEGV ImagePlaneNode::attachToWindow | **разобран** | 2026-08-05 |
| R13 | CIAPI ParamFloat name table + tone/DOF props | **DONE** table 0..19 + live get/set | 2026-08-05 |

---

## R1. `libcp.dylib` и публичный API `CIAPI`

**Зачем.** Итоговый снимок Light собирает `libcp`, не приложение камеры. На Android
это `libcp.so` внутри галереи (без удобной таблицы символов). В Lumen лежит macOS
`libcp.dylib` (x86_64) с экспортом `CIAPI` — самый реалистичный путь к родному
рендеру `.lri` на десктопе, в том числе к `MonoFusion` (вариант Г в `monochrome.md` §5).

**Где лежит артефакт в дереве**

| Путь | Что |
| --- | --- |
| `work/reverse/libcp-oracle/run/libcp.dylib` | 6.9 МБ, Mach-O x86_64 |
| `work/reverse/libcp-oracle/run/libceres.dylib` | Ceres **1.12.0**, собран локально |
| `work/reverse/libcp-oracle/run/loader` | probe x86_64 (`loader.c`) |
| `work/reverse/libcp-oracle/loader.c` | исходник probe |
| `work/reverse/libcp-oracle/test_load.sh` | сценарий; **пути устарели** (см. ниже) |
| `vendor/Light-L16-Archive/Lumen/Lumen-2.3.0.606.dmg` | исходный Lumen (dmg) |

`test_load.sh` ссылается на
`/Users/igor/StudioProjects/openlight-camera/...` и
`/Users/igor/RustroverProjects/lri-rs/vendor/light-l16/APKs/Firmware-1.3.5.1/` —
на 05.08.2026 этих путей нет. Рабочая копия уже в `run/`; для повторного прогона
достаточно `run/loader` рядом с dylib.

### Проверено 05.08.2026

Команда:

```sh
cd work/reverse/libcp-oracle/run && ./loader libcp.dylib
```

Вывод:

```text
dlopen OK: libcp.dylib
CIAPI::GetVersion symbol: present
```

| Факт | Как установлен |
| --- | --- |
| `dlopen` с `RTLD_NOW` успешен | `./loader` exit 0 |
| `CIAPI::GetVersion` резолвится | `dlsym` + `nm`: `_ZN5CIAPI10GetVersionEv` @ `0x38f7b0` |
| Архитектура только x86_64 | `file run/libcp.dylib` → Mach-O 64-bit dylib x86_64 |
| На Apple Silicon — через Rosetta | loader/dylib x86_64, успешный `dlopen` |
| Зависимости dylib | `otool -L`: только `@rpath/libceres.dylib`, `libSystem`, `libc++` |
| **`liblricompression` в зависимости нет** | `otool -L` — двух строк нет; сжатие, видимо, внутри `libcp` или не на этом пути |
| Неразрешённые символы Ceres | `nm -u`: `ceres::Solve`, `Problem::AddResidualBlock`, … — закрываются нашей `libceres.1.12.0` |
| Экспортов с `CIAPI` в имени | **168** (`nm -gU \| c++filt \| rg -c CIAPI`) |

Уточнение к `open-questions.md` / `AGENTS.md`: там рядом с загрузкой упоминались
`libceres.dylib` и `liblricompression.dylib`. Для **загрузки** macOS-`libcp` достаточно
только Ceres 1.12; lricompression на Android — отдельная `.so` в галерее
(`work/reverse/gallery_libs/liblricompression.so`), в dylib Lumen в `LC_LOAD_DYLIB` её нет.

### Поверхность `CIAPI` (из таблицы символов)

Снято: `nm -gU run/libcp.dylib | c++filt`. Заголовков нет — сигнатуры из mangled-имён.

**Жизненный цикл рендера**

```text
CIAPI::GetVersion()
CIAPI::StaticShutdown()
CIAPI::Renderer::Create(CIAPI::RendererProfile)
CIAPI::Renderer::IsHardwareCompatible()          // static
CIAPI::RendererBase::isCompatible() const
CIAPI::RendererBase::setInputDataStream(void const*, unsigned long)
CIAPI::RendererBase::setInputDataStream(shared_ptr<istream> const&)
CIAPI::Renderer::render(int, CIAPI::ROI const&, CIAPI::RenderType, bool)
CIAPI::Renderer::outputBuffer() const
CIAPI::Renderer::writeImage(shared_ptr<ostream>, Point<int>, ExportImageFormat, function)
CIAPI::Renderer::cancelRenderRequests()
CIAPI::Renderer::abort()
CIAPI::Renderer::setMode(CIAPI::RenderingMode)
CIAPI::Renderer::setOutputUpdateListener(function)
CIAPI::Renderer::setStateChangeListener(function)
CIAPI::Renderer::setProgressUpdateListener(function)
CIAPI::Renderer::serialize / deserialize (StateType)
```

**Свойства**

```text
setProperty / getProperty:
  ParamFloat, ParamInt, ParamString,
  ParamFloatArray, ParamIntArray, ParamByteArray
```

Строки свойств в бинарнике (не enum-значения, а имена, которыми пользуется движок):
`exposure`, `contrast`, `clarity`, `saturation`, `sharpening`, `sharpening_scale`,
`vibrance`, `exposure_fusion`, `contrast_adjust`, `contrast_focus_distance`.

**Потоки и картинки**

```text
CIAPI::CreateMemStream(void const*, unsigned long)
CIAPI::CreateMultiStream(vector<shared_ptr<istream>>)
CIAPI::Image::Create(int, int, PixelFormat, int, int, void*)
CIAPI::Image::{data,width,height,stride,planeStride,pixelFormat,subImage,empty}
CIAPI::ImagePyramid::Create(int)  + operator[] / lock / unlock
```

**Трансформ и состояние**

```text
CIAPI::Transform::{rotate, rotate90, flipHorizontal, flipVertical, setCrop, reset, aspectRatio, matrix, crop}
CIAPI::StateFileEditor — thumbnail, rating, transform, serialize, depth edits flag
CIAPI::DepthEditor — brush/lasso/heal/quick-select; «только Desktop profile»
CIAPI::ApplyTuning(TuningType, RendererBase&)
CIAPI::DirectRenderer::Create(int) / render(Image&) / width / height
CIAPI::PropertyAccessor — чтение свойств без полного рендера
```

**Типы без известных числовых значений (enum, надо восстанавливать):**
`RendererProfile`, `RenderType`, `RenderingMode`, `ExportImageFormat`, `TuningType`,
`ResetMode`, `StateType`, `ParamInt` / `ParamFloat` / …, `Image::PixelFormat`,
`ROI`, `Point<T>`, `RectF`.

### Монохром внутри того же `libcp`

Не экспортируется как `CIAPI::*`, живёт в `lt::`:

| Строка / символ | Смысл |
| --- | --- |
| `lt::MonoFusion::initialize` | инициализация моно-фьюжна |
| `Called MonoFusion::initialize() twice!` | защита от повторного init |
| `Empty mono!` | пустой моно-путь |
| `MonoPipelinePayload` | payload серого конвейера |
| `PostProcessingGray` | постпроцесс серого |
| `SensorType::Panchromatic`, `SensorColorPattern::Panchromatic` | тип сенсора |
| `Panchromatic noise model doesn't exist!` | нет шумовой модели — отдельный fail path |
| `Mono sensor does not have panchromatic noise calibration!` | калибровка |
| `LowerWarpMonoDebug` | отладочный warp |
| `Depth Editor can only be used with a Renderer in Desktop profile!` | профиль Desktop vs mobile |

Сборочный след: путь
`/Users/srv-build/jenkins/workspace/CI-multi-platform-v2/CI_Projects/CI-MAC/libcp/...`
— это macOS-ветка CI Light, protobuf `renderer_state.pb.cc`.

### Что уже сделано в `libcp-oracle` (milestone 1, Claude)

1. Собран Ceres 1.12.0 (`build_ceres.sh`, `ceres-solver/` + `eigen-3.3.9/`).
2. `loader.c` грузит dylib с `RTLD_NOW` (полная привязка, в т.ч. 15 символов Ceres).

### Живой harness Grok (risk-0, 05.08.2026) — `/tmp/grok-risk0/libcp/`

Камеру **не трогали**. Вход: `/tmp/grok-lri-audit/L16_00181.lri`.

#### ABI

| Символ | Факт |
| --- | --- |
| `GetVersion()` | `const char*` → **`0.26.3 (libcp_v_0_26_1-9-g3c966)`** |
| `IsHardwareCompatible()` | только CPUID (SSE2+SSSE3+SSE4.1+POPCNT). **Нет dongle.** → **1** |
| `Renderer` | 24 B: vptr + shared_ptr |
| `Image` / `ImagePyramid` | 16 B: shared_ptr |
| `Create(profile)` | sret; private для profile 0…7 |
| `setInputDataStream(ptr,len)` | ест полный `.lri`; profile **0,3** — долгий Ceres |
| `render(level, ROI, type, flag)` | ROI = **`{x0,y0,x1,y1}`** (не w/h), см. `0x3b8ba0` |
| `outputBuffer()` | пирамида 3328×2496 … 416×312, **stride = w×4** |

#### Прогоны

```text
GetVersion / IsHW / Create(0..3) / setInput(163 MB) / render — без падения
calibration.lri как input → "Bad LRI: No Hw Info found!"
setMode(1) → "Refocus mode requested before depth available!"
```

#### Пиксели — ещё не закрыто

Буфер аллоцируется, содержимое пока **нули**. Нужен completion path:

- [ ] async wait (`StereoAsyncAPI`, listeners)
- [x] `writeImage` — **не нужен** для продукта; export = outputBuffer→PPM/JPG (§ D2)
- [ ] `DirectRenderer`
- [ ] mono path / `Param*`
- [ ] уточнить 4 bpp на ненулевых данных

**Проверено:** desktop `libcp` **не** режет по «железу Light»; жрёт shot `.lri`;
fusion-размер 3328×2496. **Открыто:** ненулевые пиксели / export.

---

## R2. Два монохромных модуля (A2, C6) — повторный аудит

**Вопрос.** Не «кажется ли», а **факт ли**, что два из шестнадцати модулей L16 —
настоящие панхроматические (без CFA), а именно **A2** (группа 28 мм) и **C6**
(группа 150 мм).

**Вердикт Grok (05.08.2026):** **да, это факт**, не догадка. Несколько независимых
линий сходятся; ни одна не опровергает. Пиксельный тест в этой сессии не
перегонялся заново (на Mac нет `.lri`, `adb devices` пуст) — его числа взяты из
измерений Claude на LFCLHMB7C0700105 / LightOS 1.3.5.1; **методика, протокол,
бинарники и маппинг CameraID→SensorType перепроверены здесь с первоисточников**.

Канон Claude: `monochrome.md`,
`vendor/Light-L16-Archive/Hardware/monochrome-modules.md`.

### Карта (как у Claude, подтверждена протоколом)

| Группа | ЭФР | id | Сенсор (hw_info) |
| --- | --- | --- | --- |
| A | 28 мм | A1–A5 | A2 → **AR1335_MONO**, остальные AR1335 |
| B | 70 мм | B1–B5 | все цветные AR1335 |
| C | 150 мм | C1–C6 | C6 → **AR1335_MONO**, остальные AR1335 |

`CameraID` в protobuf (`lri-proto/proto/camera_id.proto`, rev lri-rs `91365de`):

```text
A1=0 … A5=4, B1=5 … B5=9, C1=10 … C6=15
```

### Линия 1 — протокол Light (заводской enum, не чья-то метка)

Файл: `~/.cargo/git/checkouts/lri-rs-…/91365de/lri-proto/proto/sensor_type.proto`

```protobuf
enum SensorType {
    SENSOR_UNKNOWN = 0;
    SENSOR_AR835 = 1;
    SENSOR_AR1335 = 2;
    SENSOR_AR1335_MONO = 3;   // ← отдельный тип сенсора
    SENSOR_IMX386 = 4;
    SENSOR_IMX386_MONO = 5;
}
```

`hw_info` (`hw_info.proto`): каждый `CameraModuleHwInfo` несёт `id` + `sensor`.
То есть камера **сама** в каждом `.lri` пишет, какой id какого типа.

`sensor_bayer_red_override` (`camera_module.proto` field 13): координаты красного
в 2×2. Для моно — `(-1, -1)` = «мозаики нет». Та же семантика в
`scripts/lri-mono/src/container.rs` (`cfa()` → `None` при `(-1,-1)`) и в lri-rs
`cfa_string()`.

`ColorCalibration.SpectralData.ChannelFormat` (`color_calibration.proto`):

```protobuf
enum ChannelFormat { MONO = 0; RGB = 1; BAYER_RGGB = 2; }
```

Завод измерял спектральный отклик моно-модулей **отдельным** форматом канала.

Маппинг lri-rs (`types.rs`):

```text
SENSOR_AR1335      → SensorModel::Ar1335
SENSOR_AR1335_MONO → SensorModel::Ar1335Mono
cfa_string(Ar1335Mono) → None
color_type(Ar1335Mono) → Grayscale
```

Автор lri-rs (LRI.md): «i've only ever seen AR1335 and it's monochrome variant» —
стороннее независимое наблюдение, не из openlight-camera.

### Линия 2 — пиксели (физика CFA, не метка)

Метод в `scripts/lri-mono/src/main.rs` → `print_tiled_cfa_test`:

- тайл 64×64;
- средние по четырём фазам 2×2;
- размах / уровень тайла;
- тайлы с level &lt; 8 пропускаются.

Почему не обмануть нейтральной сценой: global mean фаз может схлопнуться;
**один** цветной участок в любом тайле даёт большой размах у CFA-сенсора.

Числа с устройства (Claude / monochrome-modules.md, LFCLHMB7C0700105):

| Модуль | Тип | tiles | median | p95 |
| --- | --- | --- | --- | --- |
| A1 | Ar1335 | 2303 | **67.7%** | 83.2% |
| A2 | Ar1335Mono | 2999 | **1.2%** | 4.0% |
| C6 | Ar1335Mono | 3120 | **1.2%** | 3.5% |

Целокадровые phase means A2: 199.5 / 199.0 / 199.6 / 199.2 — разброс 0.6 отсчёта
на уровне ~199 = фотонный шум, не фильтры.

### Линия 2b — живой прогон Grok 05.08.2026 (та же камера, read-only)

**Устройство:** `LFCLHMB7C0700105`, LightOS `00WW_1_351`, model L16.  
**Фокус UI:** штатное `light.co.lightcamera` — **не трогали**.  
**Действия:** только `adb pull` двух уже снятых `.lri` + `lri-mono --peek/--stats/--census`.  
Никаких install, `features.prop`, снимков, перезапусков.

| Файл | Размер | Фокусное (lri-rs) | Модулей |
| --- | --- | --- | --- |
| `/sdcard/DCIM/Camera/L16_00181.lri` | 162 625 784 | 35 mm (wide / mono 28) | 10 |
| `/sdcard/DCIM/Camera/L16_00182.lri` | 178 858 232 | 149 mm (tele / mono 150) | 11 |

Копии: `/tmp/grok-lri-audit/L16_00181.lri`, `…00182.lri` (вне репозитория).

**`hw_info` из контейнера (00181):** A2 → `Ar1335Mono`, все остальные в кадре → `Ar1335`.  
**`hw_info` (00182):** C6 → `Ar1335Mono`, остальные → `Ar1335`.  
Других mono id в этих двух кадрах нет.

**Peek (только 16.2 МБ плоскости по adb):**

| Кадр | Модуль | cfa (sbro) | level (black 42) | clipped |
| --- | --- | --- | --- | --- |
| 00181 | **A2** | **none** | 297.7 | 0.12% |
| 00181 | A1 | GRBG | 64.5 | 0.00% |
| 00182 | **C6** | **none** | 149.9 | 0.13% |
| 00182 | C1 | BGGR | 45.3 | 0.00% |

**Tiled CFA test (`--stats`, black=42, tile 64×64) — пересчёт Grok:**

| Модуль | sensor | tiles | median | p95 | max | phase spread (counts) |
| --- | --- | --- | --- | --- | --- | --- |
| A1 | Ar1335 | 2713 | **57.6%** | 71.0% | 111.7% | 35.9 |
| **A2** | **Ar1335Mono** | 3105 | **1.0%** | 4.9% | 13.6% | **1.1** |
| C1 | Ar1335 | 3081 | **60.0%** | 74.2% | 97.4% | 26.9 |
| **C6** | **Ar1335Mono** | 3116 | **1.6%** | 4.1% | 9.9% | **1.2** |

Phase means A2: 339.7 / 339.2 / 340.4 / 339.5 — четыре фазы совпадают.  
Phase means C6: 192.1 / 191.4 / 192.5 / 191.4 — то же.

Согласуется с прежними числами Claude (median ~1.2% mono vs ~58–68% colour);
расхождение на доли процента — нормальный разброс сцены/порогов.

**Census: колориметрия**

| Модуль | daylight rg/bg | profiles |
| --- | --- | --- |
| A1, A3–A5, B*, C1–C5 | rg ~0.47…0.52, bg ~0.65…0.71 | D65 A F11 |
| **A2, C6** | **no daylight profile** | **[]** |

**Census: level per unit exposure** (level / (t_ms·gain) × 1e6, как в tool):

| 00181 | norm | 00182 | norm |
| --- | --- | --- | --- |
| A-group colour ~0.28…0.35 (A1 0.35 @ half gain) | | C-group colour ~0.06…0.11 | |
| **A2 mono 0.81** | ~+1.2 стопа к A-соседям | **C6 mono 0.19** | ~+1 стоп к C1–C5 |

(B3 на 00181 дал 0.82 — это **другой кусок сцены** 70 мм, не моно; у B3 cfa RGGB и полный color profile.)

**После прогона:** `adb devices` → device; focus всё ещё штатная камера. Состояние не меняли.

### Линия 3 — движок Light (и Android, и macOS)

Перепроверено `strings` 05.08.2026 на четырёх артефактах:

| Артефакт | `SENSOR_AR1335_MONO` | `MonoFusion` / Panchromatic |
| --- | --- | --- |
| `work/reverse/gallery_libs/libcp.so` | есть | `lt::MonoFusion`, `SensorType::Panchromatic`, … |
| `vendor/…/light_gallery_decompiled/lib/arm64-v8a/libcp.so` | есть | то же |
| `vendor/…/assets/lric` (ELF aarch64) | есть | `color2MonoCoeffs`, ChannelFormat |
| `work/reverse/libcp-oracle/run/libcp.dylib` (macOS) | есть | то же семейство |

Характерные строки (не маркетинговые — **error paths** runtime):

```text
Called MonoFusion::initialize() twice!
Empty mono!
Mono sensor does not have panchromatic noise calibration!
Panchromatic noise model doesn't exist!
SensorType::Panchromatic
SensorColorPattern::Panchromatic
color2MonoCoeffs
```

Отдельный конвейер `MonoPipelinePayload` / `PostProcessingGray` рядом с Bayer/Color —
движок **умеет** моно как first-class path, а не «обесцветить JPEG».

Также в enum есть `SENSOR_IMX386_MONO` — задел под другое железо; на L16 в `.lri`
наблюдались только AR1335 / AR1335_MONO (lri-rs + Claude census).

### Линия 4 — Camera2 молчит намеренно, это не опровержение

`vendor/Light-L16-Archive/Hardware/camera-info.txt`:

```text
org.codeaurora.qcamera3.sensor_meta_data.is_mono_only (80080001): byte[1]
  [0 ]
```

Логическая камера **не** mono-only: снаружи одна камера, цветной fusion. Тег
описывает logical camera, **не** модули за ASIC. Отсутствие mono в Camera2 **не**
означает отсутствия моно-модулей.

### Линия 5 — поведение на съёмке (Claude, 03–04.08)

| Наблюдение | Источник |
| --- | --- |
| Режим `mono`, 28 мм → в `.lri` есть план A2, `native` извлекается | `log/2026-08-03`, `monochrome.md` |
| 150 мм → C6 на месте, 11 модулей, 179 МБ (`L16_00182`) | `log/2026-08-04` |
| У моно нет daylight color profile (`no daylight profile` / пустые profiles) | census в `open-questions` |
| +~1 стоп света vs цветные при той же экспозиции (после нормировки) | census 04.08 |
| На 70 мм моно-план в контейнер не попадает | конструкция групп A/B/C |

### Что **не** является догадкой vs что ещё открыто

| Утверждение | Статус |
| --- | --- |
| A2 и C6 — AR1335 **без CFA** (panchromatic) | **факт** (метаданные + пиксели + прошивка/движок) |
| Остальные 14 — цветной AR1335 с Bayer | **факт** (CFA test + sbro ≠ −1) |
| Третьего «типа матрицы» на проверенном юните нет | **факт** на LFCLHMB7C0700105 |
| Все L16 в мире такие же | **предполагается** (один юнит; дёшево проверить census) |
| Зачем моно только на краях (A, C), нет в B | **открыто** (в бинарниках объяснения нет) |
| Участвует ли A2 в итоговой цветной склейке как luma | **открыто** (палец на модуле / `LensObstructionDetector`) |

### Методика (уже прогнана 05.08)

```sh
# read-only peek с камеры
lri-mono --peek A2 --device /sdcard/DCIM/Camera/L16_00181.lri --out /tmp/grok-lri-audit
lri-mono --peek C6 --device /sdcard/DCIM/Camera/L16_00182.lri --out /tmp/grok-lri-audit

# полный CFA + census (после adb pull в /tmp)
lri-mono /tmp/grok-lri-audit/L16_00181.lri --stats --modules A1,A2 --out /tmp/…
lri-mono /tmp/grok-lri-audit/L16_00182.lri --stats --modules C1,C6 --out /tmp/…
lri-mono /tmp/grok-lri-audit/L16_00181.lri --census --out /tmp/…
lri-mono /tmp/grok-lri-audit/L16_00182.lri --census --out /tmp/…
```

### для Claude

Живой пересчёт Grok 05.08 на LFCLHMB7C0700105, кадры `L16_00181` / `L16_00182`:

- A2/C6: `hw_info Ar1335Mono`, `cfa none`, tiled CFA **median 1.0% / 1.6%**
- A1/C1: `Ar1335`, CFA **median 57.6% / 60.0%**
- mono: **no daylight profile**; colour: D65/A/F11
- ~+1…1.2 стопа по level per unit exposure vs соседи той же группы

Можно вписать в `monochrome.md` как повторную независимую проверку — Grok файлы Claude
**не** правил.

---

## R3–R4. Указатели (без переаудита)

- **Census 16 модулей, +1 стоп, колориметрия** — `open-questions.md` § «Матрицы»,
  `log/2026-08-04.md`.
- **JPEG 1440×1080 = прокси** — `open-questions.md` § «JPEG на камере».
- **Астро-потолки 29.98 с / ISO 12800, HAL 19.45 с** — `open-questions.md`
  § «Закрываются только съёмкой».

---

## R7. Заводская калибровка на устройстве (read-only adb)

**Риск 0:** только `adb pull` / `ls` / `strings`. Снимать/ставить ничего не надо.

### `/vendor/lib/liblightcalibration.so` (ARM 32-bit, 788 KB)

Снято в `/tmp/grok-risk0/calib/liblightcalibration.so`.

| Находка | Деталь |
| --- | --- |
| Типы сенсоров | `SENSOR_AR1335`, **`SENSOR_AR1335_MONO`**, `SENSOR_IMX386`, `SENSOR_IMX386_MONO`, `SENSOR_AR835`, `SENSOR_UNKNOWN` |
| Слово `panchromatic` | есть в .so |
| Заводские protobuf | `FactoryModuleCalibration`, `GeometricCalibration`, `ColorCalibration` (+ `ChannelFormat`), `FlashCalibration`, `ToFCalibration` |
| Optical zoom | `OpticalZoomPB`, `ModuleCoverage`, `get_A1toB4_center_offset`, `get_B4toC5_center_offset` |
| Опорные модули в логах | `"Processing A1"`, `"Processing B4"`, `"Processing C5"` — **A1/B4/C5 как якоря**, не A2/C6 |
| Путь | `/lightcal/calibration.lri` |
| Исходники (vendor path) | `vendor/qcom/.../mm-camera2/.../light-calibration/` + `opticalzoomcalib.proto` |

**Интерпретация:** калибровочный стек **знает** mono-сенсор; оптический зум/перекрытие
считается через **A1→B4→C5** (цветные опоры групп). Моно A2/C6 в этой цепочке
якорями не названы — согласуется с «яркостный/служебный» ролью, **не доказано**.

### `/lightcal/` на LFCLHMB7C0700105

| Файл | Размер | Примечание |
| --- | --- | --- |
| `calibration.lri` | 333 048 | LELR, 5 блоков, внутри строка `L16` |
| `calibration.lri.md5` | 50 | |
| `zoom_calib_v0.lri` | 6 232 | |
| `asic_calib_v1.lri` | 9 884 | |
| `hotpixel.rec` | 31 020 168 | карта битых (не тянули) |
| `uuid.txt` | 32 | |

`lri-mono calibration.lri --stats` → **0 modules** (нет image planes — только
мета/калибровка). `libcp setInput(calibration.lri)` → **`Bad LRI: No Hw Info found!`**.

Копии: `/tmp/grok-risk0/calib/`.

### для Claude

- На устройстве живой `/lightcal/calibration.lri` — можно парсить offline без съёмки.
- `liblightcalibration` подтверждает `SENSOR_AR1335_MONO` на **прошивке**, не только в
  `libcp`/снимках.
- Цепочка optical-zoom anchor: A1–B4–C5.

---

## R5. Гимбал DJI RS4 Mini

| Факт | Доказательство |
| --- | --- |
| HID-клавиатура `/dev/input/event17` | log 2026-08-04 |
| Все кнопки → `KEY_VOLUMEUP` (usage `000c00e9`) | захват событий |
| Наша сборка: код 24 → полный спуск | лог `onKeyDown` … `MSG_TRIGGER_CAPTURE_TO_HAL` |
| Полунажатие/удержание до камеры не доходят | 6 событий, DOWN–UP 0.14…9.6 мс |
| Полноценный motion API — только CAN/RSA, не BLE | open-source DJI R SDK; у Mini порт RSA **не подтверждён** |

Очередь: счёт тапов на одной клавише; осмотр корпуса Mini на RSA/CAN.

---

## R6. Видео (открыто)

| Наблюдение | Файл / число | Вопрос |
| --- | --- | --- |
| ~40 к/с @ 2688×1512 | `L16_00116.mp4`, медиана 24.3 мс | ASIC шире, чем Camera2 33.3 мс |
| 4K UHD разваливается | `L16_00145.mp4`: 25 с видео / 65 с звука | термика / IO / кодер |
| Нарезка 4 ГБ | `L16_00103` + `_002` | стык без потери кадров? |
| Краш профиля `high` | файл 3227 байт | чистый logcat штатного APK |

---


---

## R8. Lumen 2.3.0.606 на текущем Mac (Apple Silicon) — почему «не стартует»

**Источник:** `/Users/igor/WebstormProjects/light-l16/Lumen/`  
(только `Lumen-2.3.0.606.dmg` + `Lumen System Requirements.txt`; установленного `.app` в дереве нет).

**Машина:** `arm64`, macOS **26.5.1** (25F80). Rosetta установлена (`com.apple.pkg.RosettaUpdateAuto`, `oahd` жив).

### Что это за бинарь

| | |
| --- | --- |
| Bundle | `co.light.Lumen`, version **2.3.0.606** (Oct 2019) |
| Архитектура | **только x86_64** (thin Mach-O) |
| Стек UI | **Qt 5.11** + Qt Quick / QML, OpenGL (`QtOpenGL`, `OpenGL.framework`) |
| Движок | `Frameworks/libcp.dylib` + `libceres` + `liblricompression` (тот же CIAPI, что в R1) |
| Подпись | Developer ID Light Labs (`H9768XDA8Y`), **Notarized**, `spctl` accepted |
| Требования (из txt) | **Intel Mac 2012+**, OSX 10.15+; support ended |

### Симптом (воспроизведено 05.08.2026)

1. `open Lumen.app` / запуск `Contents/MacOS/Lumen` под Rosetta: процесс **живёт**, CPU есть.
2. Окон **0** (`System Events` → `windows: 0`). Снаружи выглядит как «не открылся».
3. **Crash report нет** — это hang UI, не падение.
4. `sample`: main thread завис в  
   `QQuickWindow::event` → expose → **`QWaitCondition::wait`**  
   после `QWindowSystemInterface::handleExposeEvent` (SynchronousDelivery).  
   `QSG_INFO`: **`threaded render loop`** — scene graph ждёт render thread, который не отдаёт кадр (типичный Qt Quick + OpenGL/Rosetta/новый macOS).

### Не причины

- Нет Rosetta — **нет**, Rosetta есть; x86_64 бинарь стартует.
- Gatekeeper / notarization — **нет**, `spctl: accepted`.
- Битый DMG — **нет**, CRC ok, codesign ok.
- Только «нужен Intel» по бумажке — формально да, но реальный блокер **не** «не запускается exec», а **не рисуется окно**.

### Workaround (проверено)

```bash
# скопировать .app с DMG куда угодно, затем:
export QT_QUICK_BACKEND=software
export QSG_RENDER_LOOP=basic   # опционально, safer
arch -x86_64 /path/to/Lumen.app/Contents/MacOS/Lumen
```

С `QT_QUICK_BACKEND=software` в логе:

```text
Frame rendered with 'software' renderloop in 6–7ms
WindowMain_QMLTYPE_… name="mainWindow"
```

Готовый скрипт (вне репо): `/tmp/grok-risk0/run-lumen-software.sh`  
Копия app для тестов: `/tmp/grok-risk0/Lumen.app` (с DMG).

Мелкий QML warning (не блокер):  
`ViewToolbar.qml:75 ReferenceError: focusDepthVisible is not defined`.

### Практично для проекта

| Цель | Как |
| --- | --- |
| Открыть `.lri` глазами Light | Lumen + `QT_QUICK_BACKEND=software` под Rosetta |
| Рендер без UI | наш harness `libcp` (R1) — не зависит от Qt window |
| «Нативный» arm64 Lumen | **невозможен** без бинарника Light; support ended |

### Открыто

- [ ] Упаковать `.command` / alias рядом с DMG в `light-l16/Lumen/` (если попросишь — только с явного ok на запись туда; сейчас скрипт в `/tmp`)
- [ ] Проверить, хватает ли software-backend для тяжёлого 52 Мп preview (может быть медленно)
- [x] Dual display + open LRI → **SIGSEGV** в `ImagePlaneNode::attachToWindow` (R12), не только hang


---

## R9. Desktop-репозиторий: isamarin/luminat

| | |
| --- | --- |
| Remote | https://github.com/isamarin/luminat.git |
| Local | `/Users/igor/IGRS/luminat` |
| Branch | `main` (tracking `origin/main`) |
| HEAD при clone | `67cee5b` feat(luminat): GUI fuse wrapper with drag-and-drop export |
| Parent | fork of `gennyble/lri-rs` |
| Языки | Rust workspace + Tauri 2 GUI |

### Сборка (из README)

```bash
cd /Users/igor/IGRS/luminat
make release          # CLI light
make lumen-release    # GUI ./target/release/lumen
# или: cargo tauri dev  из lumen/src-tauri
```

### Роль относительно openlight-camera

- **openlight-camera** — Claude, APK, железо.
- **luminat** — Grok desktop: парсинг LRI, свой fusion, GUI, позже **вызов libcp** (R1).
- `scripts/lri-mono` в openlight-camera и `lri-rs` в luminat — родственные; luminat шире (fusion + GUI).

### Следующие шаги Grok в luminat (когда перейдём к фазе 2)

- [ ] Собрать `light` + `lumen` на этой машине, прогнать на `L16_00181.lri`
- [ ] Mono A2/C6 path в extract/GUI
- [ ] Опциональный backend: native `libcp.dylib` (R1) рядом с software fusion
- [ ] Не зависеть от Light Lumen.app UI (R8)


---

## R10. Пост-обработка: один движок, 13 vs 52 Мп, роль mono

**Для Claude (камера/галерея).** Источники: [luminat `FUSION.md`](https://github.com/isamarin/luminat/blob/main/FUSION.md)
(07.2026), `libcp` strings (R1), живые `.lri` (R2), `open-questions` про JPEG-прокси.
Confidence помечен явно.

### 1. Кто что делает (архитектура 1.3.5.1)

| Компонент | Роль | Confidence |
| --- | --- | --- |
| **light_camera** | Съёмка → пишет `.lri` (+ ViewPreferences/GPS LELR). **Не** делает 16→1 | confirmed (FUSION.md, smali ImageSaver) |
| **light_gallery** | На устройстве: `LriProcessorService` → JNI `LibCpRenderer` → **`libcp.so`** | confirmed |
| **Lumen (macOS)** | То же API: **`libcp.dylib`**, `CIAPI::Renderer` | confirmed (строки 104≈105 совпадают с ARM) |
| **lightprocessingservice** | AIDL-заготовка в camera; **APK на 1.3.5.1 нет** | confirmed (system.img) |

**База одна:** gallery и Lumen — один closed engine `libcp` (Ceres + Halide + CIAPI).
Камера — только захват контейнера. Наш harness (R1) бьёт в тот же `libcp.dylib`.

```
.lri → setInputDataStream
    → Stereo: undistort + SGM (пирамида 3–6), ToF как seed
    → ComputeFlowField (по одному dense flow на модуль)
    → warp в кадр reference (широкий, 28 mm-экв)
    → Super-res (теле → деталь на wide; mono исключены)
    → blend (DepthAndOcc, confidence)
    → Ceres refine (позы)
    → DepthEditor / refocus (desktop profile)
    → writeImage / JPEG (+ GDepth XMP на desktop)
```

Порядок **likely** (зависимости + error strings), имена стадий **confirmed**.

### 2. 13 Мп на камере vs 52 Мп в Lumen — костыль или лимит?

**Факты:**

| Уровень | Что видно | Confidence |
| --- | --- | --- |
| Мануал / HUD | превью · **13 Мп на камере** · **52 Мп в Lumen** | product claim |
| Один модуль AR1335 | **4160×3120 ≈ 13.0 Мп** | confirmed |
| JPEG рядом с `.lri` при съёмке | **1440×1080 ≈ 1.5 Мп** — прокси, не fusion | confirmed (open-questions, L16_00181) |
| `libcp` outputBuffer (R1; D measured) | profile **1/2 → 4160×3120**; profile **3 → 10432×7824** full fill | confirmed § D |
| Profiles | Create 0…3 = THUMBNAIL/MOBILE/CAMERA/DESKTOP; map_profile clamp ≤3 | confirmed smali + measure |
| Super-res | `"Super-res does not support mono modules!"`, `"This profile doesn't support super-resolution!"` | confirmed strings |
| `gDepth` | **DESKTOP only** в ProcessRequest | confirmed |
| Depth Editor | только **Desktop profile** | confirmed string |

**Интерпретация (likely, не «железо не умеет 52»):**

1. **13 Мп ≈ один полный сенсор / fusion без (или с лёгким) super-res** на wide reference.
   Это **естественный** масштаб reference plane, не «обрезали ради релиза» в смысле
   «сенсор меньше». Склейка цветных на 28 mm и даёт ~полный кадр A-группы.

2. **52 Мп — marketing/super-resolution tier:** теле-модули (70/150) после warp
   добавляют detail на более мелкую сетку (теоретически ~2× по стороне → ~4× площадь:
   13×4 ≈ 52). Включается **profile + enabledSuperRes**, полноценно в **Lumen/DESKTOP**,
   не в thrifty on-device path.

3. **Почему на камере не 52:** не потому что ASIC «не видит» 16 модулей (они в `.lri`),
   а потому что:
   - fusion сидит в **gallery**, не в camera app;
   - mobile profile **`DEVICE_L16`**, часть фич (super-res, gDepth, depth editor) режется
     строками/`This profile doesn't support super-resolution`;
   - **MSM8996 + нагрев + RAM + время** — тяжёлый SGM+flow+Halide на 52 Мп на устройстве
     2016–18 нереалистичен для UX (галерея и так «calculating depth»).

4. **1.5 Мп JPEG при спуске** — отдельный слой (preview), **не** «13 Мп fusion».
   13 Мп — результат **галерейной** сборки из `.lri` (или claim HUD).

**Итог для Claude:** 13 Мп на устройстве — **осознанный product/compute tier** на том же
движке, а не «HAL отдаёт только 13». Лимит **продуктовый/термический/профильный**, не
«модули не пишут больше». 52 Мп / desktop canvas = **Create(3)**; DESKTOP_0/1 —
ProcessLevel, не отдельные Create() ints — **закрыто** (§ D).

### 3. Что делают монохромные модули (A2, C6)

**Есть / нет CFA — факт** (R2). **Роль в fusion — mostly likely из `libcp`:**

| Наблюдение | Вывод |
| --- | --- |
| `"Super-res does not support mono modules!"` | mono **не** участвуют в super-res detail stack |
| `"ReferenceImageCache not implemented for mono camera!"` | mono **не** reference для cache/super-res |
| `"Empty mono!"`, `lt::MonoFusion`, `PostProcessingGray`, `SensorType::Panchromatic` | отдельный **mono pipeline** (серый fusion / post) |
| `color2MonoCoeffs` | цвет → mono для модели шума / сравнения |
| +~1 стоп света (census) | полезны как **luma / SNR**, не как цвет |
| A2 на 28 mm, C6 на 150 mm | mono на **краях** фокусного диапазона (wide + tele), не в B |
| Optical zoom anchors A1→B4→C5 (R7) | калибровка зума на **цветных** опорах, не mono |

**Сводка ролей (likely):**

1. **Depth / stereo:** панхроматический план без demosaic — чище корреляция; seed/уточнение
   disparity вместе с цветными (в luminat: «mono → depth + luminance»).
2. **Luminance / noise:** ярче на ~1 stop → веса в blend, denoise; не super-res chroma.
3. **Отдельный mono product path:** `MonoFusion` — если когда-либо включат «настоящий
   ч/б» рендер (вариант Г monochrome.md), путь в engine уже есть.
4. **Не** «главная 13 Мп камера вместо fusion»: fusion reference — **широкий цветной**
   (effective focal ≥ reference; widest).

**Открыто / проверка:**

- [ ] Палец на A2 при съёмке → шум/деталь gallery JPEG + лог `LensObstructionDetector`
      (open-questions) — докажет вклад в **цветной** 13 Мп output.
- [ ] Участвует ли A2 в SGM как view или только mono pipeline.
- [ ] C6 на 150 mm: depth tele vs luma only.

*(FUSION.md ошибочно писал «mono = C-row»; фактически **A2 + C6** — R2.)*

### 4. Что уже есть в luminat (свой, не libcp)

Software fusion MVP (`light/src/fuse.rs`):

- undistort (CRA) → plane-sweep depth (NCC) → homography warp → gray blend → TIFF/DNG;
- reference + tele; preview `max_side` или full_res;
- **не** полный SGM+ComputeFlowField+super-res libcp.

Путь продукта: GUI `lumen` (Tauri) + optionally **call libcp** (R1) для «как Lumen 52 Мп».

### 5. для Claude — короткие тезисы

1. **Camera app ≠ fusion.** 16→1 = gallery `libcp.so` или desktop `libcp.dylib`.
2. **Один engine** CIAPI; 13 vs 52 = **profile / super-res / platform**, не разные матрицы.
3. **13 Мп** ≈ full single-sensor / non-SR fusion; **не** «сенсор 1.5 Мп»; proxy JPEG 1.5 Мп — третье.
4. **52 Мп** ≈ super-res tier (Lumen/desktop); mobile profile часто **без** full SR.
5. **Mono A2/C6:** depth + luma + mono path; **out of super-res**; не reference wide color.
6. Режим mono в APK = доступ к планам; «настоящий» mono JPEG — gallery MonoFusion или
   desktop, не магия camera JPEG.


---

## R11. Лесные кадры с камеры — визуальный отбор (05.08.2026)

**Источник:** `/sdcard/DCIM/Camera/`, только `adb pull` JPEG (read-only).  
**Локально:** `/tmp/grok-risk0/forest-preview/` (выборка), picks → `/tmp/grok-risk0/forest-picks/`.

### Разделение по эпохам

| Диапазон | Когда | Что |
| --- | --- | --- |
| `L16_00001`–~`00111` | .lri 17–19.07, JPG часто 19.07/28.07 | **лес / поле / дача** (Igor) |
| `L16_00115`–`00183` | 04.08 вечер | тесты Claude (mono, video, long exp) |
| `00184`–`00189` | 05.08 | Lumen/тесты |

Все просмотренные JPG имеют comment **`Created with LibCP 0.26.3 (3c966)`** — это **уже fusion-output** (gallery/Lumen re-export), не camera proxy 1440×1080.  
Размеры: чаще **4160×3120** (13 Мп), реже 3328×2496 / crop.

### Визуально сильные (для fusion / depth / mono)

| Кадр | Размер | Сюжет | Зачем интересен |
| --- | --- | --- | --- |
| **L16_00026** | 4160×3120 | высокий еловый/смешанный лес, стволы вверх | **много глубины**, слойка, тень/свет — stereo/SGM/refocus |
| **L16_00020** | 4160×3120 | ель крупно, хвоя + берёза | **микродеталь** (super-res / sharpness), +1 stop mono test |
| **L16_00016** | 4160×3120 | поле кукурузы, берёзы по краям | горизонт, **DR** небо/зелень, wide reference |
| **L16_00050** | 4160×3120 | тропа в лесу | path vanishing, depth gradient |
| **L16_00075** | 4160×3120 | знак/ограда + просека | геометрия, mid-ground |
| **L16_00100** | 4160×3120 | сарай + розы + сад | **много планов**, текстуры, bokeh candidate |
| L16_00009 | 4160×3120 | обочина + кусты + небо | open sky DR |
| L16_00055 / 00090 | 4160×3120 | лес (в picks) | запас |

**Не брать как «красивый эталон»:** `L16_00001` — полный blur (первый/тестовый).

### .lri на камере

Соответствующие `.lri` для picks **ещё лежат** на `/sdcard` (для libcp harness / luminat fuse — тянуть точечно, ~160–180 МБ).

### Связь с исследованиями

- LibCP comment = тот же engine **0.26.3**, что R1 harness `GetVersion`.
- 13 Мп JPEG на диске камеры после gallery/Lumen ≠ 1.5 Мп proxy при спуске (R10).
- Лучшие depth-сцены: **00026, 00050, 00100**; detail: **00020**; landscape DR: **00016**.


---

## R12. Lumen crash: `ImagePlaneNode::attachToWindow` null deref

**Report:** 2026-08-05 15:45:44, PID 53169, path `/private/tmp/…/Lumen.app`  
**Host:** MacBookPro18,2 (M1 Max), macOS 26.5.1, **X86-64 Translated (Rosetta)**  
**Exception:** `EXC_BAD_ACCESS` / `KERN_INVALID_ADDRESS` at **0x0** (SIGSEGV)

### Call site (confirmed in binary)

```
ImagePlaneNode::attachToWindow(QQuickWindow*)  @ Lumen + 0x8fd10
crash at +74 (0x8fd5a):  movl 0x98(%rbx), %ecx
```

Дизассемблер:

1. `texCount()` — если 0, early exit (без crash).
2. Берёт указатель текстурного узла: `rbx = *(array + i*8)`.
3. `texNameAt(i)` → GL texture id.
4. **`movl 0x98(%rbx)`** — если **`rbx == NULL`** → ровно этот crash.

То есть: `texCount > 0`, но слот массива текстур **nullptr** (объект QSG geometry/material не создан).

Дальше по коду вызывался бы `QQuickWindow::createTextureFromId` (OpenGL/QSG path).

### Контекст потоков

| Thread | Что делает |
| --- | --- |
| **0 main** | crash в attachToWindow (UI / Qt Quick scene graph) |
| **9–28** | `libcp.dylib` worker pool — `pthread_cond_wait` (рендер/fusion **уже крутится**) |
| QQmlThread / NSEvent | живы |

**libcp не падает** — падает **слой показа** (Qt Quick image plane → GL texture).  
`RIP mismatch` в report — типичный артефакт Rosetta, не отдельный баг.

### Timeline

```
15:45:33  launch Lumen (из /tmp, Rosetta)
15:45:44  crash (~11 s) — как раз open sample.lri / first display
```

Session DB: `sessions` row `sample` → `file:///private/tmp/grok-risk0/sample.lri` @ 12:45 UTC = 15:45 EEST.

При этом **кэш JPEG уже есть**:  
`~/Library/Caches/Light/Lumen 2.0/LFCLHMB7C0700105/L16_00181.jpg`  
**3328×2496**, comment `Created with LibCP 0.26.3 (3c966)` — fusion **успел** (или был раньше), UI при attach — нет.

### Причины (likely, stacked)

1. **Rosetta + Qt 5.11 OpenGL/QSG** — context/textures неполные; software backend (`QT_QUICK_BACKEND=software`) обходит hang при старте (R8), но **ImagePlaneNode всё равно зовёт `createTextureFromId`** (GL API) → null materials.
2. **Гонка:** libcp отдаёт planes, UI attach до заполнения texture array.
3. **macOS 26 + dual display** — усугубляет, не первопричина (null pointer однозначен).

### Выводы

| | |
| --- | --- |
| Можно ли «чинить» Lumen UI? | Без патча Qt/GL слоя — **нет надёжно** на Apple Silicon |
| Полезен ли Lumen всё равно? | **Да:** hotpixel cache, LibCP JPEG в Caches, prefs Sparkle |
| Путь desktop | **не** полагаться на Lumen UI; **libcp harness / luminat** (R1, R9) |
| Workaround | смотреть `~/Library/Caches/Light/Lumen 2.0/<serial>/*.jpg` после частичного прогона; не ждать окна |

### для Claude / desktop

Crash **не** про «плохое .lri» и **не** про падение CIAPI core.  
Про **устаревший Qt Quick + OpenGL preview node** на Rosetta/macOS 26.  
Исследовать fusion по кэшу LibCP и прямому `libcp.dylib`, не через GUI Lumen.


---

## A1. libcp harness: `.lri` → image (DONE 05.08.2026)

**Tool:** `/tmp/grok-risk0/libcp/a1_export` (+ `a1_export.cpp`)  
**Engine:** `libcp.dylib` 0.26.3, Ceres 1.12, Rosetta x86_64

### Recipe

```bash
cd /tmp/grok-risk0/libcp
clang++ -arch x86_64 -std=c++14 -O1 -o a1_export a1_export.cpp -Wl,-rpath,$PWD
arch -x86_64 ./a1_export libcp.dylib input.lri out.ppm 1   # profile 1 = fast
sips -s format jpeg out.ppm --out out.jpg
```

Flow: `Create(profile)` → `setInputDataStream` → `render(level, ROI{x0,y0,x1,y1}, type, flag)` → poll `outputBuffer` until signal → write PPM (float/RGBA4 → 8-bit).

### Results

| Input | Profile | Output | Size | Notes |
| --- | --- | --- | --- | --- |
| `L16_00181.lri` (mono desk) | 1 | `a1_00181.jpg` | **3328×2496** | matches Lumen cache dims; real scene (mic, Pringles, pads) |
| `L16_00026.lri` (forest) | 1 | `a1_00026.jpg` | **4160×3120** | full 13 Мп; tall canopy matches forest pick |

- First polls after render: empty buffer; **~0.5–2 s later** pyramid fills (async).
- bpp=4 (float or packed); tone map 0…1 → sRGB PPM works enough for preview.
- **No Lumen UI**, no crash.

### A1 criterion

**Met:** CLI lri → jpeg, exit 0, 100% non-black pixels, dimensions match known LibCP outputs.

### Next

- **A2:** pixel/histogram compare harness vs `~/Library/Caches/Light/Lumen 2.0/…/L16_00181.jpg`
- **A3:** also 00020, 00016


---

## A2–A3. Сверка и лесные picks (DONE 05.08.2026)

### A2 — harness vs Lumen cache (`L16_00181`)

| | harness `a1_00181.jpg` | Lumen cache |
| --- | --- | --- |
| Size | 3328×2496 | 3328×2496 |
| MAE (global) | **10.9** | — |
| MAE center 200 | 14.0 | |
| MAE corner 100 | 6.8 | |
| Pearson gray r | **0.984** | |
| mean RGB | (131, 135, 133) | (125, 129, 126) |
| byte-identical | no | |

**Интерпретация:** та же сцена и геометрия (r≈0.98), harness чуть светлее / грубее tone-map
(clamp float 0…1, без полного export path `writeImage`). Не «другой fusion», а
**preview-tier dump**. Diff heatmap: `/tmp/grok-risk0/out/a2_diff_00181.png`.

### A3 — forest picks

| LRI | harness out | size | vs camera LibCP JPG MAE* |
| --- | --- | --- | --- |
| L16_00016 | `a1_00016.jpg` | **4160×3120** | ~38 |
| L16_00020 | `a1_00020.jpg` | **4160×3120** | ~47 |
| L16_00026 | `a1_00026.jpg` | **4160×3120** | ~43 |

\*MAE выше, чем vs Lumen cache: camera JPG — полный gallery/export path + возможно
другой profile/level; harness — profile=1 + crude float→8bit. Структура кадра совпадает
(поле/ель/полог).

**Файлы:** `/tmp/grok-risk0/out/a1_{00016,00020,00026,00181}.jpg`  
**LRI:** `/tmp/grok-risk0/forest-lri/`

### Фаза A — критерий

**Закрыта:** CLI `.lri` → картинка без Lumen UI; размеры 13 Мп / 8.3 Мп; близко к LibCP cache.

### Дальше

**B1** — `make release` / `make lumen-release` в `/Users/igor/IGRS/luminat`.


---

## B1. luminat build + smoke (DONE 05.08.2026)

**Repo:** `/Users/igor/IGRS/luminat` (`main`, CalVer 2026.7.14)

### Build

```bash
cd /Users/igor/IGRS/luminat
make release          # ~40s → target/release/light  (1.5 MB, arm64)
make lumen-release    # ~2.5m → target/release/lumen (6.1 MB, arm64)
```

Оба бинарника **native arm64** (не Rosetta) — в отличие от Light Lumen.app.

### Smoke

| Check | Result |
| --- | --- |
| `light --help` | commands: gather, validate, fuse, extract |
| `light gather forest-lri/` | 00016/20/26: focal 28, **a1m (A2 mono)** in module list, fus geo:16/16 |
| `light extract L16_00026.lri` | 10 DNGs (A+B @ 28 mm), **A2 Ar1335Mono**, `fusion.json` |
| `./target/release/lumen` | starts, stays up 4s, no crash (killed) |

Note: @ 28 mm extract gives **10** planes (A+B groups), not C — matches capture set.

### Paths

```text
/Users/igor/IGRS/luminat/target/release/light
/Users/igor/IGRS/luminat/target/release/lumen
```

### Next (B)

- ~~B2–B4~~ → **section B complete** (see § B2, B3, B4)


---

## light fuse on L16_00026 (2026-08-05)

```bash
./target/release/light fuse --lri …/L16_00026.lri -o …/fuse26/preview \
  --lumen …/L16_00026.jpg --max-side 1024
./target/release/light fuse --lri …/L16_00026.lri -o …/fuse26/full \
  --lumen …/L16_00026.jpg --full-res --export-tiff
```

### Results

| Mode | Time | depth plane | score | modules | vs LibCP NCC | Outputs |
| --- | --- | --- | --- | --- | --- | --- |
| preview | ~1.2 s | **3396 mm** | 0.108 | 10 (9+1) | **0.17** | fused.png 832×624 |
| full-res | ~27 s | **7458 mm** | 0.079 | 10 | **0.38** | fused.png 4160×3120, canvas TIFF/DNG **10432×7824**, dng 163 MB |

Artifacts: `/tmp/grok-risk0/luminat-smoke/fuse26/{preview,full}/`

### Visual

MVP fuse = **gray, soft, noisy** (plane-sweep + average blend). Recognizable as canopy texture at full-res, but far from LibCP/a1_export color sharpness (std ~23 vs ~75).

| | Luminat fuse | libcp a1_export |
| --- | --- | --- |
| Color | gray only | full RGB |
| Sharpness | low | high |
| Size claim | canvas 10432×7824 (~81 Мп) | 4160×3120 (13 Мп) |
| Quality vs Light | weak NCC | matches Lumen cache structure |

### Implication

- Software fuse in luminat is a **geometry/depth lab**, not a Lumen replacement yet.
- **Quality path productized in B2:** `light libcp` (same engine as A1 harness).
- Improving plane-sweep is research (D); product batch/GUI is B3–B4.


---

## B2. `light libcp` — optional CIAPI backend (DONE 05.08.2026)

**Repo:** `/Users/igor/IGRS/luminat` (not openlight-camera APK tree)

### What shipped

| Piece | Path | Role |
| --- | --- | --- |
| x86_64 helper | `tools/libcp-export/libcp_export.cpp` → `make libcp-export` | dlopen `libcp.dylib`, render, dump PPM |
| README | `tools/libcp-export/README.md` | obtain Lumen dylibs, env vars |
| Rust module | `light/src/libcp.rs` | resolve paths, `arch -x86_64 env DYLD_…`, JPG convert |
| CLI | `light libcp --lri … -o … [--profile 1] [--format jpg\|ppm\|both]` | product entry |
| Makefile | `make libcp-export` | `clang++ -arch x86_64` |
| README | luminat `README.md` CLI table + libcp section |

**Not shipped (proprietary):** `libcp.dylib`, `libceres.dylib` — from Lumen.app or user `LUMINAT_LIBCP_DIR`.

### Usage

```bash
cd /Users/igor/IGRS/luminat
make libcp-export
make release
export LUMINAT_LIBCP_DIR="/Applications/Lumen.app/Contents/Frameworks"
# dev scratch also works:
# export LUMINAT_LIBCP_DIR=/tmp/grok-risk0/libcp
./target/release/light libcp \
  --lri /path/L16_00026.lri -o ./out --format jpg --profile 1
# → out/L16_00026.libcp.jpg
```

Env: `LUMINAT_LIBCP_DIR` | `LUMINAT_LIBCP` | `LUMINAT_LIBCP_EXPORT` | `LIBCP_MAX_WAIT_MS`.

### Smoke (2026-08-05)

```text
input:  /tmp/grok-risk0/forest-lri/L16_00026.lri  (162 625 784 B)
libcp:  0.26.3 (libcp_v_0_26_1-9-g3c966)  profile=1
out:    /tmp/grok-risk0/luminat-smoke/libcp26/
        L16_00026.libcp.ppm  4160×3120  38.9 MB
        L16_00026.libcp.jpg  4160×3120  ~1.57 MB
time:   ~2.3 s render + convert (wall ~3.2 s incl. spawn)
```

Matches A1 harness size/quality path (`a1_00026.jpg` ~1.58 MB). Software `light fuse` remains gray MVP (NCC 0.17–0.38).

### Implementation notes (for next agent)

1. **Rosetta only:** helper + dylibs are x86_64; arm64 `light` spawns via `arch -x86_64`.
2. **DYLD under `arch`:** parent `Command::env(DYLD_*)` is **not** enough — use  
   `arch -x86_64 env DYLD_LIBRARY_PATH=<lib_dir> <helper> …` so `@rpath/libceres.dylib` resolves.
3. **ROI** = `x0,y0,x1,y1` (not w/h). Fast path: L=0, T=0, F=0, ROI 4160×3120 then 3328×2496; poll until non-zero float buffer.
4. **image crate** needs `features = ["pnm"]` to read helper PPM for JPEG export.
5. Default auto-find: `/Applications/Lumen.app/Contents/Frameworks`, `tools/libcp-export/vendor/`, `/tmp/grok-risk0/libcp`.

### Next after B2

- ~~**B3** mono first-class~~ → **DONE** (§ B3)
- ~~**B4** batch~~ → **DONE** (§ B4)
- ~~LibCP in Tauri GUI~~ → **DONE** button «LibCP quality»
- ~~Research **D**~~ → **DONE** (§ D full)


---

## B3. Mono A2/C6 first-class (DONE 05.08.2026)

**Repo:** `/Users/igor/IGRS/luminat`

### What

| Piece | Detail |
| --- | --- |
| `light/src/mono.rs` | `MonoInfo`, export stems, DNG labels, focal hints 28/150 mm |
| API `LriSummary.mono` | `{present,count,cameras,a2,c6}` + `CameraSummary.is_mono` |
| `light extract --only-mono` | only panchromatic planes |
| Naming | `A2_mono.dng` / `C6_mono.dng` (Lightroom-friendly model string) |
| Previews | `mono/A2.png` (default; `--no-mono-previews` to skip) |
| Sidecar | `mono.json` next to `fusion.json` |
| `gather` | `\| mono:A2≈28mm` |
| GUI | mono panel, MONO badge on cells, Export mono DNGs, LibCP quality |

### Smoke

```bash
./target/release/light gather /tmp/grok-risk0/forest-lri/
# → mono:A2≈28mm on 00016/20/26

./target/release/light extract …/L16_00026.lri …/b3-mono --only-mono
# → A2_mono.dng (25.9 MB), mono/A2.png, mono.json {a2:true,c6:false}
```

Forest 28 mm captures include **A2 only** (no C6) — expected for A-group.

---

## B4. Batch export (DONE 05.08.2026)

### What

| Command | Behavior |
| --- | --- |
| `light extract <dir> <out> [--only-mono]` | each `.lri` → `out/<stem>/` |
| `light libcp --dir <dir> -o <out>` | each `.lri` → `out/<stem>/<stem>.libcp.jpg` (sequential) |

### Smoke libcp batch (forest ×3, ~7 s wall)

```text
/tmp/grok-risk0/luminat-smoke/b4-libcp/
  L16_00016/L16_00016.libcp.jpg  4160×3120
  L16_00020/L16_00020.libcp.jpg
  L16_00026/L16_00026.libcp.jpg
```

Extract batch mono: `/tmp/grok-risk0/luminat-smoke/b4-extract/L16_*/A2_mono.dng`

### GUI

Tauri `lumen`: **LibCP quality** button (`libcp_lri` command), mono export buttons.

### Section B complete

| # | Status |
| --- | --- |
| B1 build | done |
| B2 libcp CLI | done |
| B3 mono | done |
| B4 batch | done |

**Section B complete. Section D complete.**  
**Claude (C):** camera mono UI — independent.  
**Grok next:** M0–M4 **done** (libcp + camera + ship + refocus/depth). Optional: notarized dmg / more CIAPI props.


---

## Plan Lumen — свой desktop Lumen (зафиксирован 05.08.2026)

**Определение done:** `.lri` → цветная склейка Light-quality → export JPEG, без Qt-Lumen UI.

| Решение | Выбор |
| --- | --- |
| Renderer | **libcp only** (profile 1 = 13 Мп, 3 = 10432×7824) |
| App shell | Tauri **`lumen`** |
| Software fuse | experimental |
| dylib | Lumen.app / `LUMINAT_LIBCP_DIR` |

| Веха | Критерий | Status |
| --- | --- | --- |
| M0 | CLI libcp + extract | **done** |
| **M1** | libcp-first viewer + export JPEG + fuse hidden | **done** |
| **M2** | camera pull + batch + cache | **done** |
| **M3** | .app + setup wizard + zoom/pan | **done** `make package-macos` |
| M4 | refocus/depth | **done** 2026-08-05 — ParamFloat FNumber=**0** / FocusDepth=**1** (R13: id 3 = ColorTemp, not FNumber); DepthEditor pre-setInput, DESKTOP only; UI aperture + Alt+click + depth map |

### M4 smoke (2026-08-05, L16_00026)

| Check | Result |
| --- | --- |
| `setProperty(ParamFloat,0)=4` FNumber @ p3 (auto-upgrade) | get → 4; 10432×7824 JPEG (R13 fix; early M4 wrongly used id 3) |
| DepthEditor after setInput | **throws** `Cannot set DepthEditor after setInputDataStream!` |
| DepthEditor @ p1 | **throws** `… only … Desktop profile!` |
| DepthEditor pre-setInput @ p3 | ok; `getDepthAtPoint(0.5,0.4)` → **16828.9 mm** |
| set FocusDepth=1 | get → 16828.9; re-render 10432×7824 OK |
| depth map 320×240 | 76800 valid; z 1798..1e5 mm → `.depth.ppm/.jpg` |

Полный текст: `/Users/igor/IGRS/luminat/LUMEN_PLAN.md`.


---

## D. Profiles, 52 Мп, writeImage, mono role (DONE 05.08.2026)

**Engine:** `libcp.dylib` 0.26.3 · CIAPI · Rosetta x86_64  
**Shot:** `L16_00026.lri` (forest, 28 mm, **A2 mono present**)  
**Tool:** `light libcp --profile N` → `tools/libcp-export`

### D1. RendererProfile → resolution (measured)

**Source of enum (camera smali, not guess):**  
`ProcessRequest$ProcessingProfile.mProfileNumber`:

| int | Name | Measured L0 after full fill | Time | Notes |
| --- | --- | --- | --- | --- |
| 0 | THUMBNAIL | ~520×390 (pyramid leaf) | flaky | expand search → `Invalid pyramid level` |
| **1** | **MOBILE** | **4160×3120 (~12.98 Мп)** | ~2.3 s | default quality path «13 Мп» |
| **2** | **CAMERA** | **4160×3120** | ~2.3 s | on-device JPEG tier |
| **3** | **DESKTOP** | **10432×7824 (~81.62 Мп)** | ~11 s | Lumen canvas (`ViewOutput::LUMEN_CANVAS`) |

```bash
light libcp --lri L16_00026.lri -o /tmp/out --profile 1 --format jpg   # 13 MP
light libcp --lri L16_00026.lri -o /tmp/out --profile 3 --format jpg   # desktop canvas
```

**Artifacts:**
| Profile | Path | nonzero frac | mean | std |
| --- | --- | --- | --- | --- |
| 1 | `/tmp/grok-risk0/out/d_prof1/…jpg` | 1.00 | 102.7 | 61.3 |
| 2 | `/tmp/grok-risk0/out/d_prof2/…jpg` | 1.00 | 100.3 | 62.4 |
| 3 full | `/tmp/grok-risk0/out/d_desktop_full/…jpg` | **1.00** | 103.0 | 62.3 |
| 3 early dump | `/tmp/grok-risk0/out/d_prof3/…jpg` | **0.20** | 23 | — | progressive tiles incomplete |

**Coverage gate (helper):** large canvas needs ≥55% samples + center fill before dump  
(was accepting DESKTOP at 20% → black center; fixed in `libcp_export.cpp`).

NCC mobile vs desktop↓full: **~0.993** (same scene, scale **2.5077** = 10432/4160).

#### DESKTOP_0 vs DESKTOP_1 (closed)

| Layer | Names | Meaning |
| --- | --- | --- |
| **CIAPI `Create(int)`** | 0…3 only (map_profile clamp ≤3) | **measured** table above |
| Gallery `ProcessLevel` | `THUMBNAIL`, `DEVICE_L16`, `DEVICE_FL5`, `DESKTOP_0`, `DESKTOP_1` | DB / UI processing tier |
| `LibCpRenderer$Profile` | `DEVICE_L16`, `DESKTOP_0` (+ FL5 fields) | bundles **levels** (preview/desktop/background), not extra Create() ints |

**Fact:** `DESKTOP_0` / `DESKTOP_1` are **not** separate `RendererProfile` ordinals for macOS Create.  
Both desktop gallery tiers feed **Create(3)** + different internal `render(level=…)` / ProcessLevel knobs.  
Pixel canvas for Create(3) = **10432×7824** — confirmed once full progressive fill completes.

### D3. «52 Мп» vs reality

| Claim | Number | Status |
| --- | --- | --- |
| Sensor / mobile export | 4160×3120 ≈ **13 Мп** | measured profile 1/2 |
| Marketing Lumen | **52 Мп** | product claim (manual/HUD) |
| Lumen internal canvas | 10432×7824 ≈ **81.6 Мп** | measured profile 3 + luminat `LUMEN_CANVAS` |
| 2× linear scale | 2.5077× each side | 4160→10432 |
| ~52 Мп crop candidate | 8320×6240 = 51.9 Мп | **not** required by Create(3); export crop may use view_preferences |

**Interpretation (confirmed + likely):**
1. **13 Мп** = MOBILE/CAMERA profile, full sensor reference, light/no full SR stack.  
2. **DESKTOP** allocates **oversampled fusion canvas** (~2.5×) for multi-module SR.  
3. **52 Мп** is marketing / possible crop-of-canvas, **not** a fifth Create() profile.  
4. Super-res strings: *«This profile doesn't support super-resolution!»*, *«Super-res does not support mono modules!»*, *«Superres not supported in C-mode!»*.

### D2. writeImage — closed for product

| Path | Status |
| --- | --- |
| `outputBuffer` → float/RGBA → PPM → JPG | **production** (`light libcp`, a1_export) |
| `writeImage(shared_ptr<ostream>, Point, ExportImageFormat, function)` | **ABI skip** |

**Why skip writeImage:** needs libc++ `shared_ptr` + `std::function` layout matching the **old** libcp toolchain; high crash risk for little gain — we already get full DESKTOP pixels via pyramid dump.  
GDepth/JPEG XMP strings exist in dylib (DESKTOP-only in ProcessRequest) — optional later RE.

**Product decision:** keep PPM/JPG path; do not block on writeImage.

### D4. Mono / A2 role + finger protocol

#### Software evidence (no finger required)

| Fact | How |
| --- | --- |
| A2 is AR1335 Mono in forest `.lri` | `light gather` → `mono:A2≈28mm` / `a1m` |
| Extract `A2_mono.dng` works | B3 smoke |
| Super-res **excludes** mono | libcp string `Super-res does not support mono modules!` |
| Mono **not** reference cache | `ReferenceImageCache not implemented for mono camera!` |
| No daylight color profile on A2/C6 | earlier census / open-questions |
| DESKTOP still runs on LRI **with** A2 present | profile 3 success on 00026 |

**Implication:** mono feeds **depth / luma / mono paths**, not SR detail stack. Color 13/81 MP fusion does not need A2 for chroma SR.

#### Finger protocol (optional live verification)

**Для Claude / live shoot** — 2 кадра, 28 mm, same scene, fixed tripod if possible:

1. **Control:** finger **off** modules, normal color or mono mode.  
2. **Cover:** finger **fully covers A2 only** (second module from left on top row of lenses).  
3. Pull both `.lri` + proxy JPEG.  
4. `light libcp --profile 1` both → compare center crop std/NCC and any dark patch in A2 FOV.  
5. `light extract --only-mono` on both → cover shot should show finger/black on A2 plane.

**Pass criteria:** cover shot A2 plane dark/finger; fused MOBILE JPEG detail/noise change is **small** if mono is luma-only helper (expected); large hole would mean mono is critical for that FOV (unexpected for color path).

Live pair **not** required to close D — protocol + strings + extract are enough for architecture.

### D summary table

| # | Result |
| --- | --- |
| D1 | profile 0–3 mapped; 1/2→13 Мп, 3→10432×7824 |
| D2 | export = outputBuffer path; writeImage deferred forever for product |
| D3 | 52 Мп ≠ Create profile; DESKTOP canvas 81.6 Мп; SR mono excluded |
| D4 | mono role evidence + finger protocol |

### для Claude

- **13 vs 52 Мп:** не железо «не пишет больше» — **RendererProfile**. Камера/gallery mobile = Create(1/2) ≈ 13 Мп. Desktop Lumen/libcp Create(3) = canvas **10432×7824**.  
- **DESKTOP_0/1** в DB ≠ два Create(); оба desktop tier.  
- **Mono A2/C6** не в super-res; finger-test optional (protocol above).  
- Quality offline: `light libcp --profile 1` (fast) or `--profile 3` (desktop canvas).  
- **Не** ждать writeImage / починки Lumen Qt.

---

## R13. CIAPI `ParamFloat` property bag (tone + DOF) — 2026-08-05

**Зачем.** Следующий Lumen-like слой после refocus: exposure / WB / contrast без Qt.
**Источник имён:** gallery `libnative-lib.so` `TraceRenderer` — таблица указателей на
строки (ELF `.rela.dyn` @ VA `0x2d5b0`, 20× `char*`).

### ParamFloat index → name (проверено reloc)

| id | Name | Live default (L16_00026, p1, after setInput) | set/get round-trip |
| -- | --- | --- | --- |
| **0** | **ViewDofFNumber** | **15.22** | set 4 → get 4; p1 then throws *This profile does not support depth!* |
| **1** | **ViewDofFocusDepth** | **-1** | set before depth → *Depth not available yet!* |
| **2** | **ViewExposure** | 0 | set 0.5 → 0.5 **OK** |
| **3** | **ViewColorTemperature** | **6101.9** (K) | set 5500 → 5500 **OK** |
| **4** | **ViewColorTint** | 13.3 | set 0 → 0 **OK** |
| **5** | ViewShadowBoost | 0 | OK |
| **6** | ViewHighlightBoost | 0 | OK |
| **7** | **ViewContrast** | 0 | set 0.25 → 0.25 **OK** |
| **8** | **ViewSaturation** | 0 | set −0.2 → −0.2 **OK** |
| **9** | **ViewVibrance** | 0 | OK |
| **10** | **ViewClarity** | 0 | OK |
| **11** | ViewBlacks | 0 | OK |
| **12** | ViewWhites | 0 | OK |
| **13** | **ViewSharpening** | 0 | set 0.5 → 0.5 **OK** |
| 14 | PreferredMinimumFNumber | (read) | — |
| 15 | PreferredFocusDepth | (read; after depth) | — |
| 16 | PreferredMaximumFNumber | (read) | — |
| 17 | CaptureExposureTime | capture meta | — |
| 18 | CaptureEvOffset | capture meta | — |
| 19 | MaximumInFocusBlurPixels | — | — |

**Команда probe:** x86_64 Rosetta helper against Lumen `libcp.dylib` 0.26.3  
(`setProperty`/`getProperty` ParamFloat after `Create`+`setInput` on `L16_00026.lri`).

### EditProperty → ParamFloat (`toParamFloat`, native-lib)

```
toParamFloat(editId):  // editId 1..13; 0 or >13 → -1
table: 1→3, 2→4, 3→9, 4→8, 5→2, 6→7, 7→5, 8→6, 9→13, 10→0, 11→14, 12→16, 13→1
```

`nativeSetDofDepth` hardcodes **ParamFloat(1)** (FocusDepth) + range check vs PreferredFocusDepthRange array.

### Баг M4 (исправлен 05.08)

Helper раньше ставил **FNumber = ParamFloat(3)** — это **ColorTemperature**, не aperture.
Правильно: **FNumber = 0**, FocusDepth = 1.  
DOF (FNumber/Focus) на **profile 1** → exception *does not support depth* → helper теперь
**upgrade to profile 3** + `try/catch` around setProperty.

### Tone props (product next)

Живые без depth: **Exposure, Contrast, Saturation, Vibrance, Clarity, Sharpening,
ColorTemperature, ColorTint, Shadow/Highlight/Blacks/Whites**.  
Можно вешать слайдеры в Luminat без DESKTOP.

### MonoFusion

`nm` macOS `libcp.dylib`: **нет** экспорта `lt::MonoFusion::*` (internal only).  
Путь mono product через публичный CIAPI **не найден** в этой сессии — по-прежнему strings +
gallery pipeline.

### для Claude

- Таблица ParamFloat 0..19 — канон для любого on-device gallery JNI mirror.
- Aperture/DOF ≠ tone; DOF требует depth-capable profile (DESKTOP / device depth path).
- M4 desktop: FNumber index **0**, не 3.

---

## Как дополнять этот файл

1. Новая тема — секция `## R<n>. …` и строка в оглавлении.
2. Внутри: **цель → что сделано → таблица «факт / как установлено» → открыто**.
3. Команды и пути — дословно, чтобы через месяц повторить.
4. Находки, полезные Claude, помечать блоком **«для Claude»** — готовая формулировка
   на вливание, без правки его файлов.
5. Код, smali, база знаний Claude — вне компетенции этого журнала.

