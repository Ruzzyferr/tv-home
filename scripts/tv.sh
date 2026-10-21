#!/usr/bin/env bash
# TV Home toolkit for Grundig / Google TV (macOS / Linux).  Usage: ./tv.sh <komut> [argumanlar]
# Same commands as tv.ps1 on Windows. Works with macOS's built-in bash 3.2.
set -u

SCRIPTS="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$SCRIPTS")"
IP_FILE="$SCRIPTS/.tv-ip"
PKG="app.swiip.tvhome"
SERVICES="$PKG/$PKG.HomeWatcherService:$PKG/$PKG.remote.PointerService"
STOCK_LAUNCHERS="com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith"
CMD="${1:-help}"; shift || true
OPTIONAL=0; REMOTE=0; YES=0; ARGS=()
for a in "$@"; do
  case "$a" in
    -Optional|--optional) OPTIONAL=1 ;;
    -Remote|--remote) REMOTE=1 ;;
    -Yes|--yes|-y) YES=1 ;;
    *) ARGS+=("$a") ;;
  esac
done

say()  { printf '\033[36m%s\033[0m\n' "$*"; }
ok()   { printf '\033[32m%s\033[0m\n' "$*"; }
warn() { printf '\033[33m%s\033[0m\n' "$*"; }
fail() { printf '\033[31mHATA: %s\033[0m\n' "$*"; exit 1; }

if command -v adb >/dev/null 2>&1; then ADB="adb"
elif [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then ADB="$HOME/Library/Android/sdk/platform-tools/adb"
else fail "adb yok. Kur:  brew install android-platform-tools"; fi

serial() {
  [ -f "$IP_FILE" ] || fail "Once baglan:  ./tv.sh connect <TV-IP>"
  local ip; ip="$(tr -d '[:space:]' < "$IP_FILE")"
  case "$ip" in *:*) echo "$ip" ;; *) echo "$ip:5555" ;; esac
}
sh_tv() { "$ADB" -s "$(serial)" shell "$@" | tr -d '\r'; }

ensure() {
  local s; s="$(serial)"
  if [ "$("$ADB" -s "$s" get-state 2>/dev/null)" != "device" ]; then "$ADB" connect "$s" >/dev/null; fi
  [ "$("$ADB" -s "$s" get-state 2>/dev/null)" = "device" ] || fail "TV'ye baglanilamadi ($s). TV acik mi? Ekrandaki izin sorusunda 'Her zaman' sec."
}

read_list() { [ -f "$1" ] && sed -e 's/#.*//' -e 's/[[:space:]]*$//' -e 's/^[[:space:]]*//' "$1" | grep -v '^$'; }
installed() { sh_tv pm list packages | sed 's/^package://'; }
disabled()  { sh_tv pm list packages -d | sed 's/^package://'; }

confirm() {
  [ "$YES" = 1 ] && return
  printf '%s [e/H] ' "$1"; read -r ans
  case "$ans" in e|E|evet|y) ;; *) warn "Iptal edildi."; exit 0 ;; esac
}

cmd_connect() {
  [ "${#ARGS[@]}" -gt 0 ] || fail "Kullanim: ./tv.sh connect 192.168.1.50"
  echo "${ARGS[0]}" > "$IP_FILE"
  say "Baglaniliyor: $(serial)  (TV'de izin sorusu cikarsa 'Her zaman izin ver')"
  "$ADB" connect "$(serial)"; sleep 2; ensure; ok "Bagli."; cmd_info
}

cmd_info() {
  ensure
  say "--- TV ---"
  echo "Model     : $(sh_tv getprop ro.product.manufacturer) $(sh_tv getprop ro.product.model)"
  echo "Android   : $(sh_tv getprop ro.build.version.release) (SDK $(sh_tv getprop ro.build.version.sdk))"
  echo "Yonga     : $(sh_tv getprop ro.board.platform) / $(sh_tv getprop ro.hardware)"
  echo "Ekran     : $(sh_tv wm size)"
  sh_tv "cat /proc/meminfo" | grep -E 'MemTotal|MemAvailable' | sed 's/^/Bellek    : /'
  echo "Depolama  : $(sh_tv "df -h /data" | tail -1)"
  if installed | grep -qx "$PKG"; then echo "TV Home   : kurulu"; else echo "TV Home   : kurulu degil (./tv.sh install)"; fi
}

cmd_backup() {
  ensure; mkdir -p "$ROOT/backups"; local st; st="$(date +%Y%m%d-%H%M%S)"
  sh_tv pm list packages -e > "$ROOT/backups/enabled-$st.txt"
  sh_tv pm list packages -d > "$ROOT/backups/disabled-$st.txt"
  sh_tv settings list global > "$ROOT/backups/settings-global-$st.txt"
  ok "Yedek: backups/*-$st.txt"
}

cmd_debloat() {
  ensure; cmd_backup
  local inst dis n=0; inst="$(installed)"; dis="$(disabled)"
  local list; list="$(read_list "$SCRIPTS/debloat.txt")"
  [ "$OPTIONAL" = 1 ] && list="$list
$(read_list "$SCRIPTS/debloat-optional.txt")"
  for p in $list; do
    if ! echo "$inst" | grep -qx "$p"; then echo "  -  $p (yok)"; continue; fi
    if echo "$dis" | grep -qx "$p"; then echo "  =  $p (zaten kapali)"; continue; fi
    warn "  x  $p  $(sh_tv pm disable-user --user 0 "$p")"; n=$((n+1))
  done
  ok "$n paket kapatildi. Geri almak icin: ./tv.sh restore"
}

cmd_restore() {
  ensure; local dis; dis="$(disabled)"
  for p in $(read_list "$SCRIPTS/debloat.txt") $(read_list "$SCRIPTS/debloat-optional.txt") $STOCK_LAUNCHERS; do
    echo "$dis" | grep -qx "$p" && ok "  +  $p  $(sh_tv pm enable "$p")"
  done
  ok "Kapatilan paketler geri acildi."
}

cmd_perf() {
  ensure
  for k in window_animation_scale transition_animation_scale animator_duration_scale; do sh_tv settings put global "$k" 0.5; done
  sh_tv pm trim-caches 999G >/dev/null
  ok "Animasyonlar 0.5x, onbellekler temizlendi. Ekstra: ./tv.sh optimize"
}

avail_mb() { sh_tv "cat /proc/meminfo" | awk '/MemAvailable/ {print int($2/1024)}'; }

cmd_ram() {
  ensure
  local before after
  before="$(avail_mb)"
  # Keep at most 12 cached apps in memory; sync off so Google's server config does not undo it.
  sh_tv device_config set_sync_disabled_for_tests persistent
  sh_tv device_config put activity_manager max_cached_processes 12
  sh_tv am kill-all
  for p in com.android.vending com.google.android.youtube.tv com.google.android.tts; do sh_tv am force-stop "$p"; done
  sleep 3; after="$(avail_mb)"
  ok "Bos RAM: $before MB -> $after MB (+$((after - before)) MB). Geri almak: ./tv.sh ram-reset"
}

cmd_ram_reset() {
  ensure
  sh_tv device_config delete activity_manager max_cached_processes
  sh_tv device_config set_sync_disabled_for_tests none
  ok "Bellek ayari fabrika degerine dondu."
}

cmd_optimize() { ensure; say "Uygulamalar onceden derleniyor (5-15 dk)..."; sh_tv cmd package bg-dexopt-job; ok "Bitti."; }

find_apk() {
  for p in "$ROOT/dist/TvHome.apk" "$ROOT/launcher/app/build/outputs/apk/release/app-release.apk"; do
    [ -f "$p" ] && { echo "$p"; return; }
  done
  fail "APK yok. Once derle: cd launcher && ./gradlew assembleRelease"
}

enable_services() {
  local cur; cur="$(sh_tv settings get secure enabled_accessibility_services)"
  local list=""; [ -n "$cur" ] && [ "$cur" != "null" ] && list="$cur"
  local IFS=':'
  for s in $SERVICES; do
    case ":$list:" in *":$s:"*) ;; *) list="${list:+$list:}$s" ;; esac
  done
  unset IFS
  sh_tv settings put secure enabled_accessibility_services "$list"
  sh_tv settings put secure accessibility_enabled 1
}

cmd_install() {
  ensure; local apk; apk="$(find_apk)"
  say "Kuruluyor: $apk"; "$ADB" -s "$(serial)" install -r "$apk"
  enable_services
  # Usage access: "Devam et" row, last-opened times and app sizes.
  sh_tv appops set "$PKG" GET_USAGE_STATS allow
  # Media sessions: "Devam et" shows the last YouTube video / episode with its title and artwork.
  sh_tv cmd notification allow_listener "$PKG/$PKG.MediaListener"
  # Ahead-of-time compile: without it Compose runs interpreted/JIT and every D-pad move stutters.
  say "Onceden derleniyor (akicilik icin, ~30 sn)..."; sh_tv cmd package compile -m speed -f "$PKG" >/dev/null
  sh_tv am start -n "$PKG/.MainActivity" >/dev/null
  ok "TV Home kuruldu ve acildi. Telefon fare: http://$(serial | sed 's/:[0-9]*$//'):8765"
  say "Home tusu hala Google ekranina gidiyorsa: ./tv.sh takeover"
}

cmd_takeover() {
  ensure; installed | grep -qx "$PKG" || fail "Once: ./tv.sh install"
  warn "Google TV'nin kendi ana ekrani KAPATILACAK; Home = TV Home."
  warn "Yan etki: YouTube kumanda tusu calismayabilir (Button Mapper ile duzelir). Geri: ./tv.sh untakeover"
  confirm "Devam edilsin mi?"; cmd_backup
  for p in $STOCK_LAUNCHERS; do installed | grep -qx "$p" && warn "  x  $p  $(sh_tv pm disable-user --user 0 "$p")"; done
  # Other apps may also declare HOME (e.g. Digiturk on Grundig); the role makes TV Home the default without a chooser.
  sh_tv cmd role add-role-holder --user 0 android.app.role.HOME "$PKG"
  sh_tv input keyevent KEYCODE_HOME
  ok "Bitti. Home tusu artik dogrudan TV Home'u acar."
}

cmd_untakeover() {
  ensure
  for p in $STOCK_LAUNCHERS; do ok "  +  $p  $(sh_tv pm enable "$p")"; done
  sh_tv cmd role add-role-holder --user 0 android.app.role.HOME com.google.android.apps.tv.launcherx
}

cmd_apps() {
  if [ "${#ARGS[@]}" -eq 0 ]; then
    say "Kullanim: ./tv.sh apps tailscale tvbro airscreen"
    grep -v '^#' "$SCRIPTS/apps.txt"; return
  fi
  ensure
  for key in "${ARGS[@]}"; do
    local id; id="$(grep -v '^#' "$SCRIPTS/apps.txt" | awk -F'|' -v k="$key" '{gsub(/ /,"",$1); gsub(/ /,"",$2); if ($1==k || $2==k) print $2}' | head -1)"
    [ -z "$id" ] && id="$key"
    if sh_tv am start -a android.intent.action.VIEW -d "market://details?id=$id" 2>&1 | grep -q Error; then
      warn "Play Store acilamadi: TV'de Play Store'u ac ve '$key' ara ($id)."; continue
    fi
    say "TV'de acildi: $id -> 'Yukle'ye bas, sonra Enter."
    [ "${#ARGS[@]}" -gt 1 ] && read -r _
  done
}

cmd_browser() {
  ensure
  if [ "${#ARGS[@]}" -eq 0 ]; then
    say "Kullanim: ./tv.sh browser com.phlox.tvwebbrowser"
    installed | grep -Ei 'browser|chrome|firefox|puffin|opera|brave|edge' | sed 's/^/  /'; return
  fi
  sh_tv cmd role add-role-holder --user 0 android.app.role.BROWSER "${ARGS[0]}"
  ok "Varsayilan tarayici: ${ARGS[0]}"
}

cmd_sideload() {
  local catalog="$SCRIPTS/sideload.txt"
  if [ "${#ARGS[@]}" -eq 0 ]; then
    say "Kullanim: ./tv.sh sideload brave | ./tv.sh sideload https://.../uygulama.apk | ./tv.sh sideload ~/Downloads/uygulama.apk"
    grep -v '^#' "$catalog"; return
  fi
  ensure
  local target="${ARGS[0]}" dl="$ROOT/downloads" apk
  mkdir -p "$dl"
  if [ -f "$target" ]; then
    apk="$target"
  elif echo "$target" | grep -qE '^https?://'; then
    apk="$dl/$(basename "${target%%\?*}")"; case "$apk" in *.apk) ;; *) apk="$apk.apk" ;; esac
    say "Indiriliyor: $target"; curl -fL -o "$apk" "$target" || fail "Indirilemedi."
  else
    local line repo pattern
    line="$(grep -v '^#' "$catalog" | awk -F'|' -v k="$target" '{gsub(/ /,"",$1); if ($1==k) print}' | head -1)"
    [ -n "$line" ] || fail "Listede yok: $target. Liste icin: ./tv.sh sideload"
    repo="$(echo "$line" | awk -F'|' '{gsub(/ /,"",$2); print $2}')"
    if sh_tv getprop ro.product.cpu.abilist | grep -q arm64-v8a; then
      pattern="$(echo "$line" | awk -F'|' '{gsub(/ /,"",$4); print $4}')"
    else
      pattern="$(echo "$line" | awk -F'|' '{gsub(/ /,"",$3); print $3}')"
    fi
    local url
    url="$(curl -fsSL "https://api.github.com/repos/$repo/releases/latest" | grep -o '"browser_download_url": *"[^"]*"' | sed 's/.*"\(http[^"]*\)"/\1/' \
      | while read -r u; do basename "$u" | grep -qE "$pattern" && echo "$u"; done | head -1)"
    [ -n "$url" ] || fail "$repo icinde bu TV'ye uygun APK yok ($pattern)."
    apk="$dl/$(basename "$url")"
    if [ -f "$apk" ]; then say "$(basename "$apk") zaten indirilmis."; else say "Indiriliyor: $(basename "$apk")"; curl -fL -o "$apk" "$url" || fail "Indirilemedi."; fi
  fi
  local size_kb free_kb
  size_kb=$(( $(wc -c < "$apk") / 1024 ))
  free_kb="$(sh_tv "df /data" | tail -1 | awk '{print $4}')"
  [ "$free_kb" -ge $(( size_kb * 3 )) ] || fail "TV'de yer az: bos $(( free_kb / 1024 )) MB, gereken ~$(( size_kb * 3 / 1024 )) MB."
  say "TV'ye kuruluyor ($(( size_kb / 1024 )) MB, 1-3 dk)..."
  "$ADB" -s "$(serial)" install -r "$apk" && ok "Kuruldu. TV Home'da kendi kategorisinde gorunur."
}

cmd_mirror() {
  ensure
  command -v scrcpy >/dev/null 2>&1 || { say "scrcpy kuruluyor (brew)..."; brew install scrcpy || fail "Homebrew gerekli: https://brew.sh"; }
  if [ "$REMOTE" = 1 ]; then flags="--video-bit-rate=2M --max-size=1024 --max-fps=30"; else flags="--video-bit-rate=12M --max-size=1920 --max-fps=60"; fi
  # shellcheck disable=SC2086
  scrcpy -s "$(serial)" --window-title "Grundig TV" $flags
}

cmd_phone() {
  ensure; say "Telefonda ac (PIN TV Home ekraninin sag ustunde):"
  sh_tv ip -4 -o addr show | grep -oE 'inet [0-9.]+' | awk '{print $2}' | grep -v '^127\.' | while read -r ip; do
    case "$ip" in 100.*) ok "  http://$ip:8765   (Tailscale - her yerden)" ;; *) ok "  http://$ip:8765   (ev agi)" ;; esac
  done
}

cmd_lock() { ensure; confirm "Ag ADB'si kapatilsin mi?"; sh_tv settings put global adb_enabled 0; }

cmd_help() {
  say "TV Home araclari - Grundig Google TV"
  cat <<'EOF'
  ./tv.sh connect <IP>        TV'ye baglan (bir kez)
  ./tv.sh info                Model, Android, yonga, RAM
  ./tv.sh all                 Temizlik + performans + TV Home kurulumu
  ./tv.sh debloat [-Optional] Gereksiz servisleri kapat (once yedek)
  ./tv.sh restore             Hepsini geri ac
  ./tv.sh perf | optimize     Hiz ayarlari / uygulamalari onceden derle
  ./tv.sh ram                 RAM'i bosalt + en fazla 12 arka plan uygulamasi (geri: ram-reset)
  ./tv.sh install             TV Home'u kur + servisleri ac
  ./tv.sh takeover            Google ana ekranini kapat (geri: untakeover)
  ./tv.sh apps [anahtar...]   TV'de Play Store sayfasi ac
  ./tv.sh browser <paket>     Varsayilan tarayici
  ./tv.sh sideload <ad|link|dosya>  Play Store'da olmayan uygulamayi kur (brave, smarttube)
  ./tv.sh mirror [-Remote]    TV'yi Mac'te ac ve kontrol et (scrcpy)
  ./tv.sh phone               Telefon fare adresi
  ./tv.sh lock                Ag ADB'sini kapat
EOF
}

case "$(echo "$CMD" | tr '[:upper:]' '[:lower:]')" in
  connect) cmd_connect ;; info) cmd_info ;; backup) cmd_backup ;; debloat) cmd_debloat ;;
  restore) cmd_restore ;; perf) cmd_perf ;; optimize) cmd_optimize ;; install) cmd_install ;;
  ram) cmd_ram ;; ram-reset) cmd_ram_reset ;;
  takeover) cmd_takeover ;; untakeover) cmd_untakeover ;; apps) cmd_apps ;; browser) cmd_browser ;;
  mirror) cmd_mirror ;; sideload) cmd_sideload ;; phone) cmd_phone ;; lock) cmd_lock ;;
  all) cmd_debloat; cmd_perf; cmd_install ;;
  *) cmd_help ;;
esac
