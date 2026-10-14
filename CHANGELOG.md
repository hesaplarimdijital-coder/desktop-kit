# Değişiklikler

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
  Durum `WindowInsets.isImeVisible` ile okunur. İstenmezse `DesktopShell(hideNavigationWithKeyboard = false)`.
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
