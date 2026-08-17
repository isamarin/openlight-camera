#!/bin/sh
# Снять заводскую калибровку с Light L16 в каталог calibration/<uuid>/.
#
# Только чтение: adb pull, ничего не ставится и не меняется. Рута не нужно,
# /lightcal читается из-под shell (uid 2000).
#
# Зачем это делать сегодня, а не когда-нибудь. Калибровка поштучная и
# невосстановимая: у двух наших камер оптические центры расходятся до 180 px,
# а карты битых пикселей не пересекаются вообще — совпадение ровно на уровне
# случайного. Заново её не снять ни на каком оборудовании. Умершая батарея,
# сброс к заводским настройкам или свалка уносят набор навсегда.
#
# Использование:
#   scripts/pull-lightcal.sh [каталог-назначения] [--skip-hotpixel]
#
# По умолчанию каталог — calibration/ рядом с репозиторием.

set -eu

DEST="calibration"
SKIP_HOTPIXEL=0
for arg in "$@"; do
    case "$arg" in
        --skip-hotpixel) SKIP_HOTPIXEL=1 ;;
        -*) echo "неизвестный ключ: $arg" >&2; exit 2 ;;
        *) DEST="$arg" ;;
    esac
done

command -v adb >/dev/null || { echo "adb не найден" >&2; exit 1; }

# Камера может быть не единственным устройством на шине: ищем ту, у которой есть /lightcal.
# Если камер две, выбрать нужную можно через ANDROID_SERIAL.
SERIAL=""
for s in ${ANDROID_SERIAL:-$(adb devices | awk 'NR>1 && $2=="device" {print $1}')}; do
    if adb -s "$s" shell 'ls /lightcal/calibration.lri' >/dev/null 2>&1; then
        SERIAL="$s"
        break
    fi
done

if [ -z "$SERIAL" ]; then
    echo "L16 не найден: ни на одном подключённом устройстве нет /lightcal/calibration.lri" >&2
    adb devices -l >&2
    exit 1
fi

prop() { adb -s "$SERIAL" shell "getprop $1" 2>/dev/null | tr -d '\r\n'; }

UUID=$(adb -s "$SERIAL" shell 'cat /lightcal/uuid.txt' 2>/dev/null | tr -d '\r\n' || true)
[ -n "$UUID" ] || UUID="unknown-$SERIAL"

MODEL=$(prop ro.product.model)
FIRMWARE=$(prop ro.build.version.incremental)

OUT="$DEST/$UUID"
mkdir -p "$OUT"
echo "устройство $SERIAL, прошивка $FIRMWARE, uuid $UUID → $OUT"

# Снимаем раздел целиком, а не список известных имён. Состав отличается между
# экземплярами: ранние уехали с завода с протоколами производственной линии
# (colorshopfloor.txt, flash.json, colorcalibmirrorhall.json), на поздних их
# вычистили, зато появился asic_calib_v1.lri. Что лежит на экземплярах, которых
# мы не видели, неизвестно — поэтому берём всё, что есть.
# `ls -1` тулбокс на камере не понимает, поэтому берём длинный формат и последнее поле.
FILES=$(adb -s "$SERIAL" shell 'ls -la /lightcal' 2>/dev/null | tr -d '\r' |
        awk 'NF>=7 {print $NF}' | grep -v '^lost+found$' || true)
[ -n "$FILES" ] || { echo "/lightcal пуст или не читается" >&2; exit 1; }

MISSING=0
for f in $FILES; do
    if [ "$SKIP_HOTPIXEL" -eq 1 ] && [ "$f" = "hotpixel.rec" ]; then
        echo "  hotpixel.rec                 пропущен по ключу — а он невосполним, вернитесь за ним"
        continue
    fi
    printf '  %-30s' "$f"
    if adb -s "$SERIAL" pull "/lightcal/$f" "$OUT/$f" >/dev/null 2>&1; then
        echo "$(wc -c < "$OUT/$f" | tr -d ' ') Б"
    else
        echo "НЕ СНЯЛСЯ"
        MISSING=1
    fi
done

md5_of() { md5 -q "$1" 2>/dev/null || md5sum "$1" | cut -d' ' -f1; }

# Заводские контрольные суммы лежат рядом с самими файлами — сверяем все, что есть.
BAD=0
for m in "$OUT"/*.md5; do
    [ -f "$m" ] || continue
    target="${m%.md5}"
    [ -f "$target" ] || continue
    WANT=$(tr -cd '0-9a-fA-F\n' < "$m" | tr -d '\n' | cut -c1-32 | tr 'A-F' 'a-f')
    GOT=$(md5_of "$target")
    name=$(basename "$target")
    if [ "$WANT" = "$GOT" ]; then
        echo "  md5 сошёлся: $name"
    else
        echo "  MD5 НЕ СОШЁЛСЯ: $name — на устройстве $WANT, у файла $GOT" >&2
        BAD=1
    fi
done

# Отпечаток камеры по заводской геометрии. Он же считается с любого кадра, и
# только этим кадр и привязывается к своей калибровке: серийника в .lri нет.
FINGERPRINT=""
for cand in "$(dirname "$0")/lri-mono/target/release/lri-mono" "$(command -v lri-mono || true)"; do
    if [ -n "$cand" ] && [ -x "$cand" ] && [ -f "$OUT/calibration.lri" ]; then
        FINGERPRINT=$("$cand" "$OUT/calibration.lri" --fingerprint 2>/dev/null |
                      sed -n 's/.*camera fingerprint \([0-9a-f]*\).*/\1/p')
        break
    fi
done

( cd "$OUT" && rm -f SHA256SUMS &&
  { shasum -a 256 ./* 2>/dev/null || sha256sum ./*; } > .SHA256SUMS.tmp &&
  mv .SHA256SUMS.tmp SHA256SUMS )

{
    echo "Light L16 — заводская калибровка, снятая с камеры"
    echo
    echo "серийник    $SERIAL"
    echo "модель      $MODEL"
    echo "прошивка    $FIRMWARE"
    echo "uuid        $UUID"
    if [ -n "$FINGERPRINT" ]; then
        echo "отпечаток   $FINGERPRINT"
    else
        echo "отпечаток   не посчитан (нужен lri-mono --fingerprint)"
    fi
    echo "снято       $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    echo
    echo "Отпечаток — хэш заводской геометрии. Та же геометрия едет в каждом .lri,"
    echo "а серийника в кадре нет вовсе, так что отпечаток — единственный способ"
    echo "потом узнать, каким аппаратом снят кадр из архива:"
    echo "    lri-mono кадр.lri --fingerprint"
    echo
    echo "Файлы (суммы — в SHA256SUMS):"
    for f in $FILES; do
        [ -f "$OUT/$f" ] || continue
        printf '  %-30s %s Б\n' "$f" "$(wc -c < "$OUT/$f" | tr -d ' ')"
    done
} > "$OUT/MANIFEST.txt"

echo "манифест: $OUT/MANIFEST.txt"
[ "$MISSING" -eq 0 ] || { echo "часть файлов не снялась — набор неполный" >&2; exit 1; }
[ "$BAD" -eq 0 ] || { echo "контрольные суммы не сошлись — снимите ещё раз" >&2; exit 1; }
