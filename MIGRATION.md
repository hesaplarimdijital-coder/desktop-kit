# 2.3.0'a geçiş

Bu rehber kiti kullanan uygulamaları en güncel sürüme, 2.3.0'a taşımak için. 0.1.3'ten gelenler baştan
başlar; adımlardan sonra [görünümde fark edecekleri](#013ten-gelince-görünümde-fark-edecekleriniz) ayrıca
yazılı. 1.0.0'dan geçenler 1. adımdan sonra doğrudan [1.0'dan 2.0'a](#10dan-20a) bölümüne atlayabilir. 2.0.0,
2.1.0 ya da 2.2.0 kullananlar yalnızca [2.0.0'dan 2.1.0'a](#200dan-210a), [2.1.0'dan 2.2.0'a](#210dan-220a) ve
[2.2.0'dan 2.3.0'a](#220dan-230a) bölümlerine bakar.

Sıra şöyle: önce derleme ortamı (kitin kendisi aynı adımları `7845eb5` gönderisinde attı, örnek olarak
oraya bakılabilir), sonra uygulamanın kendi yazdığı bileşenler kitinkilerle değiştirilir, en son yeni
özellikler açılır. Her adımdan sonra uygulama derlenip çalışır durumda kalır.

## 1. Derleme ortamı

| | 0.1.3 uygulamaları | 2.3.0 |
| --- | --- | --- |
| Android Gradle eklentisi (AGP) | 8.7.3 | 9.4.1 (en az 9.1.1) |
| Gradle | 8.11.1 | 9.8.2 |
| Kotlin | 2.1.0 | 2.4.20 (en az 2.3: Haze 2'nin meta verisi bunu ister) |
| Compose BOM | 2024.12.01 | 2026.09.00 (Compose 1.12; kitle birlikte gelir) |
| `compileSdk` | 35 | 37 |
| `minSdk` | 26 / 30 | **34** (Android 14) |
| `targetSdk` | 35 | 36 önerilir |
| activity-compose | 1.9.3 | 1.13.0 |
| navigation-compose | 2.8.4 | 2.10.2 (kitle birlikte gelir) |
| JDK | 17 | 17 (Robolectric testleri için 21) |

`minSdk` 34 olunca Galaxy Tab S11 Ultra ve Galaxy S24 (Android 16) etkilenmez; Android 13 ve öncesi
cihazlar uygulamayı kuramaz. Uygulamalar Play'de olmadığı için başka bir etkisi yok.

Adımlar:

1. `gradle/wrapper/gradle-wrapper.properties`: `distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.2-bin.zip`
2. `gradle/libs.versions.toml`: yukarıdaki sürümler ve `desktopKit = "2.3.0"`. En kolayı kitin kendi
   `gradle/libs.versions.toml` dosyasındaki sürümleri almak.
3. AGP 9 Kotlin'i kendisi derler (yerleşik Kotlin). Modüllerden `alias(libs.plugins.kotlin.android)`
   satırını sil, `kotlin.compose` kalsın. Kökteki `build.gradle.kts` dosyasında eklentiyi uygulamadan
   bildirmek Kotlin sürümünü katalogdakine sabitler:

   ```kotlin
   plugins {
       alias(libs.plugins.android.application) apply false
       alias(libs.plugins.kotlin.android) apply false   // yalnızca Kotlin sürümünü sabitler
       alias(libs.plugins.kotlin.compose) apply false
       alias(libs.plugins.kotlin.jvm) apply false       // saf Kotlin/JVM modülü varsa
   }
   ```

   Saf Kotlin/JVM modülü (Android'siz bir `core` gibi) `kotlin.jvm` eklentisini kullanmaya devam eder; kökteki
   satır onun da katalogdaki Kotlin'i kullanmasını sağlar.
4. `app/build.gradle.kts`: `kotlinOptions { jvmTarget = "17" }` bloğunu sil (`compileOptions` içindeki Java 17
   yeter); `compileSdk = 37`, `minSdk = 34`, `targetSdk = 36`.
5. `gradle.properties`: `android.useAndroidX` ve `android.nonTransitiveRClass` satırları AGP 9'da varsayılan,
   silinebilir.
6. Android 14 öncesi için yazılmış `Build.VERSION.SDK_INT` dallarını temizle; kit de kendininkileri sildi.
7. `settings.gradle.kts` içinde `org.gradle.toolchains.foojay-resolver-convention` varsa en az 1.0.0 yap. Eski
   sürümler Gradle 9'un kaldırdığı `JvmVendorSpec.IBM_SEMERU` alanına başvurur; araç zinciri bir JDK indirmek
   zorunda kaldığında derleme `NoSuchFieldError` ile düşer. Walkman Sync 0.9.0'dan 1.0.0'a geçip derlendi.

Uygulama doğrudan Haze kullanıyorsa: kit artık Haze 2.0.1 getiriyor. `LocalGlassSource` bir Haze 2
`HazeState`'idir; kendi cam yüzeyin için en kolayı `Modifier.glass(DeskGlass.chrome(DeskTheme.colors))`.

## 2. Uygulamanın kendi bileşenleri → kitinkiler

Uygulamaların `ui/UiKit.kt` ve `ui/compact/CompactKit.kt` dosyalarında Material'a dayanan ya da kite
eksik olduğu için yazılmış bileşenlerin hepsinin kitte bir karşılığı var. Kitinkiler kitin çizgisinde
(kendi kenarlıkları, renkleri, şekilleri) çizilir; Material bileşeni kullanmaz. Değiştirdikçe uygulamadaki
tanımı sil, `import desktopkit.components.…` ekle.

| Uygulamada | Kitte | Not |
| --- | --- | --- |
| `appResources()` | `desktopkit.appResources()` | Aynı. Çoğul metinler için `pluralString(id, adet, …)`. |
| `DeskCheckbox(checked, onCheckedChange)` | `DeskCheckbox(checked, onCheckedChange)` | Aynı imza; Material onay kutusu yerine kitin kutusu (dokunmada 22 dp, farede 16 dp). |
| `DeskTriCheckbox(state: Int, onClick)` | `DeskCheckbox(state: ToggleableState, onClick)` | 0 → `Off`, 1 → `Indeterminate`, 2 → `On`. |
| `DeskSwitch(checked, onCheckedChange)` | `DeskSwitch(checked, onCheckedChange)` | Aynı. Dokunmada kısa bir titreşim verir. |
| `SwitchRow(title, checked, onCheckedChange, subtitle)` | `SwitchRow(...)` | Aynı imza. Satırın tamamı tıklanır. |
| `DeskProgressBar(fraction, modifier, color, height)` | `DeskProgressBar(...)` | Aynı; `null` belirsiz çubuk. Ek: `trackColor`, `contentDescription`. |
| `DeskDialog(title, text, confirmLabel, dismissLabel, onConfirm, onDismiss)` | `DeskDialog(onDismiss, title, actions = { … }) { Text(text) }` | Aşağıdaki örneğe bak. |
| `DeskFormDialog(title, …) { içerik }` | `DeskDialog(onDismiss, title, actions = { … }) { içerik }` | `confirmEnabled` → onay düğmesinin `enabled`'ı. |
| `DeskTextField(value, onValueChange, hint, leading, trailing, …)` | `DeskTextField(..., trailingContent = …)` | `trailing` yuvası `trailingContent` oldu. Üst küme: `label`, `suffix`, `clearable`, `password`, `error`, `supportingText`, `mono`, `onImeAction` (verilmezse klavyenin tuşu sistemdeki işini yapar). |
| `FilterTextField(value, onValueChange, hint)` | `DeskSearchField(value, onValueChange, hint = hint)` | Büyüteç, temizleme düğmesi; Esc temizler. |
| `NumberField(value, onValue, min, max)` | `NumberField(value, onValueChange, min = …, max = …)` | Aralık dışı değer için altında yerelleştirilmiş uyarı (alan yerinde kalır); `steppers = true` ile − / + düğmeleri. Değer bir Flow'dan geç gelse de yazılan korunur. |
| `OptionalNumberField(value, onValue, hint, reset)` | `OptionalNumberField(value, onValueChange, hint = …)` | `reset` gerekmez: dışarıdan gelen değer yazılanın yerine geçer. |
| `MenuButton(text, entries, icon, enabled)` | `MenuButton(...)` | Aşağı ok sona kendiliğinden çizilir; `icon = DeskIcons.ChevronDown` verdiysen kaldır, yoksa iki ok çıkar. |
| `IconMenuButton(icon, label, entries)` | `IconMenuButton(...)` | Aynı. |
| `SurfaceCard { }` | `DeskCard { }` | `title` ve `onClick` (tıklanabilir kart) eklendi. Varsayılan iç boşluk yatayda `gapL + 4 dp`, dikeyde `gapS`; öğeler arasında boşluk yok. Eski kartın görünümü (her yanda `gapL`, öğeler arası `gapS`): `DeskCard(contentPadding = PaddingValues(gapL), verticalArrangement = Arrangement.spacedBy(gapS))`, boyutlar `DeskTheme.dimens` içinden. |
| `KeyValueRow(label, value, mono, valueColor, maxLines)` | `KeyValueRow(...)` | Ek: `labelWidth`, `selectable` (değer seçilip kopyalanır). Kendi dikey `gapXs` boşluğu var; mono değer gövde boyunda; etiket farede 128 dp, dokunmada 112 dp; `maxLines` varsayılanı 3. Aralıklı bir Column'da art arda gelen satırları aralıksız bir Column'a al, yoksa aralık iki kat olur. |
| `BannerTone`, `StatusBanner(tone, text, actionLabel, onAction, closeLabel, onClose)` | Aynı adlar | Ek: `title` (iki satırlı şerit), `icon`. Tonların simgeleri kitin (`Info`, `Warning`, `Error`, `CheckCircle`); uygulamanın kendi simgesi için `icon =`. Uygulamanın kendi `BannerTone`'unu sil. |
| Onay kutusu + metin satırı (`Row { DeskCheckbox(…); Text(…) }`) | `CheckboxRow(title, checked, onCheckedChange)` | Satırın tamamı tıklanır, TalkBack adını ve durumunu söyler; ara durum için `ToggleableState`'li hâli. |
| `CountBadge(count, color, onColor)` | `CountBadge(...)` | Ek: `max` (99+). |
| `ToggleChip(label, selected, onClick, icon)` | `DeskChip(text, selected, onClick, icon = …)` | Ek: `count`. Birden fazlası için `FilterChips`. |
| `TabChip(label, selected, onClick, icon, badge, closeLabel, onClose, busy)` | `DeskTab(...)` | Aynı parametreler, ama adlarıyla ver: kitte `modifier` dördüncü sırada, `busy` de yer değiştirdi. `closeLabel` kitin metnine düşer. Orta tık kapatır. |
| `ScrollRow { }` | `TabStrip { }` (sekmeler), `ChipRow(wrap = false) { }` (çipler) | |
| `Modifier.pagePadding()` | `Modifier.padding(horizontal = DeskTheme.dimens.pagePadding)` | İstersen uygulamada kalabilir. |
| `CompactListRow(...)` | `DeskListRow(...)` | Aynı parametreler (`openMenuOnTap` dahil). Ek: `leadingContent` (kapak), `trailingContent`, `divider`, `enabled`, `drag`; sağ tık ve Menu tuşu menüyü açar. |
| `CompactSelectionBar(countText, closeLabel, onClose, actions)` | `SelectionBar(text, onClose, closeLabel = …, actions = …)` | |
| `CompactCaption`, `isCompactWidth()` | — | Kitte yok, uygulamada kalır. |
| Soulseek+'ın `ToastHost(appViewModel)` | `rememberToastState()` (`ToastState`) + `DesktopShell(toasts = …)` | Aşağıdaki örneğe bak. |

İletişim kutusu:

```kotlin
// önce
DeskDialog(title = "Silinsin mi?", text = "3 dosya silinecek.", confirmLabel = "Sil", dismissLabel = "Vazgeç",
    onConfirm = ::sil, onDismiss = ::kapat)

// sonra
DeskDialog(onDismiss = ::kapat, title = "Silinsin mi?", actions = {
    DeskButton("Vazgeç", ::kapat)
    DeskButton("Sil", ::sil, primary = true)
}) {
    Text("3 dosya silinecek.", style = DeskTheme.type.body, color = DeskTheme.colors.textSecondary)
}
```

Soulseek+'ın mesajları (`AppViewModel.message`) kitin bildirimlerine:

```kotlin
val toasts = rememberToastState()
LaunchedEffect(Unit) {
    appViewModel.message.filterNotNull().collect { message ->
        appViewModel.dismissMessage()
        toasts.show(message.text)   // yenisi ekrandakinin yerini alır; geri alma için actionLabel = "Geri al"
    }
}
DesktopShell(…, toasts = toasts) { … }
```

Bildirim telefonda alt çubuğun ve oynatıcının, tablet ve DeX'te oynatıcının üstünde çıkar; parmakla yana
kaydırınca kapanır, TalkBack açıkken sistemin önerdiği süre kadar kalır.

## 0.1.3'ten gelince görünümde fark edecekleriniz

Kod değişmeden derlenir ama 0.2.0 ve 1.0.0 bazı varsayılanları değiştirdi. Ayrıntılar CHANGELOG'da; eskisine
dönmenin bir anahtarı olanlarda anahtar yanında yazılı.

### Telefon

- **Daha sıkı boyutlar:** alt çubuk 72 yerine 64 dp, dokunmatik satırlar 56 yerine 52 dp (hedefler yine en az
  48 dp). Eskisi: `DesktopShell(compactSizes = false)`.
- **Kaydırınca çekilen çubuklar:** içerik aşağı kaydırılınca alt gezinme çubuğu ve üstündeki şerit (oynatıcı,
  durum) aşağı kayıp gizlenir, yukarı kaydırınca geri gelir. Kapatmak: `DesktopShell(hideBarsOnScroll = false)`.
- **Cam çubuklar:** içerik pencerenin tamamını kaplar; alt çubuk ve oynatıcı şeridi buzlu cam olarak üstüne
  biner, üst çubuk içerik altına kayınca cama döner ve altında ince bir çizgi belirir. Listesini camın altına
  uzatmak isteyen ekran `rememberShellContentPadding()` ile çubuk yüksekliklerini alır; bunu çağırmayan ekran
  eskisi gibi çubukların arasında kalır. Hepsini düz yapmak: `DesktopShell(glass = false)`.
- **Ekran geçişleri:** fade-through 0.1.3'te de vardı; yeni ekran artık %98 yerine %96'dan büyüyerek belirir.
  Yer değiştiren ve parmakla sürüklenen her şey (çubuklar, oynatıcı, sayfalar, ayrıntı) parmağın hızını devralan
  yaylarla hareket eder, öngörülü geri hareketi geçişi parmakla oynatır. Eskisine dönmenin anahtarı yok;
  `DesktopShell(transitions = ShellTransitions.None)` geçişi tamamen kapatır.
- **Çipler** tek satırda yana kayar; düğmeler ve simgeler basılıyken hafifçe küçülür.

### Tablet ve DeX

- **Yazılar 1 sp büyük:** ray ve kenar çubuklu düzende her yazı 1 sp büyür; yerleşim, boyut ve renkler aynı.
  Eskisi: `DesktopShell(largeWindowTextStep = 0f)`. Uygulama `pointerStep = 1f` kullanıyorsa DeX'te toplam 2 sp
  olur; birini kaldır.
- **Ekran geçişleri:** raylı düzende (orta genişlik) 0.1.3'ün 150 ms'lik solması yerine fade-through: yeni
  ekran %98'den büyüyerek belirir. Kenar çubuklu düzende (DeX, geniş pencere) 150 ms'lik solma aynı.
- **Panel ve menüler:** kenar çubuğu daraltılıp genişletilirken genişliği 300 ms'de akıcı değişir; menüler
  köşelerinden büyüyerek açılır, solarak kapanır.
- **Cam:** tablonun yapışkan başlığı satırlar altından geçerken buzlu cama döner (`DenseTable(glassHeader = false)`
  düz bırakır); menüler, sağ tık menüleri ve yüzen oynatıcı camdır. Hepsi düz: `DesktopShell(glass = false)`.

## 1.0'dan 2.0'a

Uyumu bozan değişiklikler azdır; çoğu yeni parametre varsayılanla gelir.

- **Sürümler:** yukarıdaki tablo (özellikle `minSdk` 34, `compileSdk` 37, Kotlin 2.3+).
- **Üç düğmeli gezinme:** `DesktopKitRoot` alttaki sistem gezinme çubuğunu artık kabuğa bırakır; kabuk
  çubuklarını onun arkasına uzatır, düğmeler kitin renginde kalır. Kabuğun içinde gezinme çubuğu için
  kendi boşluğunu ekleyen ekran varsa o boşluğu kaldır: `rememberShellContentPadding()` onu zaten içerir.
  Kabuksuz kullanılan root eskisi gibi tümünü boşluk olarak bırakır. Sistem çubuklarının simge rengini
  uygulama kendisi yönetiyorsa `DesktopKitRoot(systemBarsAppearance = false)`.
- **DeX ve masaüstü pencereleri:** kenar çubuklu düzende menü çubuğu pencerenin başlık çubuğuna yerleşir
  (Android 15+; sistem başlık düğmelerinin yerini bildirdiğinde). Başlık çubuğunun altına kendi şeridini
  çizen uygulama varsa kaldırmalı.
- **`DetailTransition`** paylaşılan öğe düzeniyle sarılır (bir ön yerleşim geçişi). Hiçbir şey
  işaretlemeyen ve çok büyük listeler gösteren ekran `sharedElements = false` verebilir.
- **Sekme sırası:** Tab artık üst çubuk → gezinme → içerik → oynatıcı sırasıyla ilerler (telefonda alt
  çubuk en sonda). Kendi `focusProperties` sırası olan ekranlar etkilenmez.
- **`ShellSpec(startRoute)`** bilinmeyen bir rota verilirse çökmek yerine ilk öğeyle açılır.
- **Görünüm:** devre dışı düğmeler dolgu ve çerçeveleriyle %50 saydam; menü çubuğunun arama alanı ve tuş kapakları açık temada
  çerçeveli; telefonun alt çubuğu ve çipler büyük yazıda büyür. Ekran görüntüsü testleri olan
  uygulamalar referanslarını yenilemeli.

## 3. Yeni özellikleri açmak

Hepsi isteğe bağlı; hiçbiri açılmadan uygulama 2.0'la eskisi gibi çalışır.

- **Komut paleti (Ctrl+K):** `rememberCommandPaletteState()` ve `DesktopShell(overlays = { CommandPalette(state, items = { sorgu -> … }) })`;
  `shortcuts.paletteItems(resources) { perform(it) }` kısayol tablosunu palete ekler. Türkçe harfleri
  katlayan bulanık arama, gruplar, Ctrl+Enter ile yeni pencere. Kısayolları etkinliğin `dispatchKeyEvent`'inden
  yönlendiriyorsan paletin durumunu etkinlikte tut (`private val palet = CommandPaletteState()`, palete de bunu
  ver) ve palet açıkken yönlendirmeyi atla: `(!palet.isOpen && shortcuts.matchNative(event)?.let { … } == true)
  || super.dispatchKeyEvent(event)`; yoksa Ctrl+K yazılanı siler, öbür kısayollar paletin arkasında çalışır.
- **Kapak ızgarası:** `DeskGrid(items, key, onItemClick) { CoverCell(başlık, …) { kapak } }` tablonun seçim
  kurallarıyla.
- **Sürükle bırak:** satır ya da kapak için `DenseTable(rowDrag = …)`, `DeskGrid(itemDrag = …)`,
  `DeskListRow(drag = …)`; hedef için `Modifier.dropZone(kinds, onDrop = { yük -> … })`; kenar çubuğu için
  `DesktopShell(navDrop = NavDrop(mapOf("playlists" to setOf("track"))) { rota, yük -> … })`. Sürükleme
  uygulamanın diğer penceresine de geçer.
- **Çoklu pencere:** manifestte etkinliğe
  `<property android:name="android.window.PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI" android:value="true" />`,
  kısayol tablosuna `CommonShortcuts.NewWindow` (Ctrl+N), eylem olarak `DeskWindows.openNewWindow(activity, route)`
  ve `ShellSpec(startRoute = DeskWindows.startRoute(intent))`. Tema, vurgu ve yoğunluk pencereler arasında
  eşitlenir.
- **Paylaşılan öğe:** listede ve ayrıntıda aynı anahtarla `Modifier.detailSharedElement("kapak-$id")` ve
  başlık için `Modifier.detailSharedBounds("başlık-$id")`.
- **Duvar kâğıdı rengi:** `rememberDeskPreferences(dynamicAccent = true)`.
- **Titreşim:** `rememberDeskHaptics()` (yalnızca dokunmada çalışır); kitin anahtarları, çipleri, sayfaları
  ve sürükle bırakı bunu zaten kullanır.
- **Yükleniyor görünümü:** `DenseTable(loading = true)`, `DeskGrid(loading = true)`, `SkeletonList()`.
- **Erişilebilirlik:** `DeskSlider(label = "Ses düzeyi", valueText = { "${(it * 100).roundToInt()} %" })`
  TalkBack'e kaydırıcının adını ve değerini söyler.

## 2.0.0'dan 2.1.0'a

Kod değişmeden derlenir; sürümü `2.1.0` yapmak yeter. Testlerde ve kullanımda fark edilebilecek davranışlar:

- **Sürükle bırak basılı tutunca başlar:** `dragSource`, `rowDrag`, `itemDrag`, `DeskListRow(drag)` ve `DeskTab(drag)`
  sürüklemeyi işaretçi önce sistemin dokunup basılı tutma süresi kadar durunca başlatır: fare ve kalemde ardından
  kayınca, parmakta (`touch = true`) süre dolunca. Sürüklemeyi basıp hemen kaydırarak deneyen testler bastıktan sonra
  saati bu süreden (varsayılan 500 ms) fazla ilerletmeli, örneğin `compose.mainClock.advanceTimeBy(600)`, sonra
  kaydırmalı.
- **Bildirimler sıraya girmez:** yeni `toasts.show(…)` ekrandaki bildirimi kapatır; eskisinin `show` çağrısı
  `ToastResult.Dismissed` döner. Sonucu "geri al"a bağlayan kod değişmez; arka arkaya gösterilmesi gereken iki
  mesaj varsa tek bildirimde birleştir.
- **Parmak dokunuşu seçmez:** `DenseTable` ve `DeskGrid` seçim yokken parmak ya da kalem dokunuşunda yalnızca
  `onRowClick` / `onItemClick`'i çağırır; seçim varken dokunuş seçimi değiştirir. Yerleşim değil işaretçi belirler
  (fare tıklaması her yerleşimde seçer). Uzun basma eskisi gibi menüyü açar ve satırı seçer, menü üzerinde
  çalıştığı satırları `selectedKeys`'te bulur. Dokunuşla yalnızca seçip açmayan ekran (geniş pencerede seçilenin
  ayrıntısını yan panelde gösteren gibi) artık dokunuşu `onRowClick`'te karşılamalı ve açtığı satırı
  `selectedKeys`'e koymamalı (yoksa bir sonraki dokunuş açmak yerine seçer).
- **Ekran klavyesi parmakla kapanır:** ekran klavyesi açıkken kabuğun içinde kaydırmak ya da metin alanları
  dışına dokunmak odağı alır (donanım klavyesi bağlıyken almaz). Klavyeyi açık tutması gereken düğmeye (sohbetteki
  Gönder) ve uygulamanın kendi `BasicTextField`'larına `Modifier.keepsKeyboardOpen()` ekle; işaretlenen alan
  kitinkiler gibi sayılır.
- **Kısayollar dokunmatikte gizli:** menülerde ve "⋯" sayfasında kısayol yalnızca fare ya da donanım klavyesi
  varken yazılır. Kendi kısayol listen için aynı koşul: 2.2.0'dan beri `showShortcutHints()` (2.1.0'da
  `DeskTheme.inputMode == InputMode.Pointer || hasHardwareKeyboard()`).
- **"⋯" sayfasında `checked`:** satır sonunda anahtar olur ve dokununca sayfa kapanmaz (menüde eskisi gibi kapanır).
- **Paylaşılan kapak:** `detailSharedElement` öğeyi verilen biçimle kırpar; kapağı kendi `clip`'iyle yuvarlayan
  ekran `shape =` verirse uçarken de yuvarlak kalır (`CoverCell` içinde hücrenin biçimi varsayılan).

## 2.1.0'dan 2.2.0'a

Kod değişmeden derlenir; sürümü `2.2.0` yapmak yeter.

- **Geri seçimi temizler:** `DenseTable` ve `DeskGrid`'de süren bir seçim varken sistemin Geri hareketi önce
  seçimi temizler (Esc gibi, Android'in eylem kipindeki gibi): dokunmatik yerleşimde ya da seçim modunda her
  seçim, farede birden çok satır. Farenin tıklayıp seçtiği tek satırda Geri eskisi gibi geri gider. Uygulama
  temizlemeyi almazsa (aynı seçimi tutarsa) sonraki Geri uygulamanındır; Geri'yi baştan uygulama yönetecekse
  `clearSelectionOnBack = false`.
  - Sıra: Compose en son oluşturulan açık geri işleyicisini önce çağırır ve tablonunki tabloyla birlikte
    oluşturulur. `DetailTransition` ayrıntısının içindeki tabloda önce ayrıntı kapanır, seçim kalır.
  - Ekranın kendi `BackHandler`'ı tablonunkinden önce de sonra da çalışabilir; onunki önce çalışmalıysa bunu
    kapatıp seçimi kendi işleyicisinde temizle.
- **İlerleme çubuğunun adı:** miktarı belli bir `DeskProgressBar`'a açıklama verilmezse TalkBack artık
  "Yükleniyor" demez, yalnızca yüzdeyi okur. Ad istiyorsan `contentDescription` ver.
- **Kısayol ipuçları tek kural:** bildirimlerdeki ve komut paletindeki tuşlar da `showShortcutHints()`'e uyar:
  klavye kapağı bağlı dokunmatik yerleşimde görünür, elde tutulan telefonda paletin öğelerindeki kısayollar gizlenir.
- **Komut paleti açıkken yönlendirme:** paletin durumunu etkinlikte tut (`private val palet =
  CommandPaletteState()`) ve `dispatchKeyEvent`'teki kısayol yönlendirmesine `!palet.isOpen` koşulunu ekle
  (3. adımdaki örnek).
- **Yeni:** `showShortcutHints()`, `DeskCard(verticalArrangement)`, `DenseTable`/`DeskGrid(clearSelectionOnBack)`.

## 2.2.0'dan 2.3.0'a

Kod değişmeden derlenir (2.2.0'a karşı derlenmiş kod da bağlanır); sürümü `2.3.0` yapmak yeter. Davranış
değişikliklerinin hepsi telefonda (`WidthClass.Compact`); tablet ve DeX'te süreler ve görünüm aynı, iki tarafta ortak
olan birkaç düzeltme (yayların eşiği, alt sayfanın yerleşimle kayması, azaltılmış harekette her şeyin bir anda olması)
CHANGELOG'un "Tablet ve DeX" bölümünde. Ayrıntılar [CHANGELOG](CHANGELOG.md#230-2026-10-31) içinde.

- **Telefonda farklı görünenler:** ayrıntı derinlikte açılır (yüzde 12 kayıp solar, liste altında küçülür) ve
  öngörülü geri hareketinde liste gerçekten arkadadır; oynatıcı şeritten tek parça büyür; alt sayfa daha sert bir
  yayla gelir; menü açıldığı noktadan büyür; yana kaydırılan bildirim yoluna devam eder; basma biraz daha hafif ve
  bırakınca hafifçe seker; yaylar daha çabuk yerleşir. Hiçbiri kod istemez.
- **Yayların eşiği:** kendi yayların `DeskMotion.gesture()` ya da `DeskMotion.spatial(tier, timed)` ile yazılıysa
  birim eşiğini ver: piksel değerinde `DeskMotion.PIXEL`, `IntOffset`'te `IntOffset.VisibilityThreshold`,
  dikdörtgende `Rect.VisibilityThreshold`, 0..1 kesirde `0.001f`. Vermezsen eski davranış kalır (yay yerleşmiş
  göründükten sonra 300 ms kadar daha çalışır). Dokunuşun açtığı şeyler için `DeskMotion.open(threshold)`.
- **Kart geçişi:** listedeki kartın ayrıntının başlığına büyümesi için ikisini de aynı anahtarla
  `Modifier.detailSharedCard("kart-$id")` ile işaretle; kapak ve başlık `detailSharedElement`/`detailSharedBounds`
  ile uçmayı sürdürür. Kartsız ayrıntı derinlik geçişiyle açılır, ek bir şey gerekmez. Başlığın `shape`'i kartın
  köşesi (varsayılan `radiusCard`).
- **Oynatıcıda uçan kapak ve başlık:** `mini` ve `full` içeriğinde aynı öğeyi aynı anahtarla
  `Modifier.playerSharedElement("kapak", RoundedCornerShape(8.dp))` ile işaretle (öğenin değiştiricilerinde ilk
  sırada, boyutundan önce). İşaretlenmeyen içerik eskisi gibi solar. Öğe çizildiği gibi kaydedilir (donanım tuvalinde
  kendi grafik katmanına: Coil ya da Glide'ın donanım bitmap'i olan bir kapak da olur); açık başlayan oynatıcıda
  (geri yüklenen durum) ilk inişte de uçar. Tam oynatıcının içeriği sayfa rengi gelmeden görünmez; şeridin denetimleri
  yolun ilk çeyreğinde solar ve sonra yerleştirilmez (dokunulmaz, okunmaz).
- **Satırların sırayla gelişi:** listenin olduğu yerde `val gelis = rememberListAppear(yuklendi)` al, her satıra
  `Modifier.listAppear(gelis, index)` ver. Anahtar, satırların ne zaman geldiğini söyleyen şeydir (yüklenme bayrağı,
  gösterilen albüm); anahtarsız yalnızca listenin ilk görünüşü sayılır. `DetailTransition` listesinde `rememberListAppear`'ı
  `list` lambda'sının dışında al ki ayrıntıdan dönüşte satırlar yeniden gelmesin.
- **`ExpandablePlayer` belgesi:** tam oynatıcının kaydırılabilir içeriğinin önce kayıp en üstte sayfayı çekeceği
  cümlesi silindi; böyle bir bağlantı hiç yoktu. Tam oynatıcıda kaydırılabilir içerik varsa aşağı çekme içeriğe
  gider; sayfayı içeriğin üstünden çekmek için uygulama kendi `NestedScrollConnection`'ını kurar.
- **Yeni:** `Modifier.detailSharedCard`, `Modifier.playerSharedElement`, `rememberListAppear`/`Modifier.listAppear`
  (`ListAppear.ROWS`, `STAGGER_MS`, `DURATION_MS`), `DeskMotion.open`, `DeskMotion.PIXEL`,
  `DeskMotion.gesture(visibilityThreshold)`, `DeskMotion.spatial(tier, timed, visibilityThreshold)`.
