# 2.0'a geçiş

Bu rehber kiti kullanan uygulamaları 2.0'a taşımak için. Soulseek+ ve Walkman Sync şu an 0.1.3 kullanıyor;
1.0.0'dan geçenler 1. adımdan sonra doğrudan [1.0'dan 2.0'a](#10dan-20a) bölümüne atlayabilir. 2.0.0 kullananlar
yalnızca [2.0.0'dan 2.1.0'a](#200dan-210a) bölümüne bakar.

Sıra şöyle: önce derleme ortamı (kitin kendisi aynı adımları `7845eb5` gönderisinde attı, örnek olarak
oraya bakılabilir), sonra uygulamanın kendi yazdığı bileşenler kitinkilerle değiştirilir, en son yeni
özellikler açılır. Her adımdan sonra uygulama derlenip çalışır durumda kalır.

## 1. Derleme ortamı

| | 0.1.3 uygulamaları | 2.0.0 |
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
2. `gradle/libs.versions.toml`: yukarıdaki sürümler ve `desktopKit = "2.0.0"`. En kolayı kitin kendi
   `gradle/libs.versions.toml` dosyasındaki sürümleri almak.
3. AGP 9 Kotlin'i kendisi derler (yerleşik Kotlin). Modüllerden `alias(libs.plugins.kotlin.android)`
   satırını sil, `kotlin.compose` kalsın. Kökteki `build.gradle.kts` dosyasında eklentiyi uygulamadan
   bildirmek Kotlin sürümünü katalogdakine sabitler:

   ```kotlin
   plugins {
       alias(libs.plugins.android.application) apply false
       alias(libs.plugins.kotlin.android) apply false   // yalnızca Kotlin sürümünü sabitler
       alias(libs.plugins.kotlin.compose) apply false
   }
   ```
4. `app/build.gradle.kts`: `kotlinOptions { jvmTarget = "17" }` bloğunu sil (`compileOptions` içindeki Java 17
   yeter); `compileSdk = 37`, `minSdk = 34`, `targetSdk = 36`.
5. `gradle.properties`: `android.useAndroidX` ve `android.nonTransitiveRClass` satırları AGP 9'da varsayılan,
   silinebilir.
6. Android 14 öncesi için yazılmış `Build.VERSION.SDK_INT` dallarını temizle; kit de kendininkileri sildi.

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
| `MenuButton(text, entries, icon, enabled)` | `MenuButton(...)` | Aynı; sağında aşağı ok. |
| `IconMenuButton(icon, label, entries)` | `IconMenuButton(...)` | Aynı. |
| `SurfaceCard { }` | `DeskCard { }` | `title` ve `onClick` (tıklanabilir kart) eklendi; iç boşluk `contentPadding`. |
| `KeyValueRow(label, value, mono, valueColor, maxLines)` | `KeyValueRow(...)` | Ek: `labelWidth`, `selectable` (değer seçilip kopyalanır). |
| `BannerTone`, `StatusBanner(tone, text, actionLabel, onAction, closeLabel, onClose)` | Aynı adlar | Ek: `title` (iki satırlı şerit), `icon`. Uygulamanın kendi `BannerTone`'unu sil. |
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
        toasts.show(message.text)   // sırayla gösterir; geri alma için actionLabel = "Geri al"
    }
}
DesktopShell(…, toasts = toasts) { … }
```

Bildirim telefonda alt çubuğun ve oynatıcının, tablet ve DeX'te oynatıcının üstünde çıkar; parmakla yana
kaydırınca kapanır, TalkBack açıkken sistemin önerdiği süre kadar kalır.

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
  katlayan bulanık arama, gruplar, Ctrl+Enter ile yeni pencere.
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
  varken yazılır. Kendi kısayol listen için `hasHardwareKeyboard()`.
- **"⋯" sayfasında `checked`:** satır sonunda anahtar olur ve dokununca sayfa kapanmaz (menüde eskisi gibi kapanır).
- **Paylaşılan kapak:** `detailSharedElement` öğeyi verilen biçimle kırpar; kapağı kendi `clip`'iyle yuvarlayan
  ekran `shape =` verirse uçarken de yuvarlak kalır (`CoverCell` içinde hücrenin biçimi varsayılan).
