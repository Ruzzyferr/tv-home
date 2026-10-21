# TV Home – Grundig Google TV'ni hızlandır, yeniden tasarla, her yerden yönet

**İlk adım (2 dk):** TV'de *Ayarlar → Sistem → Hakkında → "Android TV OS derlemesi"* satırına kumandayla **7 kez** bas. "Artık geliştiricisiniz" yazısını gör.

Bu klasörde ne var:

| Ne | Nerede | Ne yapar |
|---|---|---|
| **TV Home** (yeni ana ekran) | `dist/TvHome.apk`, kaynak `launcher/` | Reklamsız, futuristik, otomatik kategorili ana ekran (1.7 MB) |
| **Telefon fare** | TV Home'un içinde | Telefonun tarayıcısından TV'yi fare + klavye + kumanda gibi kullan (iPhone ve Android, uygulama kurmadan) |
| **tv** komutu | `tv.cmd` (Windows), `scripts/tv.sh` (Mac) | Temizlik, hızlandırma, kurulum, ekran yansıtma, geri alma |

---

## 1. TV'yi bilgisayara bağla (bir kez, ~5 dk)

1. TV: *Ayarlar → Sistem → Geliştirici seçenekleri* → **USB hata ayıklama: Açık** (varsa **Ağ hata ayıklama / ADB over network** da Açık).
2. TV: *Ayarlar → Ağ ve İnternet →* bağlı ağına gir → **IP adresini** not et (örn. `192.168.1.50`).
3. Bilgisayar, TV ile **aynı Wi-Fi'da** olsun.
4. Windows'ta bu klasörde terminal aç:
   ```
   .\tv connect 192.168.1.50
   ```
   Mac'te: `cd scripts && chmod +x tv.sh && ./tv.sh connect 192.168.1.50`
5. TV ekranında *"USB hata ayıklamaya izin verilsin mi?"* çıkar → **Bu bilgisayardan her zaman izin ver** → Tamam.

Ekranda TV modeli, Android sürümü, RAM görünürse bağlantı tamam.

> Mac'te bir kez: `brew install android-platform-tools` (Homebrew yoksa: https://brew.sh). Windows'ta adb gerekirse betik kendisi indirir.

## 2. Tek komutla her şey (~3 dk)

```
.\tv all
```

Bu sırayla şunları yapar:
- **Yedek** alır (`backups/` klasörü).
- **Gereksiz Google servislerini kapatır:** ambient ekran koruyucu, reklamlı uygulama otomatik kurucusu, film mağazası, Play Games, öneri servisi ve benzerleri. Liste: `scripts/debloat.txt`. Silmez, sadece kapatır.
- **Hızlandırır:** animasyonlar 0.5x, önbellek temizliği.
- **TV Home'u kurar** ve iki servisini açar (ana ekran yönlendirici + telefon fare).

TV'de neon saatli yeni ana ekranı görmelisin.

Ekstra hız için, bir kez ve TV boştayken: `.\tv optimize` (uygulamaları önceden derler, 5–15 dk).

## 3. Home tuşu = TV Home

Kurulumdan sonra Home tuşu Google ekranını açtığı anda TV Home öne gelir; kısa bir an Google ekranını görebilirsin.

Google ekranını hiç görmek istemiyorsan:
```
.\tv takeover
```
- Google'ın kendi ana ekranını **kapatır**; Home tuşu doğrudan TV Home'u açar. Seçim penceresi çıkarsa **TV Home → Her zaman**.
- Bilinen yan etki: kumandadaki YouTube tuşu çalışmayabilir. Düzeltmek için `.\tv apps buttonmap` ile tuşu yeniden ata.
- Geri almak: `.\tv untakeover`.

### TV açılınca ne olur?

- **TV kapalıyken fişten çekilmişse veya yeniden başlatıldıysa:** açılışta TV Home gelir. Yönlendirme servisi açılıştan sonraki ilk 3 dakika içinde TV Home'u öne getirir.
- **Kumandayla kapatıp açtıysan:** TV, kapatılırken açık olan ekrana döner. Home tuşu her zaman TV Home'u açar.
- **Açılışta HDMI veya canlı TV geliyorsa:** Grundig'in açılış kaynağı ayarı yüzündendir. *Ayarlar → Kanallar ve Girişler* (veya *Sistem → Güç*) altında **Açılış girişi / Başlangıç kaynağı** ayarını **Ana ekran / Son kullanılan** yap.
- **Hiç Google ekranı görmemek istiyorsan:** `.\tv takeover`. Bundan sonra açılışta da, Home tuşunda da yalnızca TV Home olur.
- **Sağ üstte "Telefon fare: Kapalı" görünüyorsa** (örneğin TV Home'u *Ayarlar → Uygulamalar → Zorla durdur* ile kapattıysan): `.\tv install` yaz, servisler yeniden açılır.

## 4. TV Home'u kullanmak

macOS gibi: altta **Dock**, ortada **Devam et**, üstte seçili uygulamanın bilgisi. Arka plan seçili uygulamanın rengine bürünür.

- **Dock:** favorilerin + 4 araç. **Launchpad** (tüm uygulamalar; kategoriye gelmek listeyi süzer, yeni türde bir uygulama kurunca kategorisi kendiliğinden açılır), **Kaynaklar** (PS5 gibi bağlı cihazlar adıyla, HDMI, anten, uydu), **Ara** (Spotlight; "spor" yazınca tüm spor uygulamaları), **Kontrol Merkezi** (ağ, Bluetooth, ses, telefon fare, RAM, **RAM'i boşalt**, **Ayrıntılar** aç/kapat).
- **Devam et:** gerçekten son kullandıkların ("az önce", "dün").
- **OK'a uzun bas** → menü: favorilere ekle (Dock'a girer), sola/sağa taşı, kategori değiştir, gizle, varsayılan tarayıcı yap, bilgi, kaldır.
- **Sade / Ayrıntılı:** varsayılan sade (aile için). Kontrol Merkezi → *Ayrıntılar: açık* ile üst çubukta RAM ve telefon fare adresi, uygulamada boyut ve sürüm görünür.
- **Geri** veya **Home** tuşu Dock'a döner.

### RAM

```
.\tv ram              # arka plandakileri kapatır, en fazla 12 uygulama bellekte kalır (geri: .\tv ram-reset)
.\tv debloat -Optional  # metin okuma, HbbTV, AI Chat'i de kapatır (~220 MB daha); sesli arama da kapanır
```

Android boş RAM'i önbellek olarak kullanır; yüzdenin yüksek görünmesi tek başına sorun değildir. Kasma olursa önce Kontrol Merkezi → **RAM'i boşalt**.

## 5. Telefonu fare yap (~1 dk)

1. TV Home ekranının **sağ üstünde** adres ve PIN yazar: örn. `http://192.168.1.50:8765 · PIN 4821`.
2. Telefonda (iPhone Safari veya Android Chrome) o adresi aç → PIN'i gir.
3. Tarayıcıda **Ana ekrana ekle** de; artık uygulama gibi açılır.

| Telefonda | TV'de |
|---|---|
| 1 parmak kaydır | İmleç hareket eder |
| Dokun | Tıkla |
| Basılı tut | Uzun bas |
| 2 parmak kaydır | Sayfa kaydır |
| 2 parmakla dokun | Geri |
| Alttaki yazı kutusu | TV'de seçili alana **canlı yazar** (önce TV'de alana tıkla), ↵ = Ara/Git |

Ayrıca D-pad, ses, sessiz, oynat/durdur, ileri/geri sar ve bildirim tuşları var. Hassasiyet ayarı sayfada.

## 6. Her yerden kontrol – Tailscale (~10 dk)

Evin dışından TV'yi yönetmek için. Ücretsiz, port açmaya gerek yok.

1. `.\tv apps tailscale` → TV'de Play Store açılır → **Yükle**.
2. TV'de Tailscale'i aç → giriş yap (ekrandaki kodu telefondan onayla).
3. Telefona ve bilgisayara da Tailscale kur, **aynı hesapla** giriş yap.
4. `.\tv phone` → `100.x.x.x` ile başlayan adresi gösterir. Bu adres her yerden çalışır:
   - **Telefon fare:** `http://100.x.x.x:8765`
   - **TV ekranını bilgisayarda aç ve yönet:** `.\tv connect 100.x.x.x` → `.\tv mirror -Remote`

> Modemde 5555 veya 8765 portunu **asla** dışarı açma. Uzaktan erişim için yalnızca Tailscale kullan.

## 7. Ekran yansıtma

| Nereden | Nasıl | Not |
|---|---|---|
| **Android telefon** | Hızlı ayarlar → **Yayınla / Ekranı yansıt** → TV | Dahili Google Cast; en iyi kalite |
| **iPhone / iPad** | `.\tv apps airscreen` → TV'de AirScreen'i aç → iPhone Denetim Merkezi → **Ekran Yansıtma** | Ücretsiz sürüm süre sınırlı ve reklamlı; sık kullanacaksan ücretli plan veya AirReceiver |
| **Mac** | AirScreen açıkken → Denetim Merkezi → **Ekran Yansıtma** → TV | Aynı AirScreen |
| **Windows** | Chrome / Edge → ⋮ → **Yayınla** → *Kaynaklar* → **Sekmeyi** (video için en akıcı) veya **Masaüstünü** yayınla | Ek uygulama gerekmez |
| **Ters yön: TV → PC/Mac** | `.\tv mirror` | TV ekranı bilgisayarda açılır, fare/klavyeyle kontrol edersin (scrcpy, kendisi kurulur) |

## 8. Tarayıcı

İstediğin tarayıcıyı kullanabilirsin:
- `.\tv apps tvbro` – **TV Bro** (önerilen: açık kaynak, reklam engelli, kumandaya uygun, 2 GB RAM'de hızlı)
- `.\tv apps browsehere` / `.\tv apps puffin` – alternatifler

Varsayılan yapmak için:
- TV Home'da tarayıcıya **uzun bas → Varsayılan tarayıcı yap**. Kontrol Merkezi'ndeki *İnternet* bunu açar.
- Linklerin de onda açılması için: `.\tv browser com.phlox.tvwebbrowser`. Paket adını `.\tv browser` listeler.

Telefon fare ile tarayıcıda gezinmek fare gibi çalışır: tıkla, iki parmakla kaydır, alttaki kutudan adres yaz.

### Mağazada olmayan uygulamalar (Brave, SmartTube…)

TV'nin Play Store'u yalnızca "TV için" işaretli uygulamaları gösterir. Diğerlerini resmi kaynağından kurmak için:

```
.\tv sideload brave                          # Brave tarayıcı (resmi GitHub sürümü)
.\tv sideload smarttube                      # reklamsız YouTube
.\tv sideload https://site.com/uygulama.apk  # doğrudan APK linki
.\tv sideload C:\Indirilenler\uygulama.apk   # bilgisayardaki APK
```

- Betik TV'nin işlemcisine uyan dosyayı kendisi seçer. Bu TV 32-bit (`armeabi-v7a`) olduğu için 64-bit APK'lar kurulmaz.
- İndirilenler `downloads/` klasöründe kalır; aynı sürümü tekrar indirmez.
- Kurulan uygulama TV Home'da kendi kategorisinde görünür. Brave'i linklerin varsayılan tarayıcısı yapmak için: `.\tv browser com.brave.browser`.
- Telefon sürümü uygulamalar (Brave gibi) kumandayla zor gezilir; **telefon fare** ile rahat kullanılır.
- Listeye yeni uygulama eklemek: `scripts/sideload.txt` dosyasına bir satır yaz (GitHub deposu ve dosya adı kalıbı).
- Yalnızca güvendiğin, resmi kaynaklardan APK kur.

## 9. Elle yapılacak 3 ayar (en büyük hız kazancı, ~3 dk)

1. *Ayarlar → Hesaplar ve oturum açma →* hesabın → **Yalnızca uygulamalar modu: Açık**. Öneri ve reklam akışını tamamen durdurur.
2. *Ayarlar → Sistem → Ortam modu (Ambient)* → **Kapalı** veya basit ekran koruyucu.
3. *Ayarlar → Gizlilik → Kullanım ve teşhis* → **Kapalı**.

## 10. Geri alma ve güvenlik

| İstek | Komut |
|---|---|
| Kapatılan servisleri geri aç | `.\tv restore` |
| Google ana ekranını geri getir | `.\tv untakeover` |
| TV Home'u kaldır | Önce `.\tv untakeover` (yaptıysan), sonra *Ayarlar → Uygulamalar → TV Home → Kaldır* |
| İş bitince ağ ADB'sini kapat | `.\tv lock` |
| Fabrika ayarı (son çare) | *Ayarlar → Sistem → Hakkında → Sıfırla* |

- Telefon fare sayfası PIN ile korunur ve yalnızca ev ağından veya Tailscale'den erişilir.
- Ağ ADB'si açıkken aynı ağdaki herkes TV'ye bağlanmayı deneyebilir. Misafir ağında `.\tv lock` kullan.

## 11. Özelleştirmek (geliştirici notu)

- Kod: `launcher/app/src/main/java/app/swiip/tvhome/`
  - Renkler: `ui/Theme.kt`
  - Kategori kuralları: `Categories.kt`
  - Telefon sayfası: `assets/remote.html`
- Derle ve kur:
  ```
  cd launcher
  .\gradlew.bat assembleRelease
  copy app\build\outputs\apk\release\app-release.apk ..\dist\TvHome.apk
  .\tv install
  ```
- TV olmadan test: `TvHomeTest` emülatörü (bkz. `.claude/skills/tv-home/SKILL.md`).
