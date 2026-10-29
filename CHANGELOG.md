# Değişiklikler

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
