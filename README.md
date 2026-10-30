# desktop-kit

Android tabletler ve Samsung DeX için **masaüstü sınıfı** Jetpack Compose arayüz kiti. Nağmedar'ın
arayüzünden çıkarıldı. Klavye, fare, sağ tık ve dokunmatik aynı uygulamada birlikte çalışır.

- **Uyarlanabilir kabuk:** dar pencerede alt çubuk, orta genişlikte yan ray, DeX ve büyük
  ekranda menü çubuğu ile daraltılabilir kenar çubuğu. Pencere boyutu değişince açık ekran, geri
  yığını ve kaydırma konumu korunur. Sığmayan ekranlar otomatik bir **"Daha fazla"** ekranında listelenir.
  Ekran geçişleri canlandırılır (telefonda fade-through, geniş pencerede kısa geçiş); telefonun üst
  çubuğunu ekran kendisi değiştirebilir (`CompactTopBar`).
- **Buzlu cam:** telefonun alt ve üst çubuğu, oynatıcı şeridi, alt sayfalar ve menüler arkalarındaki içeriği
  bulanıklaştırır; pil tasarrufunda ve "Animasyonları kaldır" açıkken opak.
- **Telefona göre:** telefonda alt çubuk 64 dp, satırlar 52 dp; içerik çubukların altından geçer, kaydırılınca
  alt çubuk çekilir (yalnızca çizimde, ekran yeniden ölçülmez), yukarı kaydırınca geri gelir. Cam, üç düğmeli
  gezinmenin arkasına kadar uzanır. Üç sekme ve "⋯", genişleyen oynatıcı. Chip filtreler tek satırda yana kayar, gereksiz tablo sütunları gizlenir, ayar
  satırları sığmazsa alt alta dizilir. Tablet ve DeX'te yazılar bir punto büyük, düzen aynı.
- **Android hareketleri:** telefonda parmağı izleyen, kesilebilen yaylar; tablet ve DeX'te kısa, süreli
  geçişler. Fade-through ve paylaşılan eksen geçişleri, öngörülü geri hareketi (`DetailTransition`, genişleyen
  oynatıcı), akıcı kenar çubuğu, köşeden açılan menüler, yaylanan gezinme göstergesi ve dokunma geri bildirimi.
  Hepsi sistemin animasyon ölçeğine uyar.
- **İş istasyonu:** tablet ve DeX'te kenar çubuğu, ana bölme ve sekmeli, boyutlandırılabilir ayrıntı paneli;
  yüzen cam oynatıcı çubuğu.
- **İki yoğunluk:** fare veya touchpad kullanılınca sıkı düzen (24 dp hedef), parmakla
  dokununca geniş düzen (48 dp). Geçiş otomatiktir; kullanıcı ayarlardan sabitleyebilir.
- **Tema:** açık, koyu ve OLED siyah. Dört hazır vurgu rengi var; istediğin markanın rengini de
  verebilirsin. Tüm metinler WCAG AA kontrastını (4.5:1) testle sağlar.
- **Bileşenler:** düğme, simge düğmesi, segment ve renk seçimi, kaydırıcı (tekerlek ve ok
  tuşları), yoğun tablo (sıralama, çoklu seçim, sütun seçici, satır menüsü), kapak ızgarası, bölünmüş panel, menü
  çubuğu, sağ tık menüsü (sağ tık, iki parmak dokunma, uzun basma, Menu tuşu), "⋯" sayfası, iletişim kutusu, ipucu
  (tooltip), ayar satırı; metin, arama ve sayı alanları, onay kutusu, anahtar, ilerleme çubuğu, durum şeridi,
  rozet, bildirim (geri alma düğmeli), kart, liste satırı, seçim çubuğu, sekmeler, yükleniyor yer tutucuları.
  Hepsi kitin kendi çizgisinde (Material bileşeni yok). Her birinde üzerine gelme, odak halkası ve basılı durumu
  var ve testle denetleniyor.
- **Komut paleti (Ctrl+K):** ekranlar, uygulamanın nesneleri ve kısayollar tek aramada; Türkçe harfleri katlayan
  bulanık eşleşme.
- **Sürükle bırak:** tablo satırları, kapaklar, liste satırları ve sekmeler kenar çubuğuna, listelere ya da
  uygulamanın öbür penceresine sürüklenir.
- **DeX ve pencereler:** menü çubuğu pencerenin başlık çubuğuna yerleşir (Android 15+); uygulama birden çok
  pencerede açılır, tema ve vurgu pencereler arasında eşitlenir.
- **Erişilebilirlik:** %200 yazı boyutunda taşan ya da sessizce kesilen yazı yok (sığmayan tek satırlık
  etiketler üç noktayla biter), her denetimin TalkBack adı var, Tab okuma sırasıyla ilerler; testle denetlenir.
- **Klavye kısayolları:** tek tablodan menü çubuğu, ipuçları, ayarlardaki liste ve sistemin
  Meta+/ listesi üretilir.
- **Hazır ekranlar:** sayfa başlığı, boş durum veya "yakında" ekranı, görünüm, giriş ve kısayol
  ayar bölümleri.
- **Türkçe ve İngilizce** metinler kitin içinde gelir.

## Kurulum

Kit [JitPack](https://jitpack.io) ile git etiketinden derlenir.

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Uygulama modülünün `build.gradle.kts` dosyası:

```kotlin
dependencies {
    implementation("com.github.hesaplarimdijital-coder:desktop-kit:2.0.0")
    implementation("androidx.activity:activity-compose:1.13.0")
}
```

Gereksinimler: `minSdk` 34 (Android 14), `compileSdk` 37, Android Gradle eklentisi 9.1.1 ve üstü, Kotlin 2.3 ve
üstü, Compose açık (`buildFeatures { compose = true }` ile Kotlin Compose eklentisi). Kit Compose BOM 2026.09.00
(Compose 1.12) ile derlenir ve buzlu cam için [Haze](https://github.com/chrisbanes/haze) 2.0.1'i (Apache-2.0)
getirir. Eski bir sürümden geçiş: [MIGRATION.md](MIGRATION.md).

## Yeni uygulama: en kısa hâli

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = rememberDeskPreferences()
            DesktopKitRoot(prefs) { widthClass ->
                DesktopShell(
                    spec = ShellSpec(
                        appTitle = "Uygulamam",
                        sections = listOf(
                            NavSection("Kitaplık", listOf(
                                NavItem("home", "Ana sayfa", DeskIcons.Home),
                                NavItem("files", "Dosyalar", DeskIcons.Folder),
                            )),
                        ),
                        footer = NavItem("settings", "Ayarlar", DeskIcons.Settings),
                    ),
                    widthClass = widthClass,
                    sidebarCollapsed = prefs.sidebarCollapsed,
                    onSidebarCollapsedChange = prefs::updateSidebarCollapsed,
                ) { route, navigator ->
                    when (route) {
                        "home" -> AnaSayfa()
                        "files" -> Dosyalar()
                        "settings" -> LazyColumn {
                            item { AppearanceSection(prefs) }
                            item { InputSection(prefs) }
                        }
                    }
                }
            }
        }
    }
}
```

`AndroidManifest.xml` içinde DeX'te pencere boyutlandırılınca etkinlik yeniden başlamasın diye (ve sistemin kendi
"Yeni pencere" menüsü için):

```xml
<activity
    android:name=".MainActivity"
    android:resizeableActivity="true"
    android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|density|keyboard|keyboardHidden|navigation|uiMode|fontScale"
    android:windowSoftInputMode="adjustResize">
    <property android:name="android.window.PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI" android:value="true" />
</activity>
```

Tam örnek `sample/` klasöründe: her bileşeni her durumuyla gösteren katalog, kapak ızgarası ve albüm sayfası,
sürükle bırak, komut paleti, kısayollar, menü çubuğu, tablo, ayarlar. Yeni bir uygulamaya başlarken onu
kopyalayıp ekranları değiştirmek en hızlı yol.

## Klavye kısayolları

```kotlin
private val FileGroup = ShortcutGroup("file", R.string.menu_file)

val shortcuts = ShortcutRegistry(
    groups = listOf(FileGroup, CommonShortcuts.ViewGroup, CommonShortcuts.HelpGroup),
    shortcuts = listOf(
        Shortcut("open", FileGroup, R.string.open, KeyChord(Key.O, ctrl = true)),
    ) + CommonShortcuts.all, // Ctrl+K arama, Ctrl+, ayarlar, Ctrl+B kenar çubuğu, Ctrl+/ liste
)
```

- Menü çubuğu: `DesktopShell(menus = shortcuts.menus(resources, perform = ::run))`
- Tuşları etkinlikten yönlendirmek odak nerede olursa olsun çalışır:

  ```kotlin
  override fun dispatchKeyEvent(event: KeyEvent) =
      shortcuts.matchNative(event)?.let { run(it.id) } == true || super.dispatchKeyEvent(event)
  ```

  Ctrl/Alt/Meta'sız tek tuşlar (Boşluk, M) metin kutusuna yazmayı bozmasın diye burada
  atlanır. Onları sahibi olan ekranda `shortcuts.match(event, scopes)` ile yakala.
- Meta+/ sistem listesi: `onProvideKeyboardShortcuts` içinde `shortcuts.keyboardShortcutGroups(resources)`.
- Ayarlardaki liste: `ShortcutListSection(shortcuts)`.
- Bir birim testinde `assertTrue(shortcuts.conflicts().isEmpty())` ile aynı tuşa iki iş bağlanmadığını denetle.

## Görünümü uygulamaya göre değiştirmek

| Ne | Nasıl |
| --- | --- |
| Marka rengi | `Accent.custom("Marka", Color(0xFF00897B))`. Koyu ve açık temada okunur kalacak şekilde otomatik ayarlanır. Ayarlarda sunmak için `rememberDeskPreferences(accents = Accent.presets + marka)`. |
| Yazı tipi | `DesktopKitRoot(prefs, fonts = DeskFonts.System)` ya da `DeskFonts(sans = …, mono = …)` |
| Renk ince ayarı | `DesktopKitRoot(prefs, colors = { it.copy(danger = Color(…)) })` |
| Boyutlar ve köşeler | `DesktopKitRoot(prefs, dimens = { it.copy(radiusCard = 4.dp, sidebarWidth = 260.dp) })` |
| Yazı boyutları | Tablet ve DeX'te kabuk her yazıyı zaten 1 sp büyütür (`DesktopShell(largeWindowTextStep = 1f)`). Ayrıca `DesktopKitRoot(prefs, type = { mode, fonts -> DeskType.forMode(mode, fonts, pointerStep = 1f) })`: fare ve klavyede her yazı 1 sp daha büyür, dokunmatik değişmez. `pointerStep`/`touchStep` her stile (display'den mono'ya) o kadar sp ekler, satır yüksekliği oranını korur. Tek tek ayar için `{ mode, fonts -> DeskType.forMode(mode, fonts).copy(body = …) }`. Yüksekliği yazıya göre büyüyen bileşenler (tablo satırı, düğme, menü, üst çubuk) kırpılmaz. |
| Logo | `DesktopShell(brand = { m -> Image(painterResource(R.drawable.logo), null, m) })` |
| Yeni simge | `DeskIcons.build("Bulut", DeskIcons.stroke("M4 11a3 3 0 0 1 …"))`. 16×16 ızgara, kitin çizgi stiliyle aynı. |
| Kitin metinleri | Uygulamanın `res/values*/strings.xml` dosyasında aynı adla (`dk_more`, `dk_search_hint`, …) tanımla; uygulamanınki kazanır. |

Değerlere doğrudan erişim için `DeskTheme.colors.text`, `DeskTheme.type.body` ve `DeskTheme.dimens.rowHeight` kullanılır.

## Bileşenler

| Bileşen | Not |
| --- | --- |
| `DeskButton`, `DeskIconButton` | `primary`, `selected`, `enabled` |
| `SegmentedChoice`, `SwatchChoice`, `SettingRow` | Ayar ekranları için |
| `DeskSlider` | 0..1 değer; sürükleme, tıklama, fare tekerleği, ok/Home/End, TalkBack |
| `DenseTable` | Cam başlık, sıralama, seçili satır, `rowMenu` ile satır başına sağ tık menüsü. Çoklu seçim ve sütun seçici için aşağıya bak. |
| `HorizontalSplitPane`, `TwoPane` | Sürüklenebilir ve klavyeyle taşınabilir ayırıcı |
| `ContextMenuArea`, `Modifier.contextMenuGesture` | Sağ tık, uzun basma, Menu tuşu, Shift+F10 |
| `MenuPopup`, `MenuBarMenus`, `MenuEntry` | Kenarda taşmayan menüler |
| `TooltipArea` | Yalnızca fareyle görünür, kısayolu da gösterir |
| `PageHeader`, `MessageScreen`, `OverflowScreen` | Hazır ekran parçaları. `PageHeader` başlığı yalnızca kenar çubuklu düzende gösterir (aşağıya bak). |
| `CompactTopBar`, `TopBarAction` | Telefonda üst çubuğun başlığı, geri düğmesi ve simgeleri (aşağıya bak) |
| `AppearanceSection`, `InputSection`, `ShortcutListSection`, `SettingsSection` | Hazır ayar bölümleri |
| `DeskChip`, `ChipRow`, `FilterChips` | Filtre chip'leri: telefonda tek satır yana kayar, genişte sarar (aşağıya bak) |
| `DetailTransition` | Liste → ayrıntı geçişi ve öngörülü geri hareketi (aşağıya bak) |
| `ExpandablePlayer` | Telefonda mini oynatıcıdan tam ekrana büyüyen oynatıcı, yana kaydırarak parça atlama (aşağıya bak) |
| `MoreSheet`, `MoreButton`, `GlassBottomSheet`, `ActionGroup` | "⋯" eylemleri: telefonda cam alt sayfa, genişte menü |
| `WorkstationLayout`, `InspectorPane`, `InspectorToggleButton` | Tablet ve DeX'te sekmeli, boyutlandırılabilir ayrıntı paneli |
| `DeskDialog`, `DialogFrame`, `DialogDismissButton` | İletişim kutusu; klavye varsa "Esc", yoksa × |
| `DeskTextField`, `DeskSearchField`, `NumberField`, `OptionalNumberField` | Metin, arama ve sayı alanları (aşağıya bak) |
| `DeskCheckbox`, `DeskSwitch`, `CheckboxRow`, `SwitchRow` | Onay kutusu (ara durum dahil) ve anahtar; satır hâlleri TalkBack'e adını söyler |
| `DeskProgressBar`, `StatusBanner`, `CountBadge` | İlerleme (belirli, belirsiz), dört tonlu durum şeridi, sayı rozeti |
| `rememberToastState`, `ToastState`, `ToastHost` | Bildirimler; kabuk `DesktopShell(toasts = …)` ile yerleştirir (aşağıya bak) |
| `DeskCard`, `KeyValueRow`, `DeskListRow`, `SelectionBar` | Kart, etiket–değer satırı, telefon listesi satırı, seçim çubuğu |
| `MenuButton`, `IconMenuButton` | Altında menü açan düğmeler |
| `TabStrip`, `DeskTab` | Sekmeler: rozet, meşgul noktası, kapatma, orta tık, sürükleme |
| `DeskGrid`, `CoverCell` | Kapak ızgarası (aşağıya bak) |
| `CommandPalette`, `PaletteItem` | Ctrl+K komut paleti (aşağıya bak) |
| `Modifier.dragSource`, `Modifier.dropZone`, `NavDrop` | Sürükle bırak (aşağıya bak) |
| `SkeletonList`, `SkeletonListRow`, `SkeletonBlock` | Yükleniyor yer tutucuları; tablo ve ızgarada `loading = true` |
| `rememberDeskHaptics` | Dokunmada titreşim: aç/kapa, tık, eşik, onay, ret |
| `DeskWindows`, `rememberNewWindow` | Uygulamayı yeni pencerede açmak (aşağıya bak) |

### Buzlu cam

Kabuk içerik alanını cam kaynağı yapar; çubuklar, sayfalar ve menüler onu bulanıklaştırarak çizer.

| Cihaz durumu | Görünüm |
| --- | --- |
| Donanım çizimi | Bulanık cam (Haze) |
| Yazılım tuvali (ekran görüntüsü testleri) | Yarı saydam düz yüzey, bulanıklık yok |
| Pil tasarrufu ya da "Animasyonları kaldır" | Opak yüzey, içerik kaydedilmez |

- Cam yüzeyler: telefonda alt çubuk ve oynatıcı şeridi, içerik altına kaydıkça üst çubuk, alt sayfalar, menüler
  ve sağ tık menüleri; tablet ve DeX'te tablonun başlığı, yüzen oynatıcı çubuğu ve menüler. **Başka yüzeyi cam
  yapmadan önce düşün:** her bulanık yüzey her karede ayrı bir GPU geçişidir. Büyük, sık değişen alanlarda
  (tam ekran oynatıcı, liste satırları) kullanma.
- Kendi yüzeyin için: `Modifier.glass(DeskGlass.chrome(DeskTheme.colors, GlassThickness.Thin))`. `chrome` çubuklar
  için, `raised` menü ve sayfalar için. Yazı taşıyan yüzeyde `Thin` ya da `Regular` kullan; ikisi de arkada en
  kötü içerik varken bile yazıyı 4.5:1'de tutar. `UltraThin` yalnızca yazısız yüzeyler için.
- Kendi cam kaynağın (örneğin bir listenin üstünde duran başlık): `val kaynak = remember { HazeState() }`, listede
  `Modifier.glassSource(kaynak, DeskGlass.mode)`, başlıkta `Modifier.glass(malzeme, source = kaynak)`. Cam, kendi
  kaynağının içinde olamaz; kardeş olmalı.
- `DesktopShell(glass = false)` her şeyi düz yapar. `LocalGlassMode provides GlassMode.Translucent` bir alt ağaç
  için kipi sabitler (testler, ekran görüntüsü).
- Bulanık cam kaynağı yazılım tuvaline çizilemez: pencereyi `View.draw(Canvas(bitmap))` ile resme dökeceksen o sırada
  `LocalGlassMode` ile `Translucent` ver ya da `PixelCopy` kullan. Robolectric testlerinde kit bunu kendisi yapar.

### Telefonda içerik çubukların altında

Telefonda içerik pencerenin tamamını kaplar; üst çubuk, oynatıcı şeridi ve alt çubuk üstüne biner. Çubukların
altına uzanan ekran, kapladıkları yeri kabuktan alır ve listesinin uçlarına ekler:

```kotlin
@Composable
fun Sarkilar() {
    val bosluk = rememberShellContentPadding()    // üst: üst çubuk; alt: oynatıcı + alt çubuk
    LazyColumn(contentPadding = bosluk + PaddingValues(bottom = 8.dp)) { … }
}
```

- İlk satır üst çubuğun altında, son satır alt çubuğun üstünde durabilir; arada satırlar camın altından geçer.
- Kaydırınca alt çubuk ve oynatıcı yalnızca çizimde kayar (`graphicsLayer`): ekran yeniden ölçülmez, yeniden
  oluşturulmaz. 120 Hz'de bile kare başına iş yalnızca listenin kendisi.
- Kaydırılmayan ekran için `Modifier.shellContentPadding()`.
- `rememberShellContentPadding()` çağırmayan ekranlar eskisi gibi çubukların arasına yerleşir; 0.2.0 ekranları
  değiştirmeden aynı görünür (yalnızca onlar çubuk kayarken yeniden ölçülür).
- Tablet ve DeX'te boşluk sıfırdır; yalnızca `DesktopShell(floatingPlayer = true)` iken altta yüzen oynatıcının yeri gelir.

### Hareket katmanları

| Pencere | Yer değiştiren, sürüklenen | Solma, renk |
| --- | --- | --- |
| Telefon (`WidthClass.Compact`) | `DeskMotion.gesture()`: sekmesiz, `StiffnessMediumLow` yay; kesilebilir, fırlatma hızını alır | Kısa, süreli |
| Tablet ve DeX | 0.2.0 süreleri: 90 ms solma, 210 ms %98→%100 büyüme, DeX'te 150 ms geçiş, 300 ms panel | Aynı |
| "Animasyonları kaldır" | Anında | Anında |

Kendi animasyonların için `DeskMotion.spatial(LocalMotionTier.current, tween(300))` (telefonda yay, genişte verilen
süre, azaltılmış harekette atlama) ve `DeskMotion.fade(tier, tween(150))`. View kodunda
`ViewAnimators.bind(view).start(animator)`, Compose'da `rememberViewAnimators()`: ekran kapanınca ve yapılandırma
değişince animatör durur ve son değerine atlar.

### Genişleyen oynatıcı (telefon)

```kotlin
val oynatici = rememberExpandablePlayerState()
DesktopShell(…, bottomBar = { tur ->
    if (tur == NavKind.BottomBar) {
        ExpandablePlayer(
            state = oynatici,
            mini = { MiniOynatici() },          // şerit; camı kit çizer
            full = { TamOynatici(kapat = { kapsam.launch { oynatici.collapse() } }) },
            onPrevious = calar::onceki,
            onNext = calar::sonraki,
        )
    } else {
        CalmaCubugu()                           // tablet ve DeX
    }
})
```

- Mini oynatıcı yukarı sürüklenince ya da dokununca büyür; tam oynatıcı aşağı sürüklenince küçülür. Yarı yolu
  geçmişse ya da hızlı fırlatıldıysa açılır, yoksa geri döner; yerleşme fırlatma hızını alan bir yaydır.
- Perde, miniden tama geçiş ve alt çubuğun çekilmesi parmağı izler (`oynatici.progress`, çizimde okunur).
- Öngörülü geri hareketi tam oynatıcıyı küçültür ve kapatır.
- Mini oynatıcıyı sola kaydırmak `onNext`, sağa `onPrevious`; şerit parmağı izler, eşikte titreşir, eşiğe
  varmazsa yaylanarak geri gelir.

### "⋯" eylemleri ve telefonda 3 sekme

```kotlin
MoreButton(groups = {
    listOf(
        ActionGroup(null, listOf(MenuEntry.Item("Sıradaki yap", ::siradaki, icon = DeskIcons.Play))),
        ActionGroup("Git", listOf(MenuEntry.Item("Sanatçı", ::sanatci, icon = DeskIcons.Artists))),
    )
})
```

Telefonda cam alt sayfa, genişte menü. Klavyeyle gezilir; Esc, Geri ya da aşağı kaydırmak kapatır.

Telefonda üç sekme ve "⋯": `ShellSpec(bottomBar = listOf("muzik", "podcast", "radyo"), moreStyle = MoreStyle.Sheet)`.
"⋯" sığmayan ekranları bölümleriyle ve `DesktopShell(moreActions = listOf(ActionGroup(…)))` eylemlerini listeler.

### İş istasyonu (tablet ve DeX)

```kotlin
val panel = rememberInspectorState(prefs)            // genişlik ve açık/kapalı saklanır
WorkstationLayout(
    state = panel,
    main = { Kitaplik() },
    inspector = {
        InspectorPane(sekmeler, selected = panel.selectedTab, onSelect = { panel.selectedTab = it },
            onCollapse = panel::toggle) { sekme -> SekmeIcerigi(sekme) }
    },
)
```

Ayırıcı sürüklenir ya da odaktayken ←/→ ile taşınır. Telefonda yalnızca `main` görünür. Paneli araç çubuğundan
açıp kapatmak için `InspectorToggleButton(panel)`.

### Tablo sütunları

```kotlin
DenseTable(…, columnsState = rememberTableColumnsState("sarkilar", prefs))
```

Başlığa sağ tık, uzun basma ya da Menu tuşu: sütunları göster/gizle, sola/sağa taşı, sıfırla. Seçim saklanır;
sütunlar `TableColumn(id = "sure")` ile tanınır. Satırlar başlığın altından geçerken başlık cama döner.

### İletişim kutusu

```kotlin
if (acik) DeskDialog(onDismiss = { acik = false }, title = "Listeyi kaydet", actions = {
    DeskButton("Vazgeç", { acik = false })
    DeskButton("Kaydet", ::kaydet, primary = true)
}) { … }
```

Başlığın yanındaki kapatma işareti klavye bağlıyken "Esc", dokunmatikte × düğmesidir. Kendi çerçevende
`DialogDismissButton()` aynı kuralı `LocalDialogDismiss` üzerinden uygular.

### Vurgu rengi

Vurgu (ve camın tonu) değişince renkler 150 ms'de geçer. Çalan parçanın kapağından vurgu almak için
`DesktopKitRoot(prefs, accent = Accent.custom("Kapak", kapakRengi))`; saklanan vurgu değişmez. Duvar kâğıdının
rengini ayarlarda sunmak için `rememberDeskPreferences(dynamicAccent = true)` (`Accent.dynamic(context)`); duvar
kâğıdı değişince renk de değişir.

### Chip filtreler

```kotlin
var turler by remember { mutableStateOf(setOf<String>()) }
FilterChips(
    options = listOf(ChipOption("flac", "FLAC", count = "128"), ChipOption("mp3", "MP3"), ChipOption("wav", "WAV")),
    selected = turler,
    onToggle = { t -> turler = if (t in turler) turler - t else turler + t },
)
```

- Telefonda (`WidthClass.Compact`) tek satırdır, yana kayar; kaydırılacak yer varken kenarlar solar. Böylece
  filtreler ekranın iki üç satırını kaplamaz.
- Tablet ve DeX'te alt satıra sarar. `wrap = true/false` ile zorlanabilir.
- Kendi chip'lerin için `ChipRow { DeskChip(…); DeskChip(…) }`.

### Tabloda sütun gizleme

`TableColumn("Tür", ColumnWidth.Fixed(120.dp), hideBelow = WidthClass.Medium) { it.tur }` sütunu telefonda
gizler; sıralama sütun numaraları değişmez.

### Liste → ayrıntı geçişi ve öngörülü geri

```kotlin
var acik by remember { mutableStateOf<Kitap?>(null) }
DetailTransition(
    detail = acik,
    onBack = { acik = null },
    detailKey = { it.id },
    list = { KitapListesi(onAc = { acik = it }) },
) { kitap ->
    CompactTopBar(title = kitap.ad, onBack = { acik = null })
    KitapAyrintisi(kitap)
}
```

- Açılırken ayrıntı kısa bir yoldan kayıp belirir, liste öbür yana çekilir; kapanırken tersi.
- Android 14+ öngörülü geri hareketinde ayrıntı parmakla küçülür ve köşeleri yuvarlanır; bırakınca kapanır.
  Manifestte `<application android:enableOnBackInvokedCallback="true">` gerekir. Eski sürümlerde Geri yine kapatır.
- Geri hareketini `DetailTransition` üstlenir (`CompactTopBar` içindeyken de); ayrıca `BackHandler` gerekmez.

### Tabloda çoklu seçim

```kotlin
var secili by remember { mutableStateOf<Set<Any>>(emptySet()) }
var secimModu by remember { mutableStateOf(false) } // dokunmatikte "Seç" düğmesi
DenseTable(
    columns = sutunlar, rows = kitaplar, key = { it.id },
    onRowClick = { kitap, _ -> ayrintiyiAc(kitap) },   // yalnızca düz tıklamada çağrılır
    selectedKeys = secili,
    onSelectionChange = { secili = it },
    toggleOnClick = secimModu,
    rowMenu = { kitap, _ -> menu(secili) },            // menü açılırken seçim güncellenmiş olur
)
```

- Tıklama yalnızca o satırı seçer; Ctrl (veya Meta) + tıklama satırı ekler/çıkarır; Shift + tıklama son
  tıklanan satırdan bu satıra kadar seçer (Ctrl+Shift ekler).
- Odak tablodayken Ctrl+A hepsini seçer, Shift+↑/↓ seçimi genişletir.
- `toggleOnClick = true` iken her dokunuş satırı ekler/çıkarır; `onRowClick` çağrılmaz.
- Sağ tık / uzun basma seçili bir satırdaysa seçim korunur, başka satırdaysa yalnızca o satır seçilir.
  `rowMenu` uygulamanın `secili` durumunu okuyarak "3 kitabı sil" gibi menü kurar.
- `onSelectionChange` verilmezse tablo eskisi gibi tek seçimlidir (`selectedKey`).

### Ekran klavyesi

`DesktopKitRoot` uygulamanın tamamına `WindowInsets.safeDrawing` boşluğu verir (klavye dâhil).
`enableEdgeToEdge()` ve `android:windowSoftInputMode="adjustResize"` ile içerik klavyenin hemen
üstünde biter; ekranlarda ayrıca `imePadding()` gerekmez. Telefonda klavye açıkken alt gezinme
çubuğu gizlenir, kapanınca geri gelir (`DesktopShell(hideNavigationWithKeyboard = false)` ile kapatılır).

### Ekran geçişleri

`DesktopShell(transitions = ShellTransitions.Default)` (varsayılan): telefonda (alt çubuklu düzen)
ekranlar arasında fade-through: eski ekran 90 ms'de solar, yenisi 210 ms'de belirirken %96'dan tam
boyuta yaylanarak büyür. Ray ve kenar çubuklu düzende (tablet, DeX) hareketsiz, 150 ms'lik kısa bir geçiş.
Geri dönüş aynı biçimde. Süreler sistemin animasyon ölçeğine uyar ("Animasyonları kaldır" açıksa
geçiş olmaz). Alt çubuk, ray ve kenar çubuğundaki seçim rengi 150 ms'de yumuşakça değişir.
`ShellTransitions.None` geçişi kapatır (ekran görüntüsü testleri için).

0.2.0 ile telefon ve tablette (alt çubuk, ray) Android'in fade-through geçişi kullanılır: eski ekran 90 ms'de
solar, yenisi 210 ms'de %96 (telefon) ya da %98'den (tablet) büyüyerek belirir. Kenar çubuklu düzende (DeX)
150 ms'lik sade geçiş kalır. Manifestte `android:enableOnBackInvokedCallback="true"` varsa öngörülü geri
hareketi geçişi parmakla oynatır.

### Telefonda kaydırma ve boyutlar

- `hideBarsOnScroll = true` (varsayılan): içerik aşağı kaydırılınca alt çubuk ve üstündeki şerit çekilir, yukarı
  kaydırınca gelir; ekran değişince hep görünür başlar. Üst çubuk kaydırılmış içerikte cama döner ve altında ince
  bir çizgi belirir.
- `compactSizes = true` (varsayılan): alt çubuk 64 dp, dokunmatik satır 52 dp.
- `largeWindowTextStep = 1f` (varsayılan): tablet ve DeX'te her yazı 1 sp büyük. `0f` eski boyutlar.

### Telefonda üst çubuk: `CompactTopBar`

Bir ayrıntı ya da düzenleme ekranı telefondaki üst çubuğu kendisi kurar:

```kotlin
CompactTopBar(
    title = kitap.ad,                         // null: rotanın adı kalır
    onBack = { ayrinti = null },              // geri oku; sistemin Geri hareketi de bunu çağırır
    backIcon = TopBarBackIcon.Back,           // ya da TopBarBackIcon.Close (×)
    actions = listOf(
        TopBarAction(DeskIcons.Check, "Kaydet", ::kaydet, primary = true),  // vurgu renginde
        TopBarAction(DeskIcons.Delete, "Sil", ::sil),
    ),
    showSearch = false,                       // arama simgesini gizler
)
```

- Yalnızca telefon düzeninde (`WidthClass.Compact`) etkilidir. Orta ve daha geniş pencerede (ray,
  kenar çubuğu, DeX) hiçbir şey değişmez ve Geri hareketi de yakalanmaz; bu düzenlerde ekran kendi
  başlığını ve düğmelerini (`PageHeader`) gösterir.
- Ekranda (kompozisyonda) durduğu sürece geçerlidir; ekrandan çıkınca varsayılan başlık geri gelir.
  Birden çok çağrı varsa en son eklenen kazanır, o çıkınca bir öncekine dönülür.
- Geri düğmesi 48 dp; başlık tek satır, sığmazsa "…" ile biter ve değişince yumuşakça geçer.
- En çok üç simge görünür: dört ya da daha fazla eylemde ilk ikisi (önce `primary` olanlar) kalır,
  gerisi "⋮" menüsüne gider. `label` hem erişilebilirlik adı hem ipucudur (fareyle üzerine gelince
  ya da uzun basınca görünür).
- Sistemin Geri hareketi `onBack`'i çağırır; uygulamanın ayrıca `BackHandler` yazması gerekmez.
- Ekranın hangi düzende olduğu: `currentWidthClass()` ya da `LocalWidthClass.current`
  (`DesktopShell` ve `DesktopKitRoot` sağlar).

### Sayfa başlığı: `PageHeader`

- Kenar çubuklu düzende (büyük pencere, DeX) başlığı büyük yazar, yanında alt başlık ve eylemler.
- Ray ve telefon düzeninde başlık zaten üst çubukta olduğu için **tekrar yazılmaz**; yalnızca alt
  başlık (küçük) ve eylemler kalır.
- Telefonda alt başlığı ve eylemi olmayan bir `PageHeader` hiç yer kaplamaz (önceden boş bir şerit
  bırakıyordu). Ekranın kendi başlığını telefonda göstermek için `CompactTopBar(title = …)` kullan.

### Formlar

```kotlin
DeskTextField(klasör, { klasör = it }, Modifier.fillMaxWidth(), label = "Müzik klasörü", clearable = true, mono = true,
    error = !geçerli, supportingText = if (geçerli) null else "Bu cihazda böyle bir klasör yok")
DeskSearchField(sorgu, { sorgu = it })                          // Esc temizler
NumberField(tampon, { tampon = it }, label = "Ön tampon", min = 0, max = 5000, suffix = "ms", steppers = true)
SwitchRow("Sesi DAC'a doğrudan gönder", açık, { açık = it }, subtitle = "Ses düzeyi DAC'tan ayarlanır")
CheckboxRow("Tüm biçimler", ToggleableState.Indeterminate, onClick = { … })
```

Klavyenin eylem tuşu sistemdeki işini yapar (Bitti klavyeyi kapatır, İleri sonraki alana geçer); `onImeAction`
verilirse onun yerine o çalışır, tek satırda donanım Enter'ı da. `NumberField`'ın aralık uyarısı alanı genişletmez.

Alanın etiketi, ipucu ve açıklaması TalkBack'e alanla birlikte okunur. Tek başına bir `DeskCheckbox` ya da
`DeskSwitch` kullanılacaksa ona `Modifier.semantics { contentDescription = … }` ile ad ver; satır hâlleri bunu kendisi
yapar.

### Bildirimler

```kotlin
val toasts = rememberToastState()
DesktopShell(…, toasts = toasts) { … }

scope.launch {
    val sonuç = toasts.show("“Pencere” sıranın sonuna eklendi", actionLabel = "Geri al", shortcut = "Ctrl+Z")
    if (sonuç == ToastResult.ActionPerformed) geriAl()
}
```

Bildirimler sırayla gösterilir; telefonda alt çubuğun ve oynatıcının, tablet ve DeX'te oynatıcının üstünde çıkar.

### Komut paleti

```kotlin
val navController = rememberNavController()
val navigator = remember(navController) { ShellNavigator(navController) }   // paletten ekran açmak için
val palet = rememberCommandPaletteState()
DesktopShell(…, navController = navController, onSearch = { palet.open() }, overlays = {
    CommandPalette(palet, items = { sorgu ->
        ekranlar.map { PaletteItem(it.route, it.label, "Ekranlar", { navigator.navigate(it.route) }, icon = it.icon) } +
            albümler.map { PaletteItem(it.id, it.ad, "Albümler", { aç(it) }, subtitle = it.sanatçı) } +
            shortcuts.paletteItems(resources) { çalıştır(it) }
    })
})
```

Arama Türkçe harfleri katlar ("sarki" "Şarkı"yı bulur), eşleşen harfleri vurgular ve sonuçları gruplar. Klavye
odağı alanda kalır: ↑/↓, Enter, Ctrl+Enter (`onOpenInNewWindow` varsa yeni pencerede), Tab ile grup süzgeçleri,
Esc. Palet kapanınca odak açılmadan önceki yerine döner.

### Kapak ızgarası

```kotlin
DeskGrid(albümler, key = { it.id }, onItemClick = { albüm, _ -> aç(albüm) },
    selectedKeys = seçili, onSelectionChange = { seçili = it }, itemMenu = { albüm, _ -> menü(albüm) },
    loading = yükleniyor) { albüm ->
    CoverCell(albüm.ad, subtitle = albüm.sanatçı, onPlay = { çal(albüm) }, playLabel = "Çal") { Kapak(albüm) }
}
```

Seçim kuralları tablonunkiyle aynı (Ctrl/Shift ile tıklama, Shift ile oklar, Ctrl+A, Esc). Oynat düğmesi
farenin altında çıkar; TalkBack onu hücrenin eylemlerinde bulur, klavye için `itemMenu`'ye de "Oynat" koy (Menu
tuşu menüyü açar).

### Sürükle bırak

```kotlin
DenseTable(…, rowDrag = { dosya, _ -> DeskDragPayload("file", listOf(dosya.ad), dosya.ad) })
Box(Modifier.dropZone(setOf("file"), onDrop = { yük -> taşı(yük.keys); true })) { … }
DesktopShell(…, navDrop = NavDrop(mapOf("bin" to setOf("file"))) { rota, yük -> çöpeAt(yük.keys); true })
```

Farede düğme basılıyken birkaç piksel kayınca başlar; dokunmada uzun basma menüyü açmaya devam eder. Sürükleme
uygulamanın öbür penceresine de geçer (`DeskDragPayload.from(event)`); başka uygulamalar yalnızca etiketi düz metin
olarak alır, tür ve anahtarlar uygulamanın dışına çıkmaz.

### Birden çok pencere ve başlık çubuğu

```kotlin
val appSpec = ShellSpec(…, startRoute = DeskWindows.startRoute(intent))   // yeni pencere kendi ekranında açılır
DeskWindows.openNewWindow(activity, route = "albums")                        // Ctrl+N: CommonShortcuts.NewWindow
```

DeX'te ve masaüstü pencerelemede pencere ayrı açılır, tablette yan yana. Tema, vurgu ve yoğunluk pencereler
arasında eşitlenir. Kenar çubuklu düzende menü çubuğu pencerenin başlık çubuğuna yerleşir (Android 15+, sistem
başlık düğmelerinin yerini bildirdiğinde); menüler ve arama dokunuşları uygulamaya gider, çubuğun boş yeri pencereyi
taşır. Liste–ayrıntı geçişinde kapak ve başlık uçar: listede ve ayrıntıda aynı anahtarla
`Modifier.detailSharedElement("kapak-$id")` ve `Modifier.detailSharedBounds("başlık-$id")`.

## Geliştirme

- `./gradlew :kit:testDebugUnitTest`: kontrast, boyut, kabuk durumu, kısayol ve erişilebilirlik testleri ile
  ekran görüntüleri (Robolectric, API 36; Java 21 gerekir). Görüntüler `kit/build/outputs/screenshots` altına yazılır.
- CI her gönderimde derler, testleri çalıştırır ve görüntüleri `kit/src/test/screenshots`
  ile karşılaştırır. `main` dalında görüntüler `ci/screenshots` dalına konur; örnek APK de
  Actions çıktısı olarak indirilebilir. Çekme isteklerinde görüntüler `ci/screenshots-pr-<numara>`, derleme çıktısı
  ve test sonuçları `ci/logs-pr-<numara>` dalına konur. Görünüm bilerek değiştiyse yeni görüntüleri
  `kit/src/test/screenshots` altına kopyalayıp gönder.

## Sürüm yayınlamak

1. `CHANGELOG.md` dosyasına ne değiştiğini yaz, `gradle.properties` içindeki `VERSION_NAME`'i güncelle.
2. GitHub'da **Releases → Draft a new release** ile etiketi (`2.0.0`) `main` üzerinde oluştur; ya da
   `git tag 2.0.0 && git push origin 2.0.0`.
3. JitPack ilk istekte derler (`jitpack.yml`, Java 17). Uygulamada sürümü `2.0.0` yapman yeterli.

Sürüm numaraları [SemVer](https://semver.org/lang/tr/) kuralına uyar: uyumu bozan değişiklik
ana sürümü, yeni özellik ikinci sayıyı, düzeltme üçüncüyü artırır.

## Lisans

Kod: [Apache License 2.0](LICENSE). Yazı tipi: IBM Plex, [SIL Open Font License 1.1](licenses/OFL-IBM-Plex.txt).
Buzlu cam: [Haze](https://github.com/chrisbanes/haze), Apache License 2.0.
Ayrıntılar [NOTICE](NOTICE) dosyasında.
