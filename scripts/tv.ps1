<#
  TV Home toolkit for Grundig / Google TV (Windows).  Usage:  tv <komut> [argumanlar]
  Run "tv help" for the command list. Every change here is reversible with ".\tv restore" / ".\tv untakeover".
#>
param(
    [Parameter(Position = 0)][string]$Command = "help",
    [Parameter(Position = 1, ValueFromRemainingArguments = $true)][string[]]$Rest = @(),
    [switch]$Optional,
    [switch]$Remote,
    [switch]$Yes
)

# "Continue": in Windows PowerShell 5.1, "Stop" turns any adb stderr line into a fatal error.
$ErrorActionPreference = "Continue"
$Root     = Split-Path -Parent $PSScriptRoot
$Scripts  = $PSScriptRoot
$IpFile   = Join-Path $Scripts ".tv-ip"
$Pkg      = "app.swiip.tvhome"
$Services = @("$Pkg/$Pkg.HomeWatcherService", "$Pkg/$Pkg.remote.PointerService")
$StockLaunchers = @("com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith")

function Say([string]$text, [string]$color = "Cyan") { Write-Host $text -ForegroundColor $color }
function Fail([string]$text) { Write-Host "HATA: $text" -ForegroundColor Red; exit 1 }

# ---------- adb ----------
function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($p in @("$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe", (Join-Path $Scripts ".tools\platform-tools\adb.exe"))) {
        if (Test-Path $p) { return $p }
    }
    Say "adb bulunamadi, Google platform-tools indiriliyor (~7 MB)..."
    $tools = Join-Path $Scripts ".tools"
    New-Item -ItemType Directory -Force $tools | Out-Null
    $zip = Join-Path $tools "platform-tools.zip"
    Invoke-WebRequest "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" -OutFile $zip -UseBasicParsing
    Expand-Archive $zip -DestinationPath $tools -Force
    Remove-Item $zip
    return (Join-Path $tools "platform-tools\adb.exe")
}
$Adb = Find-Adb

function Serial {
    if (-not (Test-Path $IpFile)) { Fail "Once baglan:  .\tv connect <TV-IP>   (IP: TV Ayarlar > Ag > Wi-Fi/Ethernet)" }
    $ip = (Get-Content $IpFile -Raw).Trim()
    if ($ip -notmatch ":") { $ip = "${ip}:5555" }
    return $ip
}

function Adb { & $Adb -s (Serial) @args }
function Sh([string]$cmd) { (& $Adb -s (Serial) shell $cmd) }

function Ensure-Connected {
    $s = Serial
    $state = (& $Adb -s $s get-state 2>$null)
    if ($state -ne "device") {
        & $Adb connect $s | Out-Null
        $state = (& $Adb -s $s get-state 2>$null)
    }
    if ($state -ne "device") { Fail "TV'ye baglanilamadi ($s). TV acik mi? Ekranda 'USB hata ayiklamaya izin ver' sorusu cikarsa 'Her zaman' sec, sonra tekrar dene." }
}

function Read-List([string]$file) {
    if (-not (Test-Path $file)) { return @() }
    Get-Content $file -Encoding UTF8 | ForEach-Object { ($_ -split "#")[0].Trim() } | Where-Object { $_ }
}

function Installed-Packages { Sh "pm list packages" | ForEach-Object { $_ -replace "^package:", "" } }
function Disabled-Packages  { Sh "pm list packages -d" | ForEach-Object { $_ -replace "^package:", "" } }

function Confirm-Step([string]$question) {
    if ($Yes) { return }
    $answer = Read-Host "$question [e/H]"
    if ($answer -notin @("e", "E", "evet", "y")) { Say "Iptal edildi." "Yellow"; exit 0 }
}

# ---------- commands ----------
function Cmd-Connect {
    $ip = $Rest | Select-Object -First 1
    if (-not $ip) { Fail "Kullanim: .\tv connect 192.168.1.50" }
    Set-Content -Path $IpFile -Value $ip -Encoding ascii
    $s = Serial
    Say "Baglaniliyor: $s  (TV ekraninda izin sorusu cikarsa 'Her zaman izin ver' sec)"
    & $Adb connect $s
    Start-Sleep -Seconds 2
    Ensure-Connected
    Say "Bagli." "Green"
    Cmd-Info
}

function Cmd-Info {
    Ensure-Connected
    $mem = Sh "cat /proc/meminfo" | Select-String "MemTotal|MemAvailable"
    Say "--- TV ---"
    "Model     : " + (Sh "getprop ro.product.manufacturer").Trim() + " " + (Sh "getprop ro.product.model").Trim()
    "Android   : " + (Sh "getprop ro.build.version.release").Trim() + " (SDK " + (Sh "getprop ro.build.version.sdk").Trim() + ")"
    "Yonga     : " + (Sh "getprop ro.board.platform").Trim() + " / " + (Sh "getprop ro.hardware").Trim()
    "Ekran     : " + ((Sh "wm size") -join " ")
    $mem | ForEach-Object { "Bellek    : " + ($_.Line -replace "\s+", " ") }
    "Depolama  : " + ((Sh "df -h /data" | Select-Object -Last 1) -replace "\s+", " ")
    "TV Home   : " + $(if ((Installed-Packages) -contains $Pkg) { "kurulu" } else { "kurulu degil (.\tv install)" })
}

function Cmd-Backup {
    Ensure-Connected
    $dir = Join-Path $Root "backups"
    New-Item -ItemType Directory -Force $dir | Out-Null
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    Sh "pm list packages -e" | Set-Content (Join-Path $dir "enabled-$stamp.txt") -Encoding utf8
    Sh "pm list packages -d" | Set-Content (Join-Path $dir "disabled-$stamp.txt") -Encoding utf8
    Sh "settings list global" | Set-Content (Join-Path $dir "settings-global-$stamp.txt") -Encoding utf8
    Say "Yedek: backups\*-$stamp.txt" "Green"
}

function Cmd-Debloat {
    Ensure-Connected
    Cmd-Backup
    $targets = @(Read-List (Join-Path $Scripts "debloat.txt"))
    if ($Optional) { $targets += Read-List (Join-Path $Scripts "debloat-optional.txt") }
    $installed = Installed-Packages
    $disabled = Disabled-Packages
    $done = 0
    foreach ($p in $targets) {
        if ($installed -notcontains $p) { Write-Host "  -  $p (yok)" -ForegroundColor DarkGray; continue }
        if ($disabled -contains $p) { Write-Host "  =  $p (zaten kapali)" -ForegroundColor DarkGray; continue }
        $out = Sh "pm disable-user --user 0 $p"
        Write-Host "  x  $p  $out" -ForegroundColor Yellow
        $done++
    }
    Say "$done paket kapatildi. Geri almak icin: .\tv restore" "Green"
}

function Cmd-Restore {
    Ensure-Connected
    $targets = @(Read-List (Join-Path $Scripts "debloat.txt")) + @(Read-List (Join-Path $Scripts "debloat-optional.txt")) + $StockLaunchers
    $disabled = Disabled-Packages
    foreach ($p in $targets) {
        if ($disabled -contains $p) { Write-Host "  +  $p  $(Sh "pm enable $p")" -ForegroundColor Green }
    }
    Say "Kapatilan paketler geri acildi." "Green"
}

function Cmd-Perf {
    Ensure-Connected
    foreach ($k in @("window_animation_scale", "transition_animation_scale", "animator_duration_scale")) {
        Sh "settings put global $k 0.5" | Out-Null
    }
    Sh "pm trim-caches 999G" | Out-Null
    Say "Animasyonlar 0.5x (daha cevik), uygulama onbellekleri temizlendi." "Green"
    Say "Ekstra hiz icin bir kez: .\tv optimize  (uygulamalari onceden derler, 5-15 dk)"
}

function Avail-Mb { [math]::Round(([long](((Sh "cat /proc/meminfo" | Select-String "MemAvailable").Line -split "\s+")[1])) / 1024) }

function Cmd-Ram {
    Ensure-Connected
    $before = Avail-Mb
    # Keep at most 12 cached apps in memory (stock is far more); sync off so Google's server config does not undo it.
    Sh "device_config set_sync_disabled_for_tests persistent" | Out-Null
    Sh "device_config put activity_manager max_cached_processes 12" | Out-Null
    Sh "am kill-all" | Out-Null
    foreach ($p in @("com.android.vending", "com.google.android.youtube.tv", "com.google.android.tts")) { Sh "am force-stop $p" | Out-Null }
    Start-Sleep -Seconds 3
    $after = Avail-Mb
    Say "Bos RAM: $before MB -> $after MB  (+$($after - $before) MB)" "Green"
    Say "Kalici: Android en fazla 12 arka plan uygulamasi tutar. Geri almak: .\tv ram-reset"
    Say "Daha fazlasi: .\tv debloat -Optional  (metin okuma, HbbTV, AI Chat; ~220 MB)"
}

function Cmd-RamReset {
    Ensure-Connected
    Sh "device_config delete activity_manager max_cached_processes" | Out-Null
    Sh "device_config set_sync_disabled_for_tests none" | Out-Null
    Say "Bellek ayari fabrika degerine dondu." "Green"
}

function Cmd-Optimize {
    Ensure-Connected
    Say "Uygulamalar onceden derleniyor (bg-dexopt). TV'yi kapatma, 5-15 dk surer..."
    Sh "cmd package bg-dexopt-job"
    Say "Bitti. Uygulamalar daha hizli acilacak." "Green"
}

function Find-Apk {
    foreach ($p in @((Join-Path $Root "dist\TvHome.apk"), (Join-Path $Root "launcher\app\build\outputs\apk\release\app-release.apk"))) {
        if (Test-Path $p) { return $p }
    }
    Fail "APK yok. Once derle:  cd launcher; .\gradlew.bat assembleRelease"
}

function Enable-Services {
    $current = (Sh "settings get secure enabled_accessibility_services" | Out-String).Trim()
    $list = @()
    if ($current -and $current -ne "null") { $list = @($current -split ":" | Where-Object { $_ }) }
    foreach ($s in $Services) { if ($list -notcontains $s) { $list += $s } }
    Sh "settings put secure enabled_accessibility_services $($list -join ':')" | Out-Null
    Sh "settings put secure accessibility_enabled 1" | Out-Null
}

function Cmd-Install {
    Ensure-Connected
    $apk = Find-Apk
    Say "Kuruluyor: $apk"
    Adb install -r $apk
    Enable-Services
    # Usage access: "Devam et" row, last-opened times and app sizes.
    Sh "appops set $Pkg GET_USAGE_STATS allow" | Out-Null
    # Media sessions: "Devam et" shows the last YouTube video / episode with its title and artwork.
    Sh "cmd notification allow_listener $Pkg/$Pkg.MediaListener" | Out-Null
    # Ahead-of-time compile: without it Compose runs interpreted/JIT and every D-pad move stutters.
    Say "Onceden derleniyor (akicilik icin, ~30 sn)..."
    Sh "cmd package compile -m speed -f $Pkg" | Out-Null
    Sh "am start -n $Pkg/.MainActivity" | Out-Null
    $ip = (Serial) -replace ":\d+$", ""
    Say "TV Home kuruldu ve acildi." "Green"
    Say "Ana ekran yonlendirici + telefon fare servisleri acildi."
    Say "Telefonda ac:  http://${ip}:8765   (PIN TV ekraninin sag ustunde)"
    Say "Home tusu hala Google ekranina gidiyorsa: .\tv takeover"
}

function Cmd-Takeover {
    Ensure-Connected
    if ((Installed-Packages) -notcontains $Pkg) { Fail "Once TV Home'u kur: .\tv install" }
    Say "Bu adim Google TV'nin kendi ana ekranini KAPATIR; Home tusu dogrudan TV Home'u acar." "Yellow"
    Say "Bilinen yan etkiler: YouTube kumanda tusu calismayabilir (Button Mapper ile duzelir)," "Yellow"
    Say "kayitli Wi-Fi nadiren unutulabilir. Geri almak: .\tv untakeover" "Yellow"
    Confirm-Step "Devam edilsin mi?"
    Cmd-Backup
    foreach ($p in $StockLaunchers) {
        if ((Installed-Packages) -contains $p) { Write-Host "  x  $p  $(Sh "pm disable-user --user 0 $p")" -ForegroundColor Yellow }
    }
    # Other apps may also declare HOME (e.g. Digiturk on Grundig); the role makes TV Home the default without a chooser.
    Sh "cmd role add-role-holder --user 0 android.app.role.HOME $Pkg" | Out-Null
    Sh "input keyevent KEYCODE_HOME" | Out-Null
    Say "Bitti. Home tusu artik dogrudan TV Home'u acar." "Green"
}

function Cmd-Untakeover {
    Ensure-Connected
    foreach ($p in $StockLaunchers) { Write-Host "  +  $p  $(Sh "pm enable $p")" -ForegroundColor Green }
    Sh "cmd role add-role-holder --user 0 android.app.role.HOME com.google.android.apps.tv.launcherx" | Out-Null
    Say "Google TV ana ekrani geri acildi." "Green"
}

function Cmd-Apps {
    $catalog = Get-Content (Join-Path $Scripts "apps.txt") -Encoding UTF8 | Where-Object { $_ -and -not $_.StartsWith("#") } | ForEach-Object {
        $f = $_ -split "\|"
        [pscustomobject]@{ Key = $f[0].Trim(); Package = $f[1].Trim(); About = $f[2].Trim() }
    }
    if (-not $Rest -or $Rest.Count -eq 0) {
        Say "Kullanim: .\tv apps tailscale tvbro airscreen   (TV'de Play Store sayfasini acar, 'Yukle'ye basarsin)"
        $catalog | Format-Table -AutoSize Key, Package, About
        return
    }
    Ensure-Connected
    foreach ($key in $Rest) {
        $app = $catalog | Where-Object { $_.Key -eq $key -or $_.Package -eq $key } | Select-Object -First 1
        $id = if ($app) { $app.Package } else { $key }
        # am prints "Error: ... unable to resolve Intent" on stderr when there is no Play Store.
        $out = (& $Adb -s (Serial) shell "am start -a android.intent.action.VIEW -d market://details?id=$id" 2>&1 | Out-String)
        if ($out -match "Error") {
            Say "Play Store acilamadi: TV'de Play Store'u ac ve '$($app.Key)' ara ($id)." "Yellow"
            continue
        }
        Say "TV'de acildi: $id  -> kumandayla 'Yukle'ye bas, sonra Enter."
        if ($Rest.Count -gt 1) { Read-Host | Out-Null }
    }
}

function Cmd-Browser {
    Ensure-Connected
    $p = $Rest | Select-Object -First 1
    if (-not $p) {
        Say "Kullanim: .\tv browser com.phlox.tvwebbrowser   (linkleri bu tarayici acar)"
        Say "TV'deki tarayicilar:"
        Installed-Packages | Where-Object { $_ -match "browser|chrome|firefox|puffin|opera|brave|edge" } | ForEach-Object { "  $_" }
        return
    }
    Sh "cmd role add-role-holder --user 0 android.app.role.BROWSER $p"
    Say "Varsayilan tarayici: $p   (TV Home'da da: uygulamaya uzun bas > 'Varsayilan tarayici yap')" "Green"
}

function Cmd-Sideload {
    $target = $Rest | Select-Object -First 1
    $catalog = Get-Content (Join-Path $Scripts "sideload.txt") -Encoding UTF8 | Where-Object { $_ -and -not $_.StartsWith("#") } | ForEach-Object {
        $f = $_ -split "\|"
        [pscustomobject]@{ Key = $f[0].Trim(); Repo = $f[1].Trim(); Arm32 = $f[2].Trim(); Arm64 = $f[3].Trim(); About = $f[4].Trim() }
    }
    if (-not $target) {
        Say "Kullanim: .\tv sideload brave | .\tv sideload https://.../uygulama.apk | .\tv sideload C:\indirilen\uygulama.apk"
        $catalog | Format-Table -AutoSize Key, Repo, About
        return
    }
    Ensure-Connected
    $dl = Join-Path $Root "downloads"
    New-Item -ItemType Directory -Force $dl | Out-Null
    $ProgressPreference = "SilentlyContinue"   # PS 5.1 downloads are ~10x slower with the progress bar
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

    if (Test-Path $target) {
        $apk = (Resolve-Path $target).Path
    } elseif ($target -match '^https?://') {
        $name = [IO.Path]::GetFileName(([uri]$target).AbsolutePath)
        if (-not $name.EndsWith(".apk")) { $name += ".apk" }
        $apk = Join-Path $dl $name
        Say "Indiriliyor: $target"
        Invoke-WebRequest $target -OutFile $apk -UseBasicParsing
    } else {
        $app = $catalog | Where-Object { $_.Key -eq $target } | Select-Object -First 1
        if (-not $app) { Fail "Listede yok: $target. Liste icin: .\tv sideload   (ya da dogrudan APK linki ver)" }
        $abis = (Sh "getprop ro.product.cpu.abilist" | Out-String)
        $pattern = if ($abis -match "arm64-v8a") { $app.Arm64 } else { $app.Arm32 }
        $rel = Invoke-RestMethod "https://api.github.com/repos/$($app.Repo)/releases/latest" -Headers @{ "User-Agent" = "tv-home" }
        $asset = $rel.assets | Where-Object { $_.name -match $pattern } | Select-Object -First 1
        if (-not $asset) { Fail "$($app.Repo) $($rel.tag_name) icinde bu TV'ye uygun APK yok ($pattern)." }
        $apk = Join-Path $dl $asset.name
        if ((Test-Path $apk) -and (Get-Item $apk).Length -eq $asset.size) {
            Say "$($asset.name) zaten indirilmis."
        } else {
            Say "$($app.Key) $($rel.tag_name): $($asset.name) ($([math]::Round($asset.size / 1MB)) MB) indiriliyor..."
            Invoke-WebRequest $asset.browser_download_url -OutFile $apk -UseBasicParsing
        }
    }

    $sizeKb = [math]::Ceiling((Get-Item $apk).Length / 1KB)
    $freeKb = [long](((Sh "df /data" | Select-Object -Last 1) -split "\s+")[3])
    if ($freeKb -lt $sizeKb * 3) { Fail "TV'de yer az: bos $([math]::Round($freeKb / 1MB, 1)) GB, gereken ~$([math]::Round($sizeKb * 3 / 1MB, 1)) GB." }
    Say "TV'ye kuruluyor ($([math]::Round($sizeKb / 1KB)) MB, Wi-Fi hizina gore 1-3 dk)..."
    Adb install -r $apk
    Say "Kuruldu. TV Home'da kendi kategorisinde gorunur." "Green"
}

function Cmd-Mirror {
    Ensure-Connected
    $scrcpy = Get-Command scrcpy -ErrorAction SilentlyContinue
    if (-not $scrcpy) {
        Say "scrcpy kuruluyor (winget)..."
        winget install --id Genymobile.scrcpy -e --accept-source-agreements --accept-package-agreements
        $env:Path = [Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [Environment]::GetEnvironmentVariable("Path", "User")
        $scrcpy = Get-Command scrcpy -ErrorAction SilentlyContinue
        if (-not $scrcpy) { Fail "scrcpy kuruldu ama bulunamadi; yeni bir terminal acip tekrar dene." }
    }
    $flags = if ($Remote) { @("--video-bit-rate=2M", "--max-size=1024", "--max-fps=30") } else { @("--video-bit-rate=12M", "--max-size=1920", "--max-fps=60") }
    Say "TV ekrani PC'de aciliyor: fare/klavye ile TV'yi kontrol edebilirsin. Kapatmak icin pencereyi kapat."
    & $scrcpy.Source -s (Serial) --window-title "Grundig TV" @flags
}

function Cmd-Phone {
    Ensure-Connected
    $ips = Sh "ip -4 -o addr show" | ForEach-Object { if ($_ -match "inet (\d+\.\d+\.\d+\.\d+)") { $Matches[1] } } | Where-Object { $_ -ne "127.0.0.1" }
    Say "Telefonda tarayicida ac (PIN TV Home ekraninin sag ustunde):"
    foreach ($ip in $ips) {
        $tag = if ($ip.StartsWith("100.")) { "(Tailscale - her yerden)" } else { "(ev agi)" }
        Say "  http://${ip}:8765   $tag" "Green"
    }
    Say "Ipucu: Safari/Chrome'da 'Ana ekrana ekle' -> uygulama gibi acilir."
}

function Cmd-Lock {
    Ensure-Connected
    Say "Ag uzerinden ADB kapatilacak (guvenlik). Tekrar acmak icin TV'de: Ayarlar > Sistem > Gelistirici > USB hata ayiklama." "Yellow"
    Confirm-Step "Kapatilsin mi?"
    Sh "settings put global adb_enabled 0" | Out-Null
}

function Cmd-All {
    Cmd-Debloat
    Cmd-Perf
    Cmd-Install
}

function Cmd-Help {
    Say "TV Home araclari - Grundig Google TV   (PowerShell: komutlarin basina .\ koy)"
    @"
  .\tv connect <IP>        TV'ye baglan (bir kez). IP: TV Ayarlar > Ag
  .\tv info                Model, Android, yonga, RAM, depolama
  .\tv all                 Hepsi bir arada: temizlik + performans + TV Home kurulumu
  .\tv debloat [-Optional] Gereksiz Google servislerini kapat (once yedek alir)
  .\tv restore             Kapatilan her seyi geri ac
  .\tv perf                Animasyon 0.5x + onbellek temizligi
  .\tv ram                 RAM'i bosalt + en fazla 12 arka plan uygulamasi (geri: .\tv ram-reset)
  .\tv optimize            Uygulamalari onceden derle (bir kez, 5-15 dk)
  .\tv install             TV Home'u kur + servisleri ac
  .\tv takeover            Google ana ekranini kapat, Home = TV Home  (geri: .\tv untakeover)
  .\tv apps [anahtar...]   TV'de Play Store sayfasi ac (tailscale, tvbro, airscreen...)
  .\tv browser <paket>     Varsayilan tarayiciyi sec
  .\tv sideload <ad|link|dosya>  Play Store'da olmayan uygulamayi kur (brave, smarttube, APK linki)
  .\tv mirror [-Remote]    TV ekranini PC'de ac ve kontrol et (scrcpy)
  .\tv phone               Telefon fare adresini goster
  .\tv lock                Ag ADB'sini kapat (is bitince guvenlik icin)
"@ | Write-Host
}

switch ($Command.ToLower()) {
    "connect"    { Cmd-Connect }
    "info"       { Cmd-Info }
    "backup"     { Cmd-Backup }
    "debloat"    { Cmd-Debloat }
    "restore"    { Cmd-Restore }
    "perf"       { Cmd-Perf }
    "optimize"   { Cmd-Optimize }
    "ram"        { Cmd-Ram }
    "ram-reset"  { Cmd-RamReset }
    "install"    { Cmd-Install }
    "takeover"   { Cmd-Takeover }
    "untakeover" { Cmd-Untakeover }
    "apps"       { Cmd-Apps }
    "browser"    { Cmd-Browser }
    "sideload"   { Cmd-Sideload }
    "mirror"     { Cmd-Mirror }
    "phone"      { Cmd-Phone }
    "lock"       { Cmd-Lock }
    "all"        { Cmd-All }
    default      { Cmd-Help }
}
