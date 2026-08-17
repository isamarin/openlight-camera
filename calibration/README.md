# Заводская калибровка L16

Здесь лежат снятые с камер файлы из `/lightcal` — по каталогу на экземпляр, имя каталога
это `uuid.txt` устройства.

## Почему это важнее всего остального в репозитории

Каждый L16 калибровался **поштучно**. Не модель — конкретный экземпляр, шестнадцать
модулей в нём, на заводском стенде:

- **колориметрия на каждый модуль**, три осветителя (D65, A, F11). Проверено по нашим
  кадрам: B2 в двух разных снимках даёт ровно `rg 0.491 / bg 0.667`, B4 — `0.516 / 0.711`.
  Значения не пересчитываются на кадр, они прочитаны из заводской таблицы;
- **геометрия и оптический зум** — `FactoryModuleCalibration`, `GeometricCalibration`,
  `OpticalZoomPB`, `ModuleCoverage`, смещения центров по цепочке якорей A1→B4→C5
  (строки из `/vendor/lib/liblightcalibration.so`);
- **карта битых пикселей** `hotpixel.rec`, 30 МБ — снята с этих самых матриц;
- `FlashCalibration`, `ToFCalibration` — там же, хотя ToF в кадры так и не попал.

Стенда, на котором это мерили, больше нет. Light Labs больше нет. **Восстановить эти
файлы нечем.** Если они пропадут, шестнадцать модулей перестанут быть камерой: ни
`libcp`, ни Lumen, ни наш код не соберут из них кадр — останется шестнадцать несвязанных
сенсоров с неизвестным взаимным положением.

Файл живёт в единственном экземпляре на самом устройстве, которому восемь лет. Этого
достаточно, чтобы держать копию в git.

## Что в `/lightcal`

Состав **отличается между экземплярами**, поэтому скрипт снимает раздел целиком, а не
список известных имён.

| Файл | Размер | Что это | Где встречается |
| --- | --- | --- | --- |
| `calibration.lri` | 333 048 | геометрия, зеркала, интринсики, колориметрия | везде |
| `hotpixel.rec` | 29–31 МБ | 16 карт дефектов, по одной на модуль | везде |
| `zoom_calib_v0.lri` | ~6 232 | оптический зум | везде |
| `uuid.txt` | 32 | идентификатор экземпляра | везде |
| `asic_calib_v1.lri` | 9 884 | калибровка ASIC | только поздние |
| `colorcalibmirrorhall.json` | 2 647 | датчики Холла подвижных зеркал | только ранние |
| `colorshopfloor.txt` | 7 723 | протокол цветовой калибровки, deltaE по модулям | только ранние |
| `crosstalkshopfloor.txt` | 6 737 | протокол перекрёстных наводок | только ранние |
| `colorcamparams.rec` | 35 298 | цветовые параметры, формат неизвестен | только ранние |
| `crosstalkcamparams.rec` | 263 001 | сетка перекрёстных наводок | только ранние |
| `flash.json` | 628 | замер двухцветной вспышки | только ранние |

Ранние экземпляры уехали с завода **с сырыми протоколами производственной линии**; на
поздних их вычистили, зато добавился `asic_calib_v1.lri`. То есть старая камера ценнее как
источник: она рассказывает, *как* калибровку делали, а не только чем она кончилась. Что
лежит на ревизиях, которых никто не видел, неизвестно — ещё одна причина снимать раздел
целиком.

Разобрано на сегодня: `calibration.lri` читается целиком (`lri-mono --calib`) — геометрия,
модель зеркала, интринсики, пучки фокусировки; `hotpixel.rec` читается целиком
(`lri-mono --hotpixels`). Не разобраны `colorcamparams.rec`, `crosstalkcamparams.rec`,
`asic_calib_v1.lri`, `zoom_calib_v0.lri`.

`libcp` на `calibration.lri` спотыкается: `setInput()` → `Bad LRI: No Hw Info found!` —
родной движок ждёт контейнер снимка, а не этот.

## Как снять со своего экземпляра

Только чтение, рут не нужен, `/lightcal` доступен из-под `shell`:

```sh
scripts/pull-lightcal.sh                    # всё, включая 30 МБ hotpixel.rec
scripts/pull-lightcal.sh --skip-hotpixel    # без карты битых пикселей
```

Скрипт сам находит камеру среди подключённых устройств (по наличию `/lightcal`), снимает
**весь раздел**, кладёт файлы в `calibration/<uuid>/`, сверяет каждый файл с заводским md5
там, где он есть, пишет `SHA256SUMS` на остальное и складывает `MANIFEST.txt`: серийник,
прошивка, uuid, отпечаток камеры и дата съёмки. Если камер подключено несколько, нужную
выбирает `ANDROID_SERIAL`.

USB у камеры медленный, 2–3 МБ/с: `hotpixel.rec` тянется около четверти минуты.

### Отпечаток: как потом узнать, чей это кадр

В `.lri` **нет ни серийника, ни uuid** — кадр, отделённый от камеры, безымянен. Но
заводская геометрия едет в каждом кадре, и она поштучная. `lri-mono --fingerprint` хэширует
интринсики и даёт устойчивый идентификатор:

```
lri-mono L16_00087.lri --fingerprint     # 70a9606d58c6035a
lri-mono calibration.lri --fingerprint   # 70a9606d58c6035a  — та же камера
```

Проверено на двух аппаратах: архивные кадры сошлись со своей камерой бит в бит (0.000 px
расхождения интринсик) при 159.8 px до чужой. Отпечаток пишется в `MANIFEST.txt`, так что
набор калибровки и архив снимков связываются даже спустя годы.

## Зачем нужны чужие экземпляры

Два экземпляра уже кое-что закрыли, и ответ оказался резче, чем ожидалось.

**Геометрия поштучная.** Одни и те же модули у двух камер: фокусные расходятся до 122 px,
оптические центры до 180 px, диапазоны углов зеркал до 1.3°. Взять чужую калибровку и
применить к своей — промахнуться на сотни пикселей.

**Карты дефектов не пересекаются вообще.** У одного и того же модуля двух камер общих
безнадёжных пикселей 0.19 % при случайном совпадении 0.16 %; корреляция карт 0.001. Для
контроля: два *разных* модуля одной камеры пересекаются на тех же 0.17 %. Никакой общей
структуры нет — у каждого из 32 сенсоров свои двадцать тысяч битых пикселей, и позаимствовать
их не у кого.

То есть обобщённой таблицы «для L16» не существует и существовать не может. **Данные
каждого владельца уникальны и нужны целиком.**

Чего два экземпляра не закрывают:

- у всех ли L16 панхроматические модули стоят именно на **A2 (28 мм) и C6 (150 мм)**, или
  это плавает от партии к партии;
- на скольких ранних камерах уцелели заводские протоколы — это воспроизводимый источник
  или случайность одного аппарата;
- меняли ли формат калибровки между ревизиями прошивки (состав файлов менялся точно).

Набора таких файлов не существует нигде: у Light он был на заводе и умер вместе с ней.
Собрать его можно только силами владельцев, и делается это одной командой.

---

## For L16 owners (EN)

Every Light L16 was calibrated **per unit** at the factory: per-module colour under three
illuminants, geometry and optical-zoom coverage, and a hot-pixel map measured on those
exact sensors. All of it lives in `/lightcal` on the device, and **nothing can regenerate
it** — the rig is gone, the company is gone. Without these files sixteen modules stop
being a camera.

Dumping it is read-only and needs no root:

```sh
adb pull /lightcal ./lightcal-backup
```

We have now compared two units, and the answer is blunt: **none of it transfers.** Focal
lengths differ by up to 122 px, optical centres by up to 180 px, and the hot-pixel maps
overlap at exactly the rate chance predicts (0.19 % against 0.16 % expected, correlation
0.001) — each of the 32 sensors has its own twenty thousand bad pixels and there is nobody
to borrow them from. There is no generic "L16 calibration" and there never can be.

A frame does not name its camera — there is no serial and no uuid in an `.lri`. But the
factory geometry rides along in every frame and it is per-unit, so a frame can be matched
back to the camera that shot it. Our tool hashes the intrinsics; both archived frames landed
on their own camera bit for bit, 160 px away from the other one. If you have orphaned `.lri`
files, they can still be reunited with the right calibration set.

If you can share yours, it makes a corpus that has never existed: whether the panchromatic
modules always sit at A2 (28 mm) and C6 (150 mm), how many early units still carry the
factory shop-floor logs, and whether the format changed between firmware revisions. Nothing
personal is in these files apart from the unit's own uuid.
