# desktop-kit

Android tabletler ve Samsung DeX için **masaüstü sınıfı** Jetpack Compose arayüz kiti. Nağmedar'ın
arayüzünden çıkarıldı. Klavye, fare, sağ tık ve dokunmatik aynı uygulamada birlikte çalışır.

- **Uyarlanabilir kabuk:** dar pencerede alt çubuk, orta genişlikte yan ray, DeX ve büyük
  ekranda menü çubuğu ile daraltılabilir kenar çubuğu. Pencere boyutu değişince açık ekran, geri
  yığını ve kaydırma konumu korunur. Sığmayan ekranlar otomatik bir **"Daha fazla"** ekranında listelenir.
  Ekran geçişleri canlandırılır (telefonda fade-through, geniş pencerede kısa geçiş); telefonun üst
  çubuğunu ekran kendisi değiştirebilir (`CompactTopBar`).
- **İki yoğunluk:** fare veya touchpad kullanılınca sıkı düzen (24 dp hedef), parmakla
  dokununca geniş düzen (48 dp). Geçiş otomatiktir; kullanıcı ayarlardan sabitleyebilir.
- **Tema:** açık, koyu ve OLED siyah. Dört hazır vurgu rengi var; istediğin markanın rengini de
  verebilirsin. Tüm metinler WCAG AA kontrastını (4.5:1) testle sağlar.
- **Bileşenler:** düğme, simge düğmesi, segment ve renk seçimi, kaydırıcı (tekerlek ve ok
  tuşları), yoğun tablo (sıralama, seçim, satır menüsü), bölünmüş panel, menü çubuğu, sağ tık
  menüsü (sağ tık, iki parmak dokunma, uzun basma, Menu tuşu), ipucu (tooltip), ayar satırı.
  Her birinde üzerine gelme, odak halkası ve basılı durumu var ve testle denetleniyor.
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
    implementation("com.github.hesaplarimdijital-coder:desktop-kit:0.1.3")
    implementation("androidx.activity:activity-compose:1.9.3")
}
```

Gereksinimler: `minSdk` 21 ve Compose açık (`buildFeatures { compose = true }` ile Kotlin Compose eklentisi).

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

`AndroidManifest.xml` içinde DeX'te pencere boyutlandırılınca etkinlik yeniden başlamasın diye:

```xml
<activity
    android:name=".MainActivity"
    android:resizeableActivity="true"
    android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|density|keyboard|keyboardHidden|navigation|uiMode|fontScale"
    android:windowSoftInputMode="adjustResize" />
```

Tam örnek (kısayollar, menü çubuğu, tablo, sağ tık, ayarlar) `sample/` klasöründe. Yeni bir
uygulamaya başlarken onu kopyalayıp ekranları değiştirmek en hızlı yol.

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
| Yazı boyutları | `DesktopKitRoot(prefs, type = { mode, fonts -> DeskType.forMode(mode, fonts, pointerStep = 1f) })`: fare ve klavyede her yazı 1 sp büyür, dokunmatik değişmez. `pointerStep`/`touchStep` her stile (display'den mono'ya) o kadar sp ekler, satır yüksekliği oranını korur. Tek tek ayar için `{ mode, fonts -> DeskType.forMode(mode, fonts).copy(body = …) }`. Yüksekliği yazıya göre büyüyen bileşenler (tablo satırı, düğme, menü, üst çubuk) kırpılmaz. |
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
| `DenseTable` | Sabit başlık, sıralama, seçili satır, `rowMenu` ile satır başına sağ tık menüsü. Çoklu seçim için aşağıya bak. |
| `HorizontalSplitPane`, `TwoPane` | Sürüklenebilir ve klavyeyle taşınabilir ayırıcı |
| `ContextMenuArea`, `Modifier.contextMenuGesture` | Sağ tık, uzun basma, Menu tuşu, Shift+F10 |
| `MenuPopup`, `MenuBarMenus`, `MenuEntry` | Kenarda taşmayan menüler |
| `TooltipArea` | Yalnızca fareyle görünür, kısayolu da gösterir |
| `PageHeader`, `MessageScreen`, `OverflowScreen` | Hazır ekran parçaları. `PageHeader` başlığı yalnızca kenar çubuklu düzende gösterir (aşağıya bak). |
| `CompactTopBar`, `TopBarAction` | Telefonda üst çubuğun başlığı, geri düğmesi ve simgeleri (aşağıya bak) |
| `AppearanceSection`, `InputSection`, `ShortcutListSection`, `SettingsSection` | Hazır ayar bölümleri |

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
ekranlar arasında fade-through: eski ekran 90 ms'de solar, yenisi 210 ms'de belirirken %98'den tam
boyuta büyür. Ray ve kenar çubuklu düzende (tablet, DeX) hareketsiz, 150 ms'lik kısa bir geçiş.
Geri dönüş aynı biçimde. Süreler sistemin animasyon ölçeğine uyar ("Animasyonları kaldır" açıksa
geçiş olmaz). Alt çubuk, ray ve kenar çubuğundaki seçim rengi 150 ms'de yumuşakça değişir.
`ShellTransitions.None` geçişi kapatır (ekran görüntüsü testleri için).

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

## Geliştirme

- `./gradlew :kit:testDebugUnitTest`: kontrast, boyut, kabuk durumu ve kısayol testleri ile
  ekran görüntüleri. Görüntüler `kit/build/outputs/screenshots` altına yazılır.
- CI her gönderimde derler, testleri çalıştırır ve görüntüleri `kit/src/test/screenshots`
  ile karşılaştırır. `main` dalında görüntüler `ci/screenshots` dalına konur; örnek APK de
  Actions çıktısı olarak indirilebilir. Görünüm bilerek değiştiyse yeni görüntüleri
  `kit/src/test/screenshots` altına kopyalayıp gönder.

## Sürüm yayınlamak

1. `CHANGELOG.md` dosyasına ne değiştiğini yaz.
2. Etiketle ve gönder: `git tag 0.2.0 && git push origin 0.2.0`
3. JitPack ilk istekte derler. Uygulamada sürümü `0.2.0` yapman yeterli.

Sürüm numaraları [SemVer](https://semver.org/lang/tr/) kuralına uyar: uyumu bozan değişiklik
ana sürümü artırır. `1.0.0` öncesinde ikinci sayı artırılır.

## Lisans

Kod: [Apache License 2.0](LICENSE). Yazı tipi: IBM Plex, [SIL Open Font License 1.1](licenses/OFL-IBM-Plex.txt).
Ayrıntılar [NOTICE](NOTICE) dosyasında.
