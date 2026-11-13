# Desktop Kit tasarım dili

Kitin görünüşünü ve davranışını belirleyen değerlerin kısa özeti. Ayrıntılı kurallar, gerekçeleri
ve kontrol listeleri "Desktop Kit Tasarım Standardı" belgesindedir (telefon, tablet, DeX; 192 kural,
Unslop bölümü, kitin denetimi). Burada yazan her değer koddan alınmıştır; kod değişirse bu dosya da
değişir.

## İlkeler

- Pencereye göre düzen: ekran boyutuna, cihaza ya da yönelime değil, pencerenin genişliğine bakılır.
- Yoğunluk ayrı bir eksendir: fare ya da dokunmatik yüzey kullanılırken sıkı (Pointer), parmakla
  kullanılırken rahat (Touch). Genişlik sınıfı yoğunluğu belirlemez.
- Kitin kendi bileşenleri kullanılır; Material bileşenleri renk değiştirilerek kullanılmaz.
- Sakin ve yoğun: tek vurgu rengi, ince çizgiler, gölge yerine ton farkı. Süs yok.
- Hareket bilgi verir: bir şeyin nereden gelip nereye gittiğini. Süs için hareket yok.

## Pencere sınıfları

| Sınıf | Genişlik (dp) | Gezinme | Hareket katmanı |
|---|---|---|---|
| Compact | < 600 | alt çubuk | `Gesture` (yaylar) |
| Medium | 600–839 | ray | `Timed` (0.2.0 eğrileri) |
| Expanded | 840–1199 | ray | `Timed` |
| Large | 1200–1599 | menü çubuğu ve kenar çubuğu | `Timed` |
| ExtraLarge | ≥ 1600 | menü çubuğu ve kenar çubuğu | `Timed` |

"Animasyonları kaldır" açıkken her sınıfta `Reduced`: hiçbir şey hareket etmez, cam opak olur.
Rayda ve kenar çubuğunda yazılar 1 sp büyür (`largeWindowTextStep`).

## Boşluk ölçeği

`gapXs` 4 · `gapS` 8 · `gapM` 12 · `gapL` 16 · `gapXl` 24 dp. Sayfa kenarı (`pagePadding`) Pointer'da
20, Touch'ta 16 dp. Bileşenlerde bunların dışındaki her değer aşağıdaki istisnalardan biridir.

## Ölçüler

| Değer | Pointer | Touch | Telefon (Compact) |
|---|---|---|---|
| En küçük hedef (`minTarget`) | 24 | 48 | 48 |
| Kontrol yüksekliği | 28 | 44 | 44 |
| Satır yüksekliği | 32 | 56 | 52 |
| Simge | 16 | 22 | 22 |
| Büyük simge | 20 | 26 | 26 |
| Üst çubuk | 48 | 56 | 56 |
| Alt çubuk | 56 | 72 | 64 |
| Ray genişliği | 72 | 88 | — |
| Kenar çubuğu (açık / kapalı) | 240 / 56 | 280 / 72 | — |

## Köşeler

| Değer | Pointer | Touch |
|---|---|---|
| `radiusSmall` | 4 | 6 |
| `radiusControl` | 6 | 12 |
| `radiusCard` | 10 | 16 |
| `radiusPill` | 14 | 24 |

## Yazı

IBM Plex Sans ve IBM Plex Mono (kitle gelir). Satır yüksekliği yazı boyunun 1,25 (display), 1,3
(headline, etiketler), 1,35 (title) ve 1,4 (gövde) katı. Hiçbir yazı 11 sp'nin altında değildir.

| Stil | Pointer (sp) | Touch (sp) |
|---|---|---|
| display | 22 | 26 |
| headline | 18 | 20 |
| title | 16 | 17 |
| body / bodyStrong | 13 | 15 |
| label | 12,5 | 14 |
| labelSmall | 11,5 | 13 |
| caption | 11 | 12 |
| mono | 11 | 12 |

Sayfa başlığı ile gövde arasında en az 1,25 kat fark vardır; alt düzeyler ağırlık ve renkle ayrılır.

## Renk rolleri

Renkler her zaman `DeskTheme.colors` üzerinden alınır; bileşen içinde `Color(0x…)` yazılmaz.

| Rol | Ne için |
|---|---|
| `background` | sayfa |
| `chrome` | çubuklar, ray, kenar çubuğu |
| `surface`, `surfaceRaised` | kartlar, alanlar, menüler |
| `hover`, `pressed` | etkileşim tonları |
| `border` | ayırıcı çizgiler (dekoratif) |
| `borderStrong` | alanların, düğmelerin kenarı |
| `text`, `textSecondary`, `textTertiary` | yazının üç önemi |
| `accent`, `onAccent`, `accentText` | tek vurgu: birincil düğme, seçim göstergesi, bağlantı |
| `selection`, `onSelection` | seçili satır ve öğe |
| `focusRing` | klavye odağı (≥ 3:1) |
| `success`, `warning`, `danger` | durumlar |
| `scrim` | katmanların arkası |

Vurgu rengi uygulamanın seçimidir ya da duvar kâğıdından gelir; `ensureContrast` her vurguyu yazı
için 4,5:1'e, büyük öğeler için 3:1'e getirir. Odak ve seçim göstergeleri ekranın tek vurgusuna
sayılmaz.

## Cam

Yalnızca yüzen katmanlarda: telefonun alt çubuğu ve oynatıcı şeridi, içerik altına girince üst çubuk,
alt sayfalar, menüler, yapışkan tablo başlığı, tablette ve DeX'te yüzen oynatıcı. Camın altında gölge
yok. Pil tasarrufunda ve "Animasyonları kaldır" açıkken opak. İçinde WebView ya da video olan ekran
`PauseGlassBlur()` çağırır.

## Hareket

| Ne | Telefon | Tablet, DeX |
|---|---|---|
| Parmağın bıraktığı şey (çubuk, sayfa, şerit) | `DeskMotion.gesture()` 0,92 / 600 | zamanlı eğri |
| Dokunuşun açtığı şey (sayfa, öğe) | `DeskMotion.open()` 0,9 / 700 | zamanlı eğri |
| Tam sayfa (ayrıntı) | `DeskMotion.page()` 1,0 / 800, taşmaz | paylaşılan eksen, 300 ms |
| Seçim hapı, sekme çizgisi | `DeskMotion.spatial()` 0,9 / 700 | aynı |
| Basma | `fastSpatial()` 0,9 / 1400, taşmaz | aynı |
| Renk, saydamlık | 150 ms `Standard` | aynı |
| Sekmeler arası | 90 ms çıkış + 210 ms giriş (fade-through) | rayda aynı, kenar çubuğunda 150 ms çapraz geçiş |

Satırların sırayla gelmesi (`ListAppear`) yalnızca veri liste göründükten sonra gelince (iskeletten
sonra, aramanın sonuçları) oynar. Sonsuz döngü yalnızca iskeletin nefes alışında vardır.

## Yazılı istisnalar

Bileşenlerde ölçüler 4 dp'lik ızgaradadır (1 ve 2 dp'lik çizgiler hariç). Kitte bugün ızgaranın
dışında kalan değerler ve nedenleri aşağıda; `tools/design_check.py` bunların sayısını tutar ve yeni
birini kabul etmez. Yeni bir değer gerekiyorsa önce nedenini buraya yazın, sonra taban çizgisini
güncelleyin (`python3 tools/design_check.py --update`).

| Değer | Kaç yerde | Neden |
|---|---|---|
| 0,5; 1,5; 3 dp | 10 | çizgi kalınlığı: kenar, halka, bölücünün etkin hâli |
| 5, 6, 7, 9 dp | 32 | 24–28 dp'lik pointer kontrollerinde yazıyı ve simgeyi optik olarak ortalamak |
| 10 dp | 20 | kenar çubuğu öğesi ve sekmelerin yatay iç boşluğu: simgenin daraltılmış çubuktaki yerini korur |
| 14 dp | 12 | menü çubuğunun kenarı ve pointer simge boyutu: sistemin başlık çubuğu öğeleriyle hizalanır |
| 18, 22, 26, 30 dp | 14 | dokunmatik simge ve rozet boyları, renk örneğinin halkası, alt çubuktaki seçim hapının ve pointer sekmesinin yüksekliği |
| 150, 214 dp | 2 | sayı alanının dokunmatik genişliği (düğmeli ve düğmesiz) |

Köşelerde 7 (arama alanı), 20 (alt sayfanın üst köşeleri) ve 28 dp (öngörülü Geri sırasında sayfanın
köşesi, sistemin değeri) sabit kalır.

## Bileşen seçimi

| İş | Bileşen |
|---|---|
| Uygulama iskeleti | `DesktopKitRoot` + `DesktopShell` |
| Liste ve ayrıntı | `DetailTransition` (telefonda tam ekran, genişte paylaşılan eksen) |
| Çok sütunlu veri | `DenseTable` (sıralama, çoklu seçim, sütun seçici, boş durum) |
| Telefon listesi | `DeskListRow` |
| Kapak ızgarası | `DeskGrid` + `CoverCell` |
| Ayrıntı paneli | `WorkstationLayout` + `InspectorPane` |
| Kısa bildirim | `ToastState` + `ToastHost` (platformun `Toast`'u değil) |
| Uyarı şeridi | `StatusBanner` |
| Boş, hata, yapım aşaması | `MessageScreen` |
| Komutlar ve kısayollar | `ShortcutRegistry` + `CommandPalette` |
| Alt sayfa | `GlassBottomSheet` |
| İletişim kutusu | `DeskDialog` |
