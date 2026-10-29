# Değişiklikler

## 0.2.1 (2026-10-29)

- Fareyle tablo ve listelerde gezinirken iz kalması düzeltildi: üzerine gelme ve basma renkleri farede anında
  değişir (0.1.x'teki gibi), yalnızca imlecin altındaki satır vurgulanır; dokunmatikte solma ve seçim geçişi kalır,
  saydama doğru solarken artık koyu griye dönmez.

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
