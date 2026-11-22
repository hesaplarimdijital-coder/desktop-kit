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
| code (kod düzenleyici) | 13 | 15 |

Sayfa başlığı ile gövde arasında en az 1,25 kat fark vardır; alt düzeyler ağırlık ve renkle ayrılır.

`code` mono yazının gövde boyundadır, satır yüksekliği yazı boyunun 1,5 katıdır: kod ve betik satır satır okunur.
Düzenleyici düzyazı ağırlıklı bir dil için (`proportional`) gövdenin yazı tipini aynı boy ve aralıkla kullanır.

## Renk rolleri

Renkler her zaman `DeskTheme.colors` üzerinden alınır; bileşen içinde `Color(0x…)` yazılmaz.

| Rol | Ne için |
|---|---|
| `background` | sayfa |
| `chrome` | çubuklar, ray, kenar çubuğu |
| `surface`, `surfaceRaised` | kartlar, alanlar, menüler |
| `hover`, `pressed` | etkileşim tonları |
| `border` | ayırıcı çizgiler (dekoratif) |
| `borderStrong` | düğmelerin, panellerin, menülerin kenarı (yalnızca ayırır) |
| `controlBorder` | girdinin dinlenmedeki kenarı: metin alanı, anahtarın kapalı yolu (arka plana ve yüzeylere ≥ 3:1) |
| `text`, `textSecondary`, `textTertiary` | yazının üç önemi |
| `accent`, `onAccent`, `accentText` | tek vurgu: birincil düğme, seçim göstergesi, bağlantı |
| `selection`, `onSelection` | seçili satır ve öğe |
| `focusRing` | klavye odağı (≥ 3:1) |
| `success`, `warning`, `danger` | durumlar |
| `scrim` | katmanların arkası |
| `overlayScrim`, `onOverlay` | resmin (kapağın) üstüne çizilen düğmenin altı ve kendisi |

Kod düzenleyicinin renkleri `DeskTheme.syntax`'tadır (aşağıda "Kod düzenleyici"): tema renklerinden yapılır,
sözdizimi rolüne göre verilir.

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
sonra, aramanın sonuçları) oynar. Sonsuz döngü yalnızca iskeletin nefes alışında vardır; bir iş sürerken
dönen şey (belirsiz ilerleme çubuğu, yenileme diski) o iş bitince durur.

Yan çevrilmiş telefon (yükseklik sınıfı Compact, 480 dp altı) rayı alır ama telefonun yazısını ve
yaylarını korur.

## Kod düzenleyici

Renkler sözdizimi rolüne göre (`SyntaxRole`), temanın renklerinden yapılır; ton doygunluktan önce gelir, az renk:

| Rol | Renk | Biçim |
|---|---|---|
| Comment | `textTertiary` | eğik |
| Keyword | vurgu (`accentText`) | kalın |
| Link | vurgu (`accentText`) | |
| String | yeşil (koyu `#8FD3A8`, açık `#1B6E47`) | |
| Number, Constant | kehribar (koyu `#E2BB73`, açık `#85560F`) | |
| Name, Function | camgöbeği (koyu `#7CCBD6`, açık `#0B6672`) | |
| Type, Tag | mor (koyu `#C3A9F4`, açık `#6541B8`) | |
| Meta | mor | eğik |
| Operator, Punctuation | `textSecondary` | |
| Heading, Strong | `text` | kalın |
| Emphasis | `text` | eğik |
| Invalid | `danger` | |

Her rol ve satır numaraları sayfanın arka planında ve geçerli satırın tonunda, açık, koyu ve OLED temada, hazır ve
zorlayıcı özel vurgularla 4,5:1'e ulaşır; ulaşmayan renk `ensureContrast` ile koyulaşır ya da açılır
(`SyntaxContrastTest`). Kalın daha kalın bir çizgi, eğik bir eğimdir: aynı glif genişlikleri, yazı yer değiştirmez.
Düzenleyicinin yazısı ipuçsuz (doğrusal, piksel altı) glif genişlikleriyle ölçülür: satır kıran ölçü ile çizim her
biçimde aynıdır.

| Değer | Pointer | Touch |
|---|---|---|
| Kenarda işaret simgesi | 12 | 16 |
| Kenarın başı, işaret ile numara arası, numara ile yazı arası | 8 · 4 · 12 (`gapS` · `gapXs` · `gapM`) | aynı |
| Yazının sonu | 12 (`gapM`) | 16 (`gapL`) |
| İlk satırın üstü, son satırın altı | 8 (`gapS`) | aynı |
| Satır numarası | yazının 0,85'i, mono, en az iki basamak | aynı |
| İmleç | 2 dp, vurgu rengi | aynı |
| Tamamlama listesi | en çok 360 dp genişlik, 28 dp satır, 8 satır | 48 dp satır, 5 satır |
| Arama seçenek düğmeleri | 24 dp (simge 12) | 32 dp (simge 16) |

Tonlar: geçerli satır koyu temada `surfaceRaised`, açıkta `hover`; işaretli satır tonun %10'u, aralık süslemesi ve
arama eşleşmesi %24'ü, geçerli eşleşme %55'i; seçim vurgunun %35'i; eşleşen parantezin çerçevesi `controlBorder`, 1 dp;
alt çizgi 1 dp. Tamamlama listesi menülerin düz yüzeyi gibidir: `surfaceRaised`, `borderStrong` kenar, `radiusCard`,
12 dp gölge; vurgulanan satır `selection`. Arama çubuğu `surface` üstünde, altında ince bir çizgi.

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
| Liste ve ayrıntı | `ListDetailLayout` (tablet genişliğinden yan yana, daha darda `DetailTransition`) |
| Ekranın ana eylemi | `ShellPrimaryAction` (telefonda yüzen, rayda ve kenar çubuğunda üstte) |
| Çekerek yenileme, satırda kaydırma | `PullToRefresh`, `DeskListRow(swipeStart, swipeEnd)` (yalnızca parmakla) |
| Adres, etiket girişi | `TokenField` |
| Çok sütunlu veri | `DenseTable` (sıralama, çoklu seçim, sütun seçici, boş durum) |
| Telefon listesi | `DeskListRow` |
| Kapak ızgarası | `DeskGrid` + `CoverCell`, büyüklüğü için `rememberGridZoom` |
| Kendi listende kaydırma çubuğu | `Modifier.verticalScrollbar(state)` (tablo, ızgara, menü, alt sayfa kendiliğinden) |
| Ayrıntı paneli | `WorkstationLayout` + `InspectorPane` (dar pencerede alt sayfa) |
| Kısa bildirim | `ToastState` + `ToastHost` (platformun `Toast`'u değil) |
| Uyarı şeridi | `StatusBanner` |
| Boş, hata, yapım aşaması | `MessageScreen` |
| Komutlar ve kısayollar | `ShortcutRegistry` + `CommandPalette` |
| Alt sayfa | `GlassBottomSheet` |
| İletişim kutusu | `DeskDialog` |
| Kod ya da betik düzenleme | `CodeEditor` + `CodeSearchBar` (yakınlaştırma `rememberCodeEditorZoom`) |
