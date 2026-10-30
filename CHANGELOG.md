# Değişiklikler

## 2.0.1 (2026-10-30)

Galaxy S24 ve Galaxy Tab S11 Ultra'daki kullanım testlerinde bulunanların düzeltmeleri. 2.0.0 için yazılmış kod
değişmeden derlenir; düzeltmelere eşlik eden üç küçük ekleme var: `Modifier.keepsKeyboardOpen()`,
`hasHardwareKeyboard()` ve `detailSharedElement(…, shape)`. Uygulamanın fark edebileceği davranış değişiklikleri
[MIGRATION.md](MIGRATION.md#200dan-201e) içinde.

### Telefon

- **Üst çubuk:** içerik altından kayarken camlaşırken birkaç kare griye dönmüyor (arka plan `Color.Transparent`'a,
  yani saydam siyaha doğru soluyordu; artık yalnızca kendi renginin saydamlığı değişir). Ekran değişince yeni başlık
  eskisi gittikten sonra belirir; iki başlık üst üste görünmez.
- **İlk açılış:** alt çubuğun altında boş bir şerit kalmıyor. Root, kabuğun gezinme çubuğunu kendisi çizip
  çizmediğini ölçüm sırasında okuyordu; kabuk tam o sırada kaydolduğu için boşluk bir sonraki yeniden yerleşime
  (tema değişimi gibi) kadar yanlış kalıyordu.
- **Cam alt sayfa ("⋯"):** listenin her yerinden aşağı çekince kapanır (liste en üstteyken), yalnızca tutamaktan ve
  başlıktan değil; çekilmiş sayfayı yukarı itmek önce sayfayı kaldırır, sonra liste kayar. Fare tekerleği sayfayı
  oynatmaz.
- **Açık/kapalı eylemler:** `MenuEntry.Item(checked = …)` alt sayfada satırın sonunda bir anahtardır (kapalıyken boş
  duran onay sütunu yok). Öğe kendi simgesini korur, dokunmak sayfayı kapatmadan değiştirir, TalkBack anahtar olarak
  okur. Menülerde onay işareti kalır ve durumunu TalkBack'e söyler.
- **Kapak geçişi:** albümden geri dönerken kapak uçarken de köşeleri yuvarlak (önce köşeli uçup yerine inince
  yuvarlanıyordu). `Modifier.detailSharedElement(key, shape)` öğeyi yerinde de uçarken de o biçimle kırpar;
  `CoverCell` içinde hücrenin biçimi varsayılandır.
- **Izgara ve tablo dokunmatikte:** dokunuş kapağı ya da satırı açar, seçmez (açıp geri gelince "18'den 1 seçili"
  yazıyor, her kapakta işaret dairesi duruyordu); uzun basma yalnızca menüyü açar. Seçim, seçim modunda
  (`toggleOnClick`) yapılır; bir öğe seçiliyken dokunuş ekler ya da çıkarır. Farede tıklama eskisi gibi seçer, işaret
  daireleri farede birden çok kapak seçiliyken görünür.
- **Ekran klavyesi:** çubuklar kaydırmayla gizlendikten sonra klavye açılınca mini oynatıcı klavyenin arkasında
  kalmıyor, klavyenin hemen üstünde durur. Parmakla içeriği kaydırmak ya da kitin metin alanlarının dışına dokunmak
  alanın odağını alır ve klavye kapanır; alana (düğmeleri dâhil) dokunmak, alanın görünür kalmak için yaptığı kaydırma
  ve fare tekerleği klavyeyi açık bırakır. Açık bırakması gereken denetim (mesaj alanının yanındaki Gönder düğmesi,
  uygulamanın kendi metin alanı) `Modifier.keepsKeyboardOpen()` alır. Fare tıklamasını Compose kendisi yönetir
  (varsayılan olarak alanın dışına tıklamak odağı alır).
- **Oynatıcı:** tam ekran oynatıcı kapanınca alt çubuk yarım saniye gizli kalıp sonra birden belirmiyor: sayfa,
  yayının son piksel kesirlerini beklemeden mini oynatıcı göründüğü anda çekilir.
- **Kısayol ipuçları:** menülerde ve "⋯" sayfasında kısayollar (Ctrl+K gibi) yalnızca fare ya da donanım klavyesi
  varken yazılır. Yeni `hasHardwareKeyboard()`: klavye kapağı, Bluetooth ya da DeX klavyesi bağlı ve katlanmamışken
  true; örneğin ayarlardaki kısayol listesini yalnızca o zaman göstermek için.
- `StatusBanner` telefonda eylem düğmesini metnin altına koyar; metin dar bir sütuna sıkışmaz. `SwatchChoice`
  sığmayan renkleri ikinci satıra geçirir.

### Tablet ve DeX

- **Sürükle bırak:** Samsung Dosyalarım'daki gibi, işaretçi öğenin üstünde sistemin dokunup basılı tutma süresi kadar
  (Ayarlar › Erişilebilirlik, varsayılan yarım saniye) durunca başlar; daha önce kayarsa sürükleme olmaz, listede
  hızlıca gezinirken satır kapılmaz. Fare ve touchpad'de her zaman, parmakta `touch = true` ile. Satırın saydam
  kopyası yerine işaretçinin üstünde küçük bir kart taşınır: etiket ve birden çok öğede vurgu renginde sayı.
- **Canlı satırlar:** Compose'un hazır sürükleme kaynağı öğeyi bir kez alınmış bir resimle çiziyordu; sürüklenebilir
  satırlar üzerine gelmeyi göstermiyor, Dosyalar "6'dan 1 seçili" derken hiçbir satır işaretli değildi, Parçalar
  önceki çalan parçayı işaretli tutuyordu. Kit sürüklemeyi artık kendisi başlatır ve öğenin çizimine dokunmaz.
- **Bildirimler:** yeni bildirim ekrandakinin yerini hemen alır; yerine gelinen bildirimin `show` çağrısı
  `ToastResult.Dismissed` döner. Art arda çöpe atılan dosyaların her biri öncekinin on saniyesini bekliyordu.
- **Menü konumu:** pencerenin altına yakın bir düğmenin açılır menüsü düğmenin üstünde açılır, düğmeyi örtmez; sağ
  tık menüsü eskisi gibi tıklanan noktanın üstüne çıkar.

### Örnek uygulama

- File menüsünün örnekte olmayan komutları sistemin bildirimiyle kimliklerini (`open`) yazmak yerine kitin
  bildirimiyle "“Open” is not part of the sample" der (Türkçede "“Aç” örnek uygulamada yok").
- "Follow artwork colour" kendi simgesiyle; çalma listeleri dokununca şarkılarıyla açılır; sürükleme ipuçları (Çalma
  listeleri, Çöp) yalnızca fareyle görünür; Dosyalar'da Izgara/Liste düğmeleri çalışır ve dokunmatikte (tablette de)
  dokunuş dosyayı açar; Bileşenler'de uzun açıklama "Select" düğmesini dışarı itmez; Ayarlar'daki kısayol listesi
  yalnızca klavye ya da fare varken görünür.

### Testler

- Yeni testler: üst çubuğun solması (kare kare parlaklık), ilk açılıştaki boşluk, alt sayfanın her yerden çekilmesi,
  uçan kapağın köşeleri, basılı tutunca başlayan sürükleme ve canlı kalan satırlar, dokunmatikte seçim, klavyenin
  kapanması (dokunuş, kaydırma, Gönder düğmesi, fare tekerleği), klavye açıkken oynatıcı, oynatıcı kapanınca alt
  çubuk, bildirimlerin yer değiştirmesi, menünün konumu ve kısayol ipuçları.
- Robolectric'in penceresi klavye kipinde başlar; orada odak bırakılınca sistem onu hemen ilk alana geri verir.
  Klavye testleri, gerçek bir dokunuşun yaptığı gibi dokunma kipinde başlar.

### Bu sürümde olmayan

- **Sürüklerken kenar çubuğunda bekleyince ekranı açmak:** Compose bırakma hedeflerini sürükleme başlarken toplar;
  sürükleme sürerken açılan ekranın hedefleri bırakmayı almaz.
- **Uygun olmayan hedefleri soldurmak:** Compose sürüklemenin bittiğini yalnızca sürüklemeyi kabul eden hedefe
  söyler. Soldurmak için her hedefin her sürüklemeyi kabul edip yanlış türü bırakılırken geri çevirmesi gerekir;
  bu, bir düzeltme sürümüne göre büyük bir değişiklik.

## 2.0.0 (2026-10-30)

Kitin kendi çizdiği form, geri bildirim ve yapı bileşenleri; Ctrl+K komut paleti, kapak ızgarası ve sürükle
bırak; DeX'te başlık çubuğundaki menü çubuğu ve birden çok pencere; telefonda üç düğmeli gezinmenin arkasına
uzanan cam çubuklar. Android 14 ve sonrası, AGP 9 ve Kotlin 2.4 ile. Uygulamaları taşımak için
[MIGRATION.md](MIGRATION.md).

**Uyumu bozan:** `minSdk` 34 (Android 14), `compileSdk` 37, Kotlin 2.3 ve üstü, AGP 9.1.1 ve üstü. Kod tarafında
1.0 API'si değişmeden derlenir (1.0'ın bütün genel imzaları 2.0'ınkilerle karşılaştırıldı); davranış
değişiklikleri aşağıda ve rehberde.

### Platform

- AGP 9.4.1 (Kotlin'i kendisi derler), Gradle 9.8.2, Kotlin 2.4.20, Compose BOM 2026.09.00 (Compose 1.12.1),
  activity 1.13.0, navigation 2.10.2, lifecycle 2.11.0, coroutines 1.11.0, [Haze](https://github.com/chrisbanes/haze)
  2.0.1 (`haze-materials` artık gelmiyor). Örnek uygulama Android 16'yı (API 36) hedefler.
- Android 14 öncesi için yazılmış bütün dallar ve geri dönüşler silindi: öngörülü geri, başlık çubuğu insetleri,
  titreşim sabitleri ve bulanık cam doğrudan kullanılır.
- Testler Robolectric 4.17 ile API 36'da (Java 21) çalışır; yeni testler öngörülü geri hareketini (ayrıntı parmakla
  küçülür, vazgeçince geri büyür, bitince kapanır) ve etkinlik yeniden kurulunca (döndürme) kabuğun açık ekranını ve
  ekranın durumunu denetler. CI, çekme isteklerinde derleme çıktısını ve test sonuçlarını `ci/logs-pr-<numara>`
  dalına koyar; kütüphaneyi bir de JitPack gibi (`jitpack.yml`, Java 17) derler.

### Telefon: üç düğmeli gezinme

- `DesktopKitRoot` alttaki sistem gezinme çubuğunu (üç düğme ya da hareket çizgisi) kabuğa bırakır. Telefonda alt
  çubuğun camı düğmelerin arkasına kadar uzanır; çubuklar kaydırınca camın altına çekilir, camın üstünde ince bir
  çizgi belirir. Tablet ve DeX'te düğmelerin arkası ray ve kenar çubuğunun renginde. Klavye açıkken bant yok.
- Düğmeler ve durum çubuğu simgeleri temaya uyar (açık temada koyu), sistem kitin çubuklarının üstüne perde
  çizmez. Uygulama bunları kendisi yönetecekse `DesktopKitRoot(systemBarsAppearance = false)`.
- Alt sayfalar, genişleyen oynatıcı ve bildirimler düğmelerin üstünde kalır.

### Yeni bileşenler (kitin çizgisinde, Material'sız)

- **Form (K1):** `DeskTextField` (etiket, ipucu, önek simgesi, birim, temizleme, parola göster/gizle, hata ve
  açıklama satırı, tek yazı tipi, çok satır), `DeskSearchField` (Esc temizler), `NumberField` (oklarla artırır,
  aralık dışında yerelleştirilmiş uyarı, isteğe bağlı − / + düğmeleri), `OptionalNumberField` (boş = null),
  `DeskCheckbox` (açık, kapalı ve ara durum; işaret kendini çizer), `DeskSwitch`, `SwitchRow`, `CheckboxRow`.
  Klavyenin eylem tuşu sistemdeki işini yapar (Bitti klavyeyi kapatır, İleri sonraki alana geçer);
  `onImeAction` verilirse onun yerine o çalışır, tek satırda donanım Enter'ı da. `NumberField` uyarı çıkınca
  yerinden oynamaz, uyarı alanın başına doğru uzar; değer geç gelse de (Flow) yazılan korunur. Alanın içindeki
  düğmeler farede 24 dp; dokunmada 32 dp görünür, parmak onları 48 dp'lik alanda yakalar.
- **Geri bildirim (K2):** `DeskProgressBar` (belirli ve belirsiz), dört tonlu `StatusBanner` (`BannerTone`,
  başlıklı iki satırlı hâli), `CountBadge` (99+), bildirimler: `rememberToastState()` (`ToastState`),
  `show(mesaj, actionLabel, süre, ton, simge, kısayol)` sıraya girer ve sonucunu döner (`ToastResult`);
  `DesktopShell(toasts = …)` onları telefonda alt çubuğun ve oynatıcının, geniş pencerede oynatıcının üstünde
  gösterir. Parmakla yana kaydırınca kapanır, TalkBack açıkken sistemin önerdiği kadar kalır.
- **Yapı (K3):** `DeskCard` (başlıklı, tıklanabilir), `KeyValueRow` (seçilebilir değer), `DeskListRow` (kapak,
  ilerleme, seçim modu, sağ tık ve Menu tuşuyla menü), `SelectionBar`, `MenuButton`, `IconMenuButton`, `TabStrip`
  ve `DeskTab` (rozet, meşgul noktası, kapatma; orta tık kapatır; seçim çizgisi ortadan büyür; Tab kapatma
  düğmesine geçince düğme yerinde kalır, TalkBack'te "Sekmeyi kapat" eylemi).
- Her yeni bileşenin üzerine gelme, odak ve basılı durumu ekran görüntüsüyle denetlenir.
- **Kaynaklar (K7):** `appResources()` ve `pluralString(id, adet, …)` uygulama içi dil değişince yenilenir;
  başka argüman verilmezse adet metindeki `%d`'yi doldurur.
- `DeskButton(trailingIcon, role)`; devre dışı düğme dolgusu ve çerçevesiyle bütünüyle %50 saydam. Yeni simgeler:
  Minus, Warning, Error, CheckCircle, Eye, EyeOff, Undo, Command, DragHandle, NewWindow.

### Komut paleti, ızgara, sürükle bırak

- **Komut paleti (K4):** `CommandPalette(state, items = { sorgu -> … })` Ctrl+K ile açılır: ekranlar, uygulamanın
  nesneleri ve kısayol tablosu (`shortcuts.paletteItems(resources) { … }`) tek listede. Bulanık arama Türkçe harfleri
  katlar ("sarki" "Şarkı"yı bulur), eşleşen harfleri vurgular, sonuçları gruplar ve en iyi sonucu üste koyar.
  Klavye odağı arama alanında kalır: ↑/↓ seçer, Enter çalıştırır, Ctrl+Enter yeni pencerede açar, Tab grup
  süzgeçlerini gezer, Esc kapatır. Kabuğun içinde palet kapanınca odak açılmadan önceki yerine döner (alt
  sayfalar da öyle). Telefonda tam sayfa, geniş pencerede cam panel. `DesktopShell(overlays = { CommandPalette(…) })`.
- **Kapak ızgarası (K5):** `DeskGrid(items, key, onItemClick) { CoverCell(başlık, …) { kapak } }`: tablonun seçim
  kuralları iki boyutta (Ctrl/Shift ile tıklama, Shift ile oklar, Ctrl+A, Esc), sağ tık, uzun basma ve Menu tuşuyla
  menü, farede oynat ve "⋯" düğmeleri (TalkBack'te hücrenin eylemleri), dokunmada seçim işaretleri, yüklenirken
  soluk kapaklar.
- **Sürükle bırak (K6):** `Modifier.dragSource { DeskDragPayload(tür, anahtarlar, etiket) }` ve
  `Modifier.dropZone(türler, onDrop = { … })`. Farede ve touchpad'de düğme basılıyken birkaç piksel kayınca başlar
  (Compose'un fare için kendi algılayıcısı); dokunmada uzun basma menüde kalır (`touch = true` ile sürükler).
  Bırakma alanı sürükleme sürerken hafifçe, üzerine gelince seçim rengi ve vurgu çerçevesiyle belirir. Hazır yerler: `DenseTable(rowDrag)`, `DeskGrid(itemDrag)`, `DeskListRow(drag)`,
  `DeskTab(drag)`, kenar çubuğu ve ray için `DesktopShell(navDrop = NavDrop(mapOf(rota to türler)) { rota, yük -> … })`.
  Sürükleme uygulamanın öbür penceresine de geçer; başka uygulamalar yalnızca etiketi düz metin olarak alır, tür ve
  anahtarlar (dosya adları, kimlikler) uygulamanın dışına çıkmaz.

### DeX, tablet ve pencereler

- **Başlık çubuğunda menü çubuğu (M1):** kenar çubuklu düzende menü çubuğu pencerenin başlık çubuğuna yerleşir
  (Android 15+, sistem başlık öğelerinin yerini bildirdiğinde): root başlık çubuğunu saydam yapar, menüler ve arama
  alanı sistemin uygulama çipiyle pencere düğmeleri arasında durur. Menüler ve arama dokunuşlarını sistemden alır;
  çubuğun boş yeri pencereyi taşır, çift tık büyütür. Sistem öğelerin yerini bildirmezse ya da başlık çubuğunu
  içerikten ayrı tutarsa hiçbir şey değişmez.
- **Birden çok pencere (M2):** `DeskWindows.openNewWindow(activity, route)` uygulamayı DeX'te ve masaüstü
  pencerelemede ayrı bir pencerede, tablette yan yana açar; yeni pencere `ShellSpec(startRoute =
  DeskWindows.startRoute(intent))` ile o ekranda başlar. `rememberNewWindow()`, `CommonShortcuts.NewWindow` (Ctrl+N).
  Tema, vurgu ve yoğunluk pencereler arasında eşitlenir; kenar çubuğu ve panel her pencerede ayrıdır.
  Manifestte `PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI` sistemin kendi "Yeni pencere" menüsünü açar.
- **Paylaşılan öğe (M3):** `DetailTransition` içinde listede ve ayrıntıda aynı anahtarla
  `Modifier.detailSharedElement(…)` (kapak) ve `Modifier.detailSharedBounds(…)` (başlık): ayrıntı açılırken
  kapak satırdan yerine uçar, başlık akmadan büyür; telefonda parmağı izleyen yayla, genişte vurgulu eğriyle.
  Animasyonlar kapalıyken yerinde kalır. `CoverCell(titleModifier)` ızgaradaki başlığı da uçurur.
  Hiçbir şey işaretlemeyen ekran `DetailTransition(sharedElements = false)` ile ön yerleşim geçişini atlar.
- **Giriş aygıtları (M4):** klavye kapağının touchpad'i açılıp kapandığında ya da fare uyandığında yoğunluk
  hemen değişir; yalnızca açık aygıtlar sayılır, aygıtın başka değişiklikleri (yeni düzen, tuş eşlemesi)
  yoğunluğu değiştirmez. (WindowManager'ın hassas işaretçi bilgisi henüz kararlı sürümde değil; kit kendi aygıt
  takibini kullanmaya devam ediyor.)
- `ShellSpec(startRoute)` artık olmayan bir rota verilirse çökmek yerine ilk öğeyle açılır.

### Görünüm ve dokunma

- **Duvar kâğıdı rengi (M5):** `Accent.dynamic(context)` ve `rememberDeskPreferences(dynamicAccent = true)`
  duvar kâğıdından gelen rengi hazır renklerin sonuna ekler; duvar kâğıdı değişince renk de değişir.
- **Titreşim (M6):** `rememberDeskHaptics()`: `toggle`, `tick`, `threshold`, `confirm`, `reject`, `longPress`;
  yalnızca dokunmada çalışır. Anahtarlar, çipler, segment seçimi, sekmeler, alt sayfanın kapanma eşiği,
  oynatıcı ve bırakma alanları bunu kullanır.
- **Yükleniyor (M6):** `SkeletonBlock`, `SkeletonListRow`, `SkeletonList`, `DenseTable(loading = true)`,
  `DeskGrid(loading = true)`: nefes alır gibi soluklaşan yer tutucular; animasyonlar kapalıyken durgun.

### Erişilebilirlik (T4)

- Tab ve TalkBack kabukta okuma sırasıyla ilerler: üst çubuk ya da menü çubuğu, gezinme, içerik, oynatıcı
  (telefonda alt çubuk en sonda). Çizim sırası aynı: çubuklar içeriğin üstünde.
- Çipler ve telefonun alt çubuğu büyük yazıda boyca büyür, satırları kesilmez; %200'de dar yerlerdeki tek
  satırlık etiketler (alt çubuğun, kenar çubuğunun) üç noktayla biter. `DeskSlider(label, valueText)` TalkBack'e
  kaydırıcının adını ve değerini söyler. Menü çubuğunun arama alanı ve tuş kapakları açık temada çerçeveli.
- Testler: %200 yazı boyutunda hiçbir metin kutusundan taşmaz ya da sessizce kesilmez (telefon, DeX ve bileşen
  galerisi); sığmayan tek satırlık metinler üç noktayla biter, kitin bütün tek satırlık metinleri böyle (test,
  üç noktayla kısalanları çıktısında sayar). Her denetimin bir adı var; onay kutuları ve anahtarlar durumunu
  söyler; Tab DeX penceresinde menü çubuğundan kenar çubuğuna ve içeriğe gider, Shift+Tab geri döner;
  sekmelerde, komut paletinde ve alt sayfalarda odak kaybolmaz.

### Örnek uygulama (G1)

- **Bileşenler** ekranı her bileşeni her durumuyla gösterir (devre dışı, hata, seçili yan yana; üzerine gelme,
  Tab ve basma canlı); üstte tema, vurgu ve yoğunluk anahtarları. **Albümler** ızgarası, albüm sayfasına uçan
  kapak, **Parçalar** (telefonda camın altından kayar), sürüklenenleri alan **Çalma listeleri** ve **Çöp**,
  albümleri de bulan komut paleti, Ctrl+N ile yeni pencere.

### Bu sürümde olmayan

- **API dökümü (T5):** Kotlin 2.4.20'nin ABI doğrulaması, AGP 9 Kotlin'i kendisi derlerken Android kütüphanesinin
  sınıflarını görmüyor (döküm boş çıkıyor); binary-compatibility-validator da bu düzende görev açmıyor
  ([#312](https://github.com/Kotlin/binary-compatibility-validator/issues/312)). İkisinden biri desteklediğinde eklenecek.

## 1.0.0 (2026-10-29)

Telefonda buzlu cam çubuklar ve parmağı izleyen yaylar, genişleyen oynatıcı ve "⋯" sayfası; tablet ve DeX'te
ayrıntı panelli iş istasyonu düzeni. Tablet ve DeX'teki animasyon süreleri 0.2.0'daki gibi kaldı.

- **Buzlu cam:** kabuğun çubukları, sayfaları ve menüleri arkalarındaki içeriği bulanıklaştırır
  ([Haze](https://github.com/chrisbanes/haze) 1.5.4). Malzemeler açık, koyu ve OLED paletine göre ayarlandı:
  `DeskGlass.chrome(colors, kalınlık)`, `DeskGlass.raised(colors, kalınlık)`, `GlassThickness.UltraThin / Thin / Regular`.
  İnce ve normal camda yazı, arkada en kötü içerik olsa bile (koyu temada beyaz, açık temada siyah) 4.5:1 kalır;
  testle denetlenir. Cam tonu vurgu renginden çok az alır, vurguyla birlikte değişir.
  - Android 12 ve sonrası (API 31): bulanık cam. Android 11 ve öncesi: yarı saydam düz yüzey, bulanıklık yok.
    Pil tasarrufu ya da "Animasyonları kaldır" açıkken: opak yüzey, hiçbir şey kaydedilmez.
  - Yalnızca şu yüzeyler cam, çünkü her bulanık yüzey her karede bir GPU geçişi demek: telefonda alt çubuk
    ve üstündeki oynatıcı şeridi, içerik altına kaydıkça üst çubuk, alt sayfalar, menüler ve sağ tık menüleri;
    tablet ve DeX'te tablonun yapışkan başlığı, yüzen oynatıcı çubuğu ve menüler.
  - Kabuk içerik alanını cam kaynağı yapar ve `LocalGlassSource` ile verir; seçtiği kip `LocalGlassMode`.
    Kendi yüzeyin için `Modifier.glass(DeskGlass.chrome(DeskTheme.colors))`. `DesktopShell(glass = false)`
    her şeyi düz yapar.
- **Çubuklar çizimde kayar:** telefonda içerik pencerenin tamamını kaplar, çubuklar üstüne biner. Kaydırınca
  alt çubuk ve oynatıcı şeridi yalnızca katman kaydırmasıyla (`graphicsLayer`) hareket eder: kaydırılan her
  piksel artık ekranı yeniden ölçmez, yeniden oluşturmaz. Çubukların altına uzanmak isteyen ekran
  `rememberShellContentPadding()` ile çubuk yüksekliklerini alır ve listesinin iki ucuna ekler; son satır
  çubuğun üstünde durabilir, gerisi camın altından geçer. Bunu çağırmayan (0.2.0) ekranlar eskisi gibi çubukların
  arasında kalır. Yarıda bırakılan çubuğun tamamlanması, üst çubuğun rengi ve çizgisi, ekran değişince sıfırlanma
  aynı. Yeni: `Modifier.shellContentPadding()`, `LocalShellContentPadding`, `PaddingValues + PaddingValues`.
- **İki katmanlı hareket:** telefonda yer değiştiren ve parmakla sürüklenen her şey (ekran geçişindeki büyüme,
  ayrıntı geçişi, çubukların yerleşmesi, oynatıcı, sayfalar) kesilebilen ve fırlatma hızını devralan yaylarla
  hareket eder (`DeskMotion.gesture()`: `StiffnessMediumLow`, sekmesiz). Solma ve renk geçişleri kısa ve süreli
  kalır. Tablet ve DeX'te 0.2.0 süreleri aynen: 90 ms solma, 210 ms %98→%100 büyüme, DeX'te 150 ms geçiş,
  300 ms kenar çubuğu. "Animasyonları kaldır" açıkken (animatör süre ölçeği 0) her şey anında yerine geçer.
  Yeni: `MotionTier`, `DeskMotion.tier(widthClass)`, `DeskMotion.spatial(tier, süreli)`, `LocalMotionTier`,
  `rememberReducedMotion()`, `SystemMotion.reduced(context)`.
- **View animatörleri:** `rememberViewAnimators()` ve `ViewAnimators.bind(view)` çalışan `ValueAnimator`'ları
  ekran kapanınca ve yapılandırma değişince (DeX'te pencere boyutu, döndürme, tema) durdurup son değerine atlatır.
- **Genişleyen oynatıcı (telefon):** `ExpandablePlayer(state, mini = { … }, full = { … })` kabuğun `bottomBar`
  yuvasına konur. Mini oynatıcı yukarı sürüklenince ya da dokununca tam ekran oynatıcıya büyür, aşağı
  sürüklenince küçülür; bırakınca yarı yol kuralıyla ve fırlatma hızıyla bir yaya oturur. Perde ve miniden
  tama geçiş parmağı izler, alt çubuk aşağı çekilir. Öngörülü geri hareketi oynatıcıyı parmakla küçültür ve
  kapatır. `rememberExpandablePlayerState()`, `progress`, `expand()`, `collapse()`.
- **Mini oynatıcıda yana kaydırma:** başlangıç kenarına (sola) kaydırmak `onNext`, öbür yana `onPrevious`.
  Şerit parmağı izler, eşikte bir kez titreşir; eşiğe varmadan bırakılırsa yerine yaylanır.
- **"⋯" sayfası:** `MoreSheet(visible, onDismiss, groups)` ve hazır `MoreButton(groups)`: telefonda simgeli,
  gruplu eylemleri listeleyen cam alt sayfa; geniş pencerede aynı girişler menü olarak açılır. Klavyeyle
  gezilir; Esc, Geri, perdeye dokunmak ya da aşağı kaydırmak kapatır. Gruplar: `ActionGroup(başlık, eylemler)`.
  Her içerik için cam sayfa: `GlassBottomSheet`.
- **Telefonda 3 sekme + "⋯":** `ShellSpec(bottomBar = listOf(a, b, c), moreStyle = MoreStyle.Sheet)` alt
  çubukta üç sekme ve "⋯" gösterir. "⋯" sığmayan ekranları kenar çubuğundaki bölümleriyle, ardından
  `DesktopShell(moreActions = …)` ile verilen uygulama eylemlerini cam sayfada listeler. Varsayılan
  `MoreStyle.Screen` eskisi gibi "Daha fazla" ekranını açar.
- **İş istasyonu (tablet ve DeX):** `WorkstationLayout(state, main = { … }, inspector = { InspectorPane(…) })`:
  kenar çubuğu, ana bölme ve sağda ayrıntı paneli. Ayırıcı sürüklenir ya da odaktayken oklarla taşınır;
  genişlik ve açık/kapalı durumu `DeskPreferences`'ta saklanır (`rememberInspectorState(prefs)`). Panel
  kapanırken 300 ms'de kayar (`InspectorToggleButton`). `InspectorPane(tabs, selected, onSelect) { sekme -> … }`
  sekmeleri ←/→ ile değişir, içerik 150 ms'de geçer. Telefonda yalnızca ana bölme görünür.
- **Yüzen oynatıcı (tablet ve DeX):** `DesktopShell(floatingPlayer = true)` oynatıcıyı içeriğin altına yığmak
  yerine yuvarlak bir cam çubuk olarak içeriğin üstünde yüzdürür; içerik altından kayar.
- **Yoğun tablo:** başlık satırların üstünde durur ve satırlar altından geçerken buzlu cama döner. Esc seçimi
  temizler. `TableColumnsState` ile başlığın sağ tık, uzun basma ya da Menu tuşu menüsü sütun seçicidir:
  sütun göster/gizle (en az biri kalır), sola/sağa taşı, sıfırla; `rememberTableColumnsState("anahtar", prefs)`
  seçimi saklar. Sütunlar `TableColumn(id = …)` ile tanınır (varsayılan başlık).
- **İletişim kutusu:** `DeskDialog(onDismiss, title, actions = { … }) { … }` ve satır içi `DialogFrame`.
  Kapatma işaretini çerçeve `LocalDialogDismiss` ile verir: donanım klavyesi bağlıyken
  (`Configuration.KEYBOARD_QWERTY`) tıklanabilir "Esc" tuşu, yoksa 48 dp × düğmesi (`DialogDismissButton`).
- **Vurgu rengi geçişi:** tema, vurgu (ve camın tonu) değişince renkleri 150 ms'de geçirir; kapak resminden
  alınan vurgu yumuşakça değişir. `DesktopKitRoot(prefs, accent = Accent.custom("Kapak", renk))` saklanan
  vurguyu kaydetmeden geçersiz kılar. `DesktopKitTheme(colorChangeMillis = 150)`, `lerpColors(a, b, t)`.
- Fareyle tablo ve listelerde gezinirken iz kalması düzeltildi (0.2.1'deki düzeltme): üzerine gelme ve basma
  renkleri farede anında değişir; dokunmatikte solma ve seçim geçişi kalır, saydama solarken koyu griye dönmez.
- Sağ tık menüsü (0.2.2'deki düzeltme): menü açıkken başka yere tıklayınca menü bir anlığına başka yerde
  görünmüyor, açıldığı yerde solarak kapanır. Başka yere sol tık ya da dokunma yalnızca menüyü kapatır, altındaki
  satırı seçmez; sağ tık eski menüyü kapatıp tıklanan yerde yenisini açar. Esc menüyü her durumda kapatır.
- Menüler ve sağ tık menüleri kabuğun içinde buzlu cam; kabuk dışında eskisi gibi gölgeli düz yüzey.
- Bağımlılıklar: `dev.chrisbanes.haze:haze` ve `haze-materials` 1.5.4 (Apache-2.0; Compose 1.7 ve Kotlin 2.1
  üzerine kurulu son Haze serisi; 1.6 Compose 1.8 ister). `activity-compose` 1.10.1 (Haze'in istediği).
  Kotlin 2.1.0, AGP 8.7.3 ve Compose BOM 2024.12.01 aynı.
- Örnek uygulama: telefonda üç sekme ve "⋯", genişleyen oynatıcı, kapak rengine göre değişen vurgu, camın
  altından kayan müzik listesi ve satır başına "⋯"; tablet ve DeX'te dosyalar ile dört sekmeli ayrıntı paneli,
  sütun seçici ve yüzen oynatıcı; "Esc"/× iletişim kutusu.
- Kaynak kod uyumu: 0.2.0 ile yazılmış kod değişmeden derlenir. Telefonda çubuklar artık cam ve içeriğin
  üstünde; `rememberShellContentPadding()` çağırmayan ekranlar eskisi gibi yerleşir. Robolectric testlerinde
  cam yarı saydam çizilir (yazılım tuvali bulanıklığı çizemez).

## 0.2.0 (2026-10-23)

Telefonda daha çok yer, tablette biraz daha büyük yazı, Android'in kendi hareket diliyle animasyonlar.
Tablet ve DeX düzeni (yerleşim, boyutlar, renkler) değişmedi; yalnızca yazılar bir punto büyüdü.

- **Tablet ve DeX'te yazılar 1 sp büyük:** `DesktopShell(largeWindowTextStep = 1f)` (varsayılan) ray ve
  kenar çubuklu düzende her yazı stiline 1 sp ekler; satır yüksekliği oranı korunur. Telefon değişmez.
  Eskisi gibi kalsın istenirse `largeWindowTextStep = 0f`. Uygulama `pointerStep = 1f` kullanıyorsa DeX'te
  artık toplam 2 sp olur; birini kaldırın. Yeni yardımcı: `DeskType.grow(step)`.
- **Telefonda daha çok içerik:** `DesktopShell(compactSizes = true)` (varsayılan) telefonda alt çubuğu 72'den
  64 dp'ye, dokunmatik satırları 56'dan 52 dp'ye indirir (hedefler yine en az 48 dp). `DeskDimens.forCompactWindow()`.
- **Kaydırınca çekilen çubuklar (telefon):** `DesktopShell(hideBarsOnScroll = true)` (varsayılan) içerik aşağı
  kaydırılınca alt gezinme çubuğunu ve üstündeki şeridi (oynatıcı, durum) aşağı kaydırıp gizler, yukarı kaydırınca
  geri getirir; parmak yarıda kalkarsa tam açılır ya da kapanır. İçerik boşalan yeri kullanır. Üst çubuk içerik
  kaydırılınca çerçeve rengini alır ve altında ince bir çizgi belirir (Android'in üst çubuğu gibi).
- **Chip filtreler:** `DeskChip`, `ChipRow`, `FilterChips(options, selected, onToggle)`. Telefonda tek satır,
  yana kayar, kenarları yumuşakça solar; tablet ve DeX'te eskisi gibi alt satıra sarar. Seçilince onay işareti
  kayarak gelir.
- **Duyarlı tablo:** `TableColumn(hideBelow = WidthClass.Medium)` sütunu telefonda gizler, ad sütunu yer kazanır.
- **Duyarlı ayar satırı:** `SettingRow` denetim yanına sığmazsa (telefonda uzun segment seçimi) onu etiketin
  altına alır; geniş pencerede görünüm aynı.
- **Ekran geçişi ve öngörülü geri:** yeni `DetailTransition(detail, onBack, list = { … }) { ayrıntı -> … }`.
  Ayrıntı açılırken kısa bir yoldan kayarak ve belirerek gelir, liste öbür yana çekilir (Android'in paylaşılan
  eksen geçişi); kapanınca tersi. Android 14+ öngörülü geri hareketinde ayrıntı parmakla birlikte %90'a küçülür,
  köşeleri yuvarlanır, kaydırılan kenara eğilir; bırakınca kapanır, vazgeçince yerine yaylanır. Uygulamanın
  manifestine `android:enableOnBackInvokedCallback="true"` eklenmeli (örnek uygulamada var).
- **Ana ekran geçişleri:** telefon ve tablette Android'in fade-through geçişi (yeni ekran %96 / %98'den büyür,
  Material vurgulu eğrisi); DeX'te 150 ms'lik sade geçiş korunur. Öngörülü geri hareketi bu geçişi de parmakla
  oynatır.
- **Panel animasyonları:** kenar çubuğu daraltılıp genişletilirken genişliği 300 ms'de akıcı değişir, simgeler
  yerinde kalır, yazılar yer açılınca belirir. Menüler köşelerinden büyüyerek açılır, solarak kapanır; ipuçları
  yumuşakça belirir.
- **Gezinme göstergesi:** alt çubuk ve raydaki seçim hapı Android'deki gibi ortadan yaylanarak genişler; seçili
  etiket bir ton kalınlaşır.
- **Dokunma geri bildirimi:** dokunmatikte düğme, simge düğmesi, segment, chip ve gezinme simgeleri basılıyken
  hafifçe küçülür ve yaylanarak geri gelir (farede hareket yok). Üzerine gelme, basma ve seçim renkleri 150 ms'de
  geçer. Kaydırıcının tutamacı yaylanarak belirir, sürüklerken büyür.
- **Hareket belirteçleri:** `DeskMotion` (Material 3 vurgulu ve standart eğriler, süreler, yaylar). Tüm süreler
  sistemin animasyon ölçeğine uyar; "Animasyonları kaldır" açıkken hiçbir şey hareket etmez.
- Yeni yardımcılar: `animatedStateBackground`, `Modifier.pressScale`.
- Örnek uygulama: Dosyalar ekranında chip filtreler, telefonda gizlenen "Tür" sütunu, kayarak açılan ayrıntı ve
  öngörülü geri; `pointerStep` kaldırıldı (kabuk zaten büyütüyor).
- Kaynak kod uyumu: 0.1.3 ile yazılmış kod değişmeden derlenir. Görünüm varsayılanları değiştiği için ikinci sayı arttı.

## 0.1.3 (2026-10-15)

- Ekran geçişleri: `DesktopShell` yeni `transitions: ShellTransitions = ShellTransitions.Default`
  parametresini alır. Telefonda (alt çubuklu düzen) ekranlar arasında fade-through (eski ekran 90 ms'de
  solar, yenisi 210 ms'de %98'den büyüyerek belirir); ray ve kenar çubuklu düzende (tablet, DeX) hareketsiz
  150 ms'lik geçiş. Geri dönüş aynı. Süreler sistemin animasyon ölçeğine uyar. `ShellTransitions.None`
  geçişi kapatır. Alt çubuk, ray ve kenar çubuğundaki seçim ve üzerine gelme rengi 150 ms'de değişir.
- Telefonda üst çubuk: `CompactTopBar(title, onBack, backIcon, actions, showSearch)` ekrandayken üst
  çubuğun başlığını değiştirir, 48 dp geri (←) ya da kapat (×) düğmesi ve eylem simgeleri (`TopBarAction`)
  ekler. En çok üç simge görünür, gerisi "⋮" menüsüne gider. Sistemin Geri hareketi `onBack`'i çağırır.
  Başlık değişince yumuşakça geçer. Orta ve daha geniş pencerede (DeX) hiçbir etkisi yok.
- `LocalWidthClass` ve `currentWidthClass()`: ekranlar kabuğun kullandığı genişlik sınıfını okuyabilir
  (`DesktopShell` ve `DesktopKitRoot` sağlar).
- `PageTopBar` yeni isteğe bağlı parametreler alır: `onBack`, `backIcon`, `actions`.
- `PageHeader`: telefonda alt başlığı ve eylemi olmayan başlık artık boş şerit bırakmaz. Başlık ray ve
  telefon düzeninde eskisi gibi tekrar yazılmaz; kenar çubuklu düzende değişiklik yok.
- Yeni simge: `DeskIcons.ArrowLeft`. Yeni metinler: `dk_back`, `dk_close`, `dk_more_options`.
- Örnek uygulama: Dosyalar ekranında "Aç" (telefonda dokunma) dosya ayrıntısını açar; telefonda başlık,
  geri oku ve eylemler üst çubuktadır.
- Geriye uyumlu: 0.1.2 ile yazılmış kod değişmeden derlenir.

## 0.1.2 (2026-10-15)

- Yazı boyutu kancası: `DesktopKitTheme` ve `DesktopKitRoot` yeni `type: (InputMode, DeskFonts) -> DeskType`
  parametresini alır. `DeskType.pointer(fonts, step)`, `DeskType.touch(fonts, step)` ve
  `DeskType.forMode(mode, fonts, pointerStep, touchStep)` her stile (display, headline, title, body,
  bodyStrong, label, labelSmall, caption, mono) verilen sp kadar ekler; satır yüksekliği aynı oranda büyür.
  Örnek: `type = { mode, fonts -> DeskType.forMode(mode, fonts, pointerStep = 1f) }` yalnızca fare ve
  klavyede her yazıyı 1 sp büyütür.
- Üst çubuk, menü çubuğu ve arama kutusu artık sabit yükseklikte değil, en az o yükseklikte: büyük
  yazı kırpılmaz. Varsayılan boyutlarda görünüm aynı.
- Ekran klavyesi: telefonda (alt çubuklu düzen) klavye açıkken alt gezinme çubuğu gizlenir ve yer
  kaplamaz; kapanınca geri gelir. Önceden çubuk klavyenin üstüne biniyordu. İçerik yine klavyenin
  hemen üstünde biter, çünkü `DesktopKitRoot` klavye boşluğunu (`WindowInsets.safeDrawing`) bir kez
  uygular ve tüketir; ayrıca `imePadding()` eklemek gerekmez, eklense de ikinci kez boşluk bırakmaz.
  Durum `WindowInsets.isImeVisible` ve klavyenin yüksekliğiyle (`WindowInsets.ime`) okunur: pencere boşluk bilgisi almazsa çubuk hep görünür. İstenmezse `DesktopShell(hideNavigationWithKeyboard = false)`.
- `DenseTable` çoklu seçim: `selectedKeys: Set<Any>?`, `onSelectionChange: ((Set<Any>) -> Unit)?`,
  `toggleOnClick: Boolean`. Tıklama, Ctrl/Meta+tıklama, Shift+tıklama, Ctrl+A, Shift+↑/↓; sağ tık seçili
  satırda seçimi korur, başka satırda yalnızca onu seçer. Tek seçimli kullanım değişmedi.
- `ContextMenuArea` yeni `onOpen` parametresi: menü açılmadan hemen önce çağrılır.
- Örnek uygulama: fareyle 1 sp büyük yazı, Dosyalar ekranında çoklu seçim, "Seç" düğmesi ve süzgeç kutusu.
- Geriye uyumlu: 0.1.1 ile yazılmış kod değişmeden derlenir.

## 0.1.1

- `DeskFonts` başlıklar için ayrı bir aile alır: `DeskFonts(sans, mono, heading = sans)`. `display`, `headline` ve `title` bu aileyi kullanır; varsayılan değişmedi.
- `TableColumn.textStyle`: bir sütunun hücre yazı stili seçilebilir (örneğin kitap adı için serif).
- Geriye uyumlu: 0.1.0 ile yazılmış kod değişmeden derlenir.

## 0.1.0

İlk sürüm. Arayüz Nağmedar'dan çıkarılıp genel hâle getirildi.

- Tema: açık, koyu ve OLED; dört hazır vurgu rengi ve kontrastı otomatik düzelten `Accent.custom`. Yazı tipi, renk ve boyutlar uygulamaya göre değiştirilebilir.
- Dokunmatik ve fare için iki yoğunluk. Hangisinin kullanılacağı son kullanılan aygıttan anlaşılır.
- Uyarlanabilir kabuk (`DesktopShell`): ekranlar `ShellSpec` ile tanımlanır, sığmayanlar otomatik "Daha fazla" ekranında listelenir.
- Bileşenler: düğmeler, seçimler, kaydırıcı, yoğun tablo, bölünmüş panel, menüler, sağ tık menüsü, ipucu.
- Genel kısayol tablosu (`ShortcutRegistry`): menü çubuğu, sistem listesi ve ayar bölümü bu tablodan üretilir.
- Hazır ekran parçaları ve ayar bölümleri; Türkçe ve İngilizce metinler.
