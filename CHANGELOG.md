# Değişiklikler

## 2.8.0 (2026-11-13)

Tasarım standardının denetimi ("Desktop Kit Tasarım Standardı" belgesinin son bölümü) ve DeskMail'in kitten
istedikleri (`docs/KIT-ISTEKLERI.md`), tek sürümde: DeskMail'in 23 maddesinin hepsi, denetimin yedi yüksek bulgusunun
hepsi ve orta ve düşük bulguların çoğu (kalanlar denetim bölümünün sonunda). 2.6.0 ve 2.7.0 yayımlanmadı; ikiye bölünmüş iş burada
birleşti. Uygulamalar çoğunlukla kod değişmeden derlenir; görünüşte ve klavyede değişenler ve dikkat isteyen birkaç
imza [MIGRATION.md](MIGRATION.md#250den-280a)'de. Kod değişmeden de uygulamanın görünüşünü ya da klavyesini
değiştirenler aşağıda "görünür" diye işaretli.

### Hatalar

- **Dolgu iki kat oluyordu** (DeskMail 12): ekran düzenini `BoxWithConstraints` içinde seçip
  `rememberShellContentPadding()`'i oradan isteyince istek kabuk ekranı ölçerken geliyordu. Compose bir düğümün
  ölçülürken kendisine yaptığı yeniden ölçme isteğini yok sayar; kabuk ekranı da dolgulayıp öyle bırakıyordu: liste
  bir üst çubuk boyu aşağıda başlıyor, bir alt çubuk boyu yukarıda bitiyordu. Kabuk sayacı artık bileşimde de okur:
  değişince kendini yeniden bileşir, ekran bir sonraki karede pencerenin tamamıyla ölçülür; o tek karede ekran
  pencerenin üstünden başlar. Test: `ShellPaddingTest.aScreenAskingFromASubcompositionIsNotPaddedTwice`.
- **Bölücü sınırın dışına taşıyordu** (DeskMail 13): `HorizontalSplitPane` sürüklemeyi duruma ekleyip sınırı yalnızca
  ölçerken uyguluyordu; en küçük genişliği geçen sürükleme saymaya devam ediyor, geri sürüklerken o kadar yol boşa
  gidiyordu. Durum sınırları her yerleşimden öğrenir; sürükleme, ok tuşları ve TalkBack'in `setProgress`'i onların
  içinde kalır. Sağdan sola düzende oklar ve sürükleme ters yöne çalışır. Bölücünün TalkBack adı verilmezse
  "Bölmeleri boyutlandır". `rememberSplitState(genislik, prefs, anahtar)` yerini `DeskPreferences`'ta saklar
  (sürükleme ya da tuş bitince). Test: `SplitPaneTest`.
- **Telefonun üst çubuğu ekran değişirken iki kez değişiyordu** (DeskMail 18, S24 kayıtları): bırakılan ekranın
  `CompactTopBar` çağrısı ekranın çıkışı bitene kadar kalıyordu; başlık rota ile hemen, geri düğmesi ve eylemler
  çeyrek saniye sonra değişiyor, başlık her seferinde solup yeniden geliyordu ("← Klasörler" ve eski ekranın kalemi,
  sonra boş çubuk, sonra "Klasörler"). Bırakılmakta olan ekranın çağrısı artık sayılmaz: geçerli rota olmayan sekme,
  ayrıntı açıkken liste, uygulamanın kapattığı ayrıntı (`LocalScreenLeaving`). Karar uygulamanın durumuna bakar,
  animasyona değil: Geri hareketiyle parmağın çektiği ekran çubuğunu hareket bırakılana kadar korur. Başlık artık
  bütün hâlinde solmaz: geri düğmesi başlığın önüne kayarak gelir, başlık yana kayar; başlık yalnızca sözcükleri
  değişince solup gelir ("Gelen" › "← Gelen" artık solmuyor). Testler: `CompactTopBarTest.aLeftScreensBarGivesWayAtOnce`,
  `theSameTitleDoesNotFadeWhenTheBackButtonComes`.
- **Tam sayfa yerine otururken taşıyordu** (DeskMail 19): ayrıntı sayfası 0,9 sönümlü yayla geliyordu; telefonun
  genişliğini geçen bir sayfa yerini 2 piksel geçip geri dönüyordu (85 ms), yazıyla dolu sayfada titreme gibi
  okunuyordu. Yeni `DeskMotion.page()` (1,0; 800) yerini geçmez, biraz daha sert olduğu için aşağı yukarı aynı sürede
  varır; ayrıntının kayması ve arkasındaki listenin derinliği ile perdesi bunu kullanır. Uçan küçük öğeler `open()`'da
  kalır.
- **Cam ve WebView** (DeskMail 20): kabuk içeriği cam için kaydeder, her cam yüzey onu bir kez daha, kendi boyunda ve
  kırpmasında çizer. WebView her çizimi yeni bir görüntü alanı sanıp karolarını atıyordu: S24'te cam çubukların
  altından kaydırılan ileti saniyede 59 kez tam, eksik ve boş arasında gidip geliyordu. `PauseGlassBlur()` (isteğe
  bağlı `enabled`) onu çağıran ekran açıkken camı yarı saydam tonuna çevirir ve içeriği kaydetmeyi bırakır; geri
  kalan uygulama bulanıklığını korur, kabuğun başka hiçbir yeri yeniden bileşmez. Pil tasarrufundaki opak yüzeyler
  opak kalır. Test: `GlassPauseTest`.
- **Ayrıntı açılınca listenin durumu gidiyordu** (DeskMail 9): telefonda ayrıntı listenin üstüne tamamen gelince
  liste bileşimden çıkıyordu, `AnimatedContent` da çıkardığının kayıtlı durumunu saklamıyor: geri dönünce kaydırma
  konumu, açık bölümler, yazılmış süzgeç sıfırlanıyordu (Ayarlar'da aşağı kaydırıp günlüğü açınca Ayarlar en başa
  dönüyordu). Liste artık bir `SaveableStateHolder` içinde durur, Navigation'ın ekranlar için yaptığı gibi; her
  genişlikte. Test: `DetailListStateTest`.

### DeskMail'in istekleri

- **Değişen gezinme** (DeskMail 3, hepsi):
  - Kabuk gezinme grafiğini bir kez kurar; `ShellSpec`'e eklenen rotalar grafiğe eklenir. Yeni klasör ya da hesap açık
    ekranı, geri yığınlarını ve her ekranın durumunu korur; başlangıç ekranı bir an görünmez. Başlangıç rotası
    değişirse grafik yeniden kurulur. Çıkan bir rotanın ekranı gösterildiği anda kendini kapatır, altındaki ekran
    görünür; gezinme onu listelemez. Kayıtlı bir geri yığınında kalmış olsa da (telefonda Klasörler'den açılan, sonra
    başka sekmeye geçilip silinen klasör) o sekmeye dönmek çökmez.
  - `NavItem(parent)`: kenar çubuğu öğeyi üst öğesinin altına girintili koyar; öğesini göstermeyen gezinme (telefonda
    bir klasör) onun gösterilen en yakın üst öğesini işaretler, "Daha fazla"yı değil. `ShellNavigator.open(rota)`
    ekranı gösterilenin üstüne açar (telefonda Klasörler › klasör): sayfanın kendi geri düğmesi olur, Geri geri gelir,
    sekmenin girişine yeniden dokunmak sekmenin kendi ekranına döner.
  - `NavItem(showIn)`: öğeyi hangi gezinmelerin listelediği. Klasörler yalnız kenar çubuğunda, "Klasörler" ve "Ara"
    yalnız alt çubukta ve rayda; bir gezinmede olmayan öğe onun "Daha fazla"sında da yok.
  - `DesktopShell(navMenu)`: kenar çubuğu, ray ve alt çubuk girişinin menüsü ("Tümünü okundu say", "Çöpü boşalt"):
    sağ tık, uzun basma, Menü tuşu, TalkBack eylemi.
  - `NavSection(collapsible = true)`: hesabın başlığı bölümü açıp kapatır (TalkBack'in aç/kapat eylemleriyle);
    kapalı bölüm işaretli girişi göstermeye devam eder. Daraltılmış kenar çubuğunda (yalnız simgeler) bölümü açacak
    başlık olmadığından bölümün bütün girişleri görünür.
  - "Daha fazla" ekranı ekranları bölüm başlıklarının altında listeler; iki hesabın "Gelen"i artık alt alta durmuyor.
  - Test: `NavStructureTest`.
- **Çipli adres alanı ve öneriler** (DeskMail 4): `TokenField` adresleri çip olarak tutar: virgül, noktalı virgül ya
  da Enter çipi tamamlar, yapıştırılan liste bölünür, Backspace boş alanda son çipi siler, geçersiz adres işaretlenir
  ("Geçersiz"), her çipin × düğmesi ve TalkBack eylemi vardır; yazarken alanın altında oklarla gezilen, Enter'la
  seçilen, Esc'le kapanan öneri listesi (`suggestions`; yalnızca yazılan değişince istenir, uygulama bir veritabanına
  bakabilir). Yapıştırılan listede iki kez geçen adres tek çip olur. Test: `TextInputRequestsTest`.
- **Telefonda yüzen birincil düğme** (DeskMail 5): `ShellPrimaryAction(etiket, simge, onClick)`, ekranın çağırdığı
  ana eylem. Telefonda alt çubuğun ve oynatıcının üstünde, sonda yüzen vurgu renkli düğme: çubuklar kaydırmayla
  çekilirken onlarla iner, liste en üstteyken etiketiyle, kaydırılınca yalnız simgesiyle durur, ekran klavyesi
  açıkken ve büyüyen oynatıcının altında görünmez; çubukların altından geçen listeler onun bitiminde biter,
  bildirimler onun üstünde durur. Rayda üstte vurgu renkli simge (ipucuyla), kenar çubuğunda üstte etiketli düğme
  (kapalıyken simge). Test: `PrimaryActionTest`.
- **Liste satırında kaydırma eylemleri** (DeskMail 7): `DeskListRow(swipeStart, swipeEnd)` `SwipeAction` alır. Satır
  parmağı izler, altındaki eylemi açar; genişliğinin %35'ini geçince eylem rengini alır (tehlikeliyse kırmızı),
  simgesi büyür, telefon titrer; orada ya da hızlı bir fırlatmayla bırakılınca çalışır. Satırı listeden çıkaran eylem
  (`removesRow`, arşivle, sil) satırı dışarıda bırakır, ötekisi (okundu say) geri getirir. Seçim kipinde ve fareyle
  yok; TalkBack her eylemi adıyla sunar. Geri alma uygulamanın bildirimindedir. Test: `SwipeActionsTest`.
- **Çekerek yenileme** (DeskMail 14): `PullToRefresh(refreshing, onRefresh) { liste }`. Listenin tepesinden aşağı
  çeken parmak bir disk indirir: liste telefonun cam üst çubuğunun altından geçiyorsa çubuğun altından, değilse
  (üstünde bir alan ya da sekmeler varken) listenin kendi tepesinden; disk listenin dışına çizilmez. Yayı çekişle
  dolar; yay tamamlanınca (bir titreşimle) bırakmak `onRefresh`'i çalıştırır, disk yenileme sürerken döner, sonra
  çekilir. Uygulama kendi başına yenilerken de iner. Çekiş direnir, yukarı itmek önce diski kaldırır; fare, dokunmatik
  yüzey ve klavye etkilemez. Azaltılmış harekette disk yerine atlar. TalkBack dönen diski "Yenileniyor" diye okur.
  Test: `PullToRefreshTest`.
- **Tabloda çerçeveyle seçme** (DeskMail 22): seçili olmayan bir satırdan (ya da satırların altından) sürüklenen fare
  bir çerçeve çizer, değdiği satırları seçer; Ctrl ekleyip çıkarır, Shift ekler, kenara yaklaşınca liste kayar, Esc
  çerçeveyi bırakıp önceki seçimi geri koyar. Seçili satır seçimi beklemeden sürükler (`rowDrag`). Test:
  `TableKeyboardAndFrameTest`.
- **Metin alanında imleç ve seçim** (DeskMail 23): `DeskTextField(value: TextFieldValue, onValueChange)` imlecin ve
  seçimin yerini bildirir ve alır (yanıtta imleci alıntının üstüne koymak, öneriyi seçince imleci sona almak).

- **Rozetler alt çubukta ve rayda** (DeskMail 1): `DesktopShell(iconBadges = mapOf(rota to NavIconBadge(sayı,
  açıklama)))` simgenin köşesine vurgu renginde küçük bir sayı (99'dan sonra "99+") ya da sayı 0 ise nokta koyar;
  alt çubukta, rayda ve kapalı kenar çubuğunda. Açık kenar çubuğu yazılı rozeti (`badges`) gösterir. "Daha fazla"nın
  tuttuğu bir ekranın rozeti varsa "Daha fazla"da nokta olur. TalkBack girişin adını ve rozeti okur ("Gelen, 4
  okunmamış"; açıklama yoksa "4 yeni"). Test: `NavIconBadgeTest`.
- **Tabloda vurgu, simge hücresi, hücreye tıklama** (DeskMail 2): `DenseTable(rowEmphasis)` satırı baştan sona kalın
  ve yazı renginde yapar (okunmamış ileti). `TableColumn(cell)` hücreyi uygulama çizer (yıldız, ataç, rozet); değer
  sıralamaya ve TalkBack'e kalır. `TableColumn(onCellClick, cellClickLabel)` hücreyi satırı açmayan kendi düğmesi
  yapar (yerinde yıldızlama), üzerine gelme, basılı ve odak durumlarıyla. Test: `DenseTableRequestsTest`.
- **Şeritte ikinci eylem** (DeskMail 6): `StatusBanner(secondaryActionLabel, onSecondaryAction)`; telefonda iki eylem
  metnin altında, sığmazsa alt alta.
- **Liste satırında önizleme** (DeskMail 8): `DeskListRow(subtitleMaxLines, preview, previewMaxLines)`.
- **Türkçe klavyede "/"** (DeskMail 10): noktalama kısayolları (`/ , . - = + [ ]`) yazdıkları karakterle de eşleşir;
  Ctrl, Meta ve sol Alt bir yana, tutulan tuşların yazdığı karaktere bakılır, Shift ve AltGr o karakteri yazmak için
  basılı olabilir. Kitin Ctrl+/ kısayolu (kısayol listesi) Türkçe Q klavyede de çalışır. `KeyChord(char = …)` başka
  bir tuşa karakter verir. Test: `KeyChordCharTest`.
- **Ayrıntı paneli kapalı başlayabilir** (DeskMail 11): `rememberInspectorState(defaultCollapsed = true)`; kullanıcı
  bir kez açınca saklanan seçim geçerli.
- **Rayda sayfa başlığı** (DeskMail 15): `CompactTopBar(showOnRail = true)` tablette (ray düzeninde) de başlığı, geri
  düğmesini ve sistemin Geri'sini alır; kenar çubuklu düzen (DeX) hiç almaz.
- **Boş ekranda vurgulu düğme** (DeskMail 16): `MessageScreen(actionPrimary = true)`. Başlığı TalkBack için başlık.
- **Ayar satırı düğme, sakıncalı düğme** (DeskMail 17): `SettingRow(onClick)` satırın tamamını bir düğme yapar,
  sonunda ok; üzerine gelme, basılı durum ve odak halkası satırın kenarlarından biraz taşar, böylece yazısı öteki
  satırlarla aynı hizada kalır; denetim artık isteğe bağlı. `DeskButton(danger = true)`: yazı, simge ve kenar
  tehlike renginde, üzerine gelme ve basılı durum o renkle; hiçbir zaman dolu değil.
- **Bildirimin süresi** (DeskMail 21): `ToastState.show(durationMillis = …)` tam o kadar durur; TalkBack kullanan biri
  için ötekiler gibi uzar.

### Denetimden: yüksek bulgular

- **Liste ve ayrıntı yan yana** (1): `ListDetailLayout(detail, onBack, list) { ayrıntı }` tablet genişliğinden
  (`twoPanesFrom`, varsayılan Expanded) itibaren listeyi ve seçilenin ayrıntısını yan yana koyar; liste 360 dp
  (Large'dan itibaren 412), aradaki bölücü sürüklenir, `prefs` ve `splitKey` ile yeri saklanır. Hiçbir şey seçili
  değilken ayrıntı bölmesi `placeholder`'ı gösterir; yeni seçim ayrıntıyı çapraz geçişle değiştirir (art arda seçimlerde
  geçişteki her ayrıntı kendi durumunu tutar). Daha dar pencerede, ya da pencere geniş olduğu hâlde yerleşime iki
  bölmenin en küçük genişlikleri (`minListWidth` + `minDetailWidth`) sığmıyorsa (yanında açık bir ayrıntı paneli),
  `DetailTransition` gibi tek bölme. Liste ve açık ayrıntı pencere eşiği geçerken (DeX'te yeniden boyutlandırma,
  tablet çevrilince) durumlarıyla taşınır; liste görünmediği sürece de (telefonda ayrıntının altında) kaydırma yerini
  ve yazılanı korur. Test: `ListDetailLayoutTest`.
- **Tab tuşu ve odak** (2, görünür): tablo, kapak ızgarası ve sekme şeridi Tab'ın tek durağıdır: tabloda ve ızgarada
  en son odaklanan (yoksa seçili, görünürse; yoksa görünen ilk) satır ya da kapak, şeritte seçili sekme. İçlerinde
  oklar, Home, End ve sayfa tuşları gezer (sağdan sola şeritte Home sağdaki ilk sekmeye gider). Tab ve Shift+Tab
  hemen dışarı çıkar, satırın ya da kapağın kendi düğmesinden de: yıldız hücresi, Oynat ve "⋯" Tab durağı değildir;
  uzun tablo bir sonraki durak için satır satır aranmaz. Tablonun sıralanabilir başlıkları Tab sırasında satırlardan
  önce gelir. Açık alt sayfa klavye odağını tutar: Tab ve Shift+Tab arkadaki ekrana geçmez; sayfa kapanınca odak
  açılmadan önceki yerine döner. Testler: `TableKeyboardAndFrameTest`, `FocusStopsTest`.
- **Kaydırma çubuğu** (3, görünür): fare ya da dokunmatik yüzeyle (DeX, klavyeli kapak) listenin sonunda ince bir çubuk nerede
  olunduğunu ve içeriğin ne kadarının göründüğünü gösterir; liste kayarken ve imleç üstündeyken görünür, sonra solar,
  imlecin altında kalınlaşır, sürüklenir; çubuğun boşluğuna basmak oraya gider. Dokunmatikte yok. `DenseTable`,
  `DeskGrid`, palet, menüler, alt sayfalar ve "Daha fazla" ekranında kendiliğinden; uygulama kendi listesine
  `Modifier.verticalScrollbar(state)` koyar. Çubuğa basmak satır tıklaması, çerçeve ya da sürükleme değildir. Test:
  `ScrollbarTest`.
- **İpuçları** (4, görünür): simge düğmeleri kendini adlandırır: imleç durunca (yarım saniye) ve klavye odağında ad,
  varsa kısayolla (`DeskIconButton(shortcut)`); bir ipucu açıkken komşusuna geçen imleç hemen görür. Kapalı kenar
  çubuğunun girişleri adlarını yanlarında gösterir. `TooltipArea(placement, showOnFocus, enabled)`; uygulamanın kendi
  `TooltipArea`'sı içindeki simge düğmesi ikinci bir ipucu açmaz. Test: `TooltipTest`.
- **Kontrollerin kenarı** (5, görünür): yeni `controlBorder` rolü, girdinin dinlenmedeki kenarı: açık, koyu ve OLED temada
  arka plana, kroma ve yüzeylere en az 3:1 (`borderStrong` 1,4:1'di). Metin alanları, çipli alan ve anahtarın kapalı
  yolu bunu kullanır; düğmeler, paneller, menüler `borderStrong`'da kalır. Elle kurulan paletler eskisi gibi
  `borderStrong` alır. Test: `PaletteContrastTest.inputsCanBeFoundByTheirEdge`.

- **Alt sayfa jestsiz kapanır, tek sayfa açık kalır** (6): tutamak aynı zamanda sayfanın kapatma
  düğmesidir ("Sayfayı kapat"): odaklanır, odak halkası var, TalkBack, Anahtar Erişimi ve Tab ulaşır; görünüşü ve
  yeri aynı. Perde, üzerine dokununca ne olacağını TalkBack'e söyler. Kabuğun içinde bir sayfa açılırken açık olan
  sayfa kapanır. Köşeye görünür bir × bilerek konmadı: tutamak bu işi kitin sayfalarının görünüşünü değiştirmeden
  görüyor. Test: `SheetAccessTest`.
- **Görünür komutlar** (7, görünür): `DeskListRow(moreButton = true)` satırın sonuna menüyü açan "⋯" koyar. Kapak
  hücresinde Oynat ve "⋯" klavye odağında da görünür; dokunmatikte başlığın yanında "⋮" düğmesi var. "⋯" ve "⋮"
  yalnızca menüsü olan kapakta.

### Denetimden: orta ve düşük bulgular

- **Fareyle sürükleme beklemeden** (8): seçili satır ve kapak fareyle hemen sürüklenir; parmağın beklemesi
  dokunmatikte kaldı.
- **Baseline Profile** (9): `kit/src/main/baseline-prof.txt` kitin kodunu kurulumda derletir: ilk tablo kaydırması,
  ilk alt sayfa ve ilk paylaşılan öğe yorumlanarak çalışmaz. Cihazda ölçülmedi (macrobenchmark cihaz ister).
- **Yükseklik sınıfı** (10, görünür): `HeightClass` (480 dp altı Compact, 900'den Expanded) `DesktopKitRoot`'tan
  `LocalHeightClass` olarak gelir; pencerenin yüksekliğinden, ekran klavyesinin bıraktığından değil (bir alana
  dokununca telefon kısa pencere sayılmaz). Yan çevrilmiş telefon (S24, 780 × 360 dp) rayı alır ama tabletin büyük yazısını,
  zamanlı hareketini ve içeriğin üstünde yüzen oynatıcıyı almaz: telefonun yazısı ve yayları, 8 dp alçak üst çubuk
  (hedeften küçük olmadan), içeriğin altına dizilen oynatıcı. Test: `HeightClassTest`.
- **Dar pencerede ayrıntı paneli** (11): `WorkstationLayout`'un `showFrom`'undan dar pencerede `InspectorToggleButton`
  paneli ana bölmenin üstüne alt sayfa olarak açar (`InspectorState.sheetOpen`, başlığı `sheetTitle`); sayfa açıkken
  genişleyen pencere paneli yana alır. Eskiden panel o genişlikte hiç yoktu. Test: `InspectorSheetTest`.
- **Bildirim imleç ve odak için bekler** (12): imleç bildirimin üstündeyken ya da klavye odağı içindeyken süre
  durur, sonra kaldığı yerden sayar. DeX'te "Geri al" fareyle ulaşılmadan gidiyordu. Dokunmatikte kapatma düğmesi
  48 dp (40'tı); simge aynı boyda. Test: `ToastTimingTest`.
- **Uzun değer ortadan kısalır** (13): `TableColumn(ellipsis = TextOverflow.MiddleEllipsis)`; dosya adı uzantısını,
  yol son klasörünü korur.
- **Üzerine gelme, basılı ve devre dışı** (14, görünür): `pressed` üzerine gelmenin bir adım ötesinde (koyuda 1,16:1, 1,06:1'di);
  açık temada üzerine gelme sayfada görünür (1,14:1, 1,10:1'di), basılı onun da ötesinde. Devre dışı `DeskButton` kendi
  dolgusunu almaz (birincilse sakin bir dolgu), yazısı üçüncül, kenarı soluk; etkin düğmenin yarı saydamı değil.
- **Rayda menü çubuğunun menüleri** (15, görünür): menü çubuğu yalnız kenar çubuklu düzende; tablette ve Expanded DeX
  penceresinde Dosya, Düzen ve ötekiler ulaşılamıyordu. Ray düzeninin üst çubuğunda "≡" ("Menüler") menülerin
  başlıklarını listeler, başlık menüsünü açar. Test: `RailMenusTest`.
- **Boş durum** (18): `DenseTable(emptyContent)` ve `DeskGrid(emptyContent)`; satır yokken ve bir şey yüklenmiyorken
  gösterilir. Boş tablo yalnızca başlığından ibaretti.
- **Gösterilen ekranın girişine yeniden dokunma** (19): `OnReselect { … }` ekranın kendi girişine yeniden dokunulunca
  çalışır: listeyi başa kaydırmak, ekranın başına dönmek için. Test: `ReselectTest`.
- **Tablonun başlığı ve seçimi** (21, görünür): başlık satır boyunda; seçili satırın renginin yanında başında bir çubuk var;
  seçim kipinde (`toggleOnClick`) her satırda onay kutusu, başlıkta üç durumlu "Tümünü seç". Tablo başlıkları
  TalkBack için başlıktır; sıralanan sütun yönünü söyler ("Artan sırada"), sıralanabilir olan dokununca ne
  olacağını ("Gönderen sütununa göre sırala").
- **Dokunma hedefi** (22, görünür): dokunmatikte sekmenin kapatma düğmesi 48 dp (32'ydi); bildirimin kapatma düğmesi 48 dp
  (40'tı).
- **Ham renkler** (26, görünür): bileşenlerde `Color(0x…)` kalmadı: resmin üstüne çizilen için `overlayScrim` ve `onOverlay`
  rolleri, vurgu seçicinin onayı için `DeskPalette.onSwatch`; açık temada anahtarın topu yüzey rengi.
- **Kapak ızgarasının büyüklüğü:** `DeskGrid(zoom = rememberGridZoom(prefs = prefs, key = "albumler"))` kapakları kullanıcının
  büyütüp küçültmesine bırakır: Ctrl ile fare tekerleği (ya da dokunmatik yüzeyde kıstırma), ekranda iki parmak,
  Ctrl ile + ve − (Ctrl+0 varsayılana döner), TalkBack'in "Kapakları büyüt/küçült" eylemleri. Tuşlar yazdıkları
  karakterle eşleşir (Türkçe Q'da − kendi tuşunda, + Shift+4'te), tuş takımınınkiler de. Boy çeyrek çeyrek değişir,
  4 dp ızgarasına oturur, saklanır. Kullanıcı bir boy seçmedikçe ızgara varsayılanı izler (parmakla daha büyük);
  seçtiği boy varsayılan değişince kalır, Ctrl+0 onu unutur. Test: `GridZoomTest`.
- **Komut paleti** (PER-3): liste başlıkları, sonuçları ve "hepsini göster" satırlarını her harfte kendi türlerine
  göre yeniden kullanır.
- **Metinler:** İngilizce "Close the player" → "Collapse the player" (oynatıcı yalnızca küçülüyor; Türkçesi doğruydu).
  Türkçe ayarlarda görünüm bölümü "Görünüş"; "Görünüm" menü çubuğundaki menü olarak kaldı.

Kalanlar: pencere başına başlık (`setTaskDescription`), sürükleme imleçleri, şeritlerde kaydırma okları, sistemin
kontrast ayarı, bölücü sürüklenirken Esc, katı katmanlarda kenar ve gölgenin ikisi birden (25), işaretçi kipinde
11 sp alt satırlar (24), test matrisindeki eksik pencere boyları (23), `Shortcut`'un `Command`'a genişlemesi (15'in
geri kalanı).

### Hareket


Standardın hareket kararları; telefonda ve genişte:

- `DeskMotion.spatial()` 0,85 yerine 0,9: gezinme hapı ve sekme çizgisi sekmez.
- `pressScale`: telefonda bırakılan denetim dinlenme boyunu geçmez; basma her yerde aynı hızlı yayla geri gelir
  (telefonda bırakınca hafifçe taşıyordu). Basmanın derinliği aynı.
- `ListAppear`: satırlar yalnızca veri liste göründükten sonra gelince sırayla gelir (`rememberListAppear`'ın
  anahtarları değişince: iskeletten sonra, aramanın sonuçları). Satırlarıyla birlikte görünen liste (yeniden açılan
  sekme, geri dönülen ekran, anahtarsız liste) satırlarını hemen gösterir; 2.5.0 her listenin ilk görünüşünü
  oynatıyordu. Testler: `ListAppearAndPressTest`.
- `DeskMotion.page()` (yukarıda): ayrıntı sayfası yerini geçmeden oturur.
- Kaydırma eylemli satır `gesture()` yayıyla bırakılır; yüzen düğme `open()` ile büyüyerek gelir, `fastSpatial()` ile
  gider; çekerek yenilemenin diski `page()` ile yerine oturur ya da çekilir, yalnızca yenileme sürerken döner.
  Azaltılmış harekette hepsi atlar.

### Tasarım standardı


- `DESIGN.md`: pencere sınıfları, boşluk ölçeği, ölçüler, köşeler, yazı, renk rolleri, cam, hareket, hangi iş için
  hangi bileşen ve kitte bugün 4 dp ızgarasının dışında kalan değerler, nedenleriyle.
- `AGENTS.md`: kitle uygulama yazan ya da kitte çalışan yapay zekâ yardımcıları için ortam ve kurallar.
- `.github/pull_request_template.md`: standardın kontrol listesi.
- `tools/design_check.py` CI'da ilk adım: tema dışında ham renk, `Toast.makeText`, boş `onClick`, iskelet dışında
  sonsuz animasyon, 4 dp ızgarasının dışında ölçü, "!" ile biten ya da "..." içeren metin. Bugün olanlar
  `tools/design_baseline.json`'da (115); fazlası derlemeyi durdurur ve yeni satırları gösterir.

### Örnek uygulama


Yapay zekâ yardımıyla yazılan uygulamaların ilk kopyaladığı yer:

- Dosya ayrıntısının eylemleri platformun `Toast`'unu kullanıyordu; Sil hiçbir şey yapmıyordu, Yeniden adlandır boş
  bir menü girişiydi. Sil (ayrıntının üst çubuğu, tablonun menüsü) dosyaları "Geri al"lı bir bildirimle çöpe taşır;
  örneğin yapmadığı eylemler bunu kitin bildirimiyle, adıyla söyler; Yeniden adlandır kaldırıldı.
- Boyut sütunu metni sıralıyordu, "1.2 GB" "12 KB"tan önce geliyordu; artık baytları sıralar. Uzun adlar ortadan
  kısalır.
- Oynatıcının Duraklat düğmeleri bir şey yapmıyordu: tek oynat/duraklat durumu, simge ve ad basınca ne olacağını
  söyler.
- Hakkında penceresi "Desktop Kit 2.0" diyordu: kitin sürümünü derlemeden (`gradle.properties`) alır.
- Manifest masaüstü penceresine varsayılan boyunu ve Android'in en küçüğünü (386 × 352 dp) verir.
- Dosyalar ekranı, bırakma bildirimleri ve oynatıcı Türkçe ve İngilizce (metin kaynakları, sayılar için `plurals`,
  kodda parça parça kurulan cümle yok). Bileşen kataloğunun örnek etiketleri İngilizce kaldı.
- Dosyalar ekranı tablet genişliğinden itibaren listeyi ve dosyanın ayrıntısını yan yana gösterir (`ListDetailLayout`,
  bölücünün yeri saklanır), seçim yokken "Burada görmek için bir dosya seçin"; parmakla aşağı çekilince yenilenir;
  ayrıntı panelinin düğmesi her genişlikte var, dar pencerede paneli alt sayfa olarak açar.
- Albümler ızgarası `rememberGridZoom()` ile büyütülüp küçültülür.

### Doğrulama, neyi kapsıyor

Kitin testleri (Robolectric): yukarıda adı geçen yeni testler ve var olanların hepsi, her gönderide CI'da yeşil.
Hiçbir değişiklik bir cihazda ya da emülatörde denenmedi. DeskMail'in kayıtlarında görülen üç sorunun (üst çubuk,
yayın taşması, WebView) kitteki nedeni koddan ve kayıtların kare kare dökümünden çıkarıldı; düzeltmenin cihazdaki
sonucu DeskMail kite geçince görülecek. Ekran görüntüleri görünüş değişiklikleri yüzünden (kenarlar, tonlar, tablo
başlığı, raydaki "≡", kaydırma çubuğu yok: görüntüler dinlenmede) farklı çıkar; referanslar CI'ın çektikleriyle
güncellendi. 2.4.1 (#10) ve 2.5.0 (#13) `main`'e birleşmeden bu PR birleştirilemez: üstlerine kuruldu.

## 2.5.0 (2026-11-13)

Komut paletinde bir grubun gerisi artık görünür: yalnız en iyi altısı gösterilen grubun altındaki satır kaç eşleşmesi
olduğunu yazar ve seçilince hepsini gösterir. 2.4.1'de bir grubun yedinci eşleşmesi gösterilmiyordu; bunu söyleyen bir
şey de yoktu, süzgeç (`filters`) vermeyen uygulamada ona götüren bir yol da. Kod değişmeden derlenir, API aynı
([MIGRATION.md](MIGRATION.md#241den-250a)). Paletlerde yeni bir satır ve yeni bir davranış göründüğü için sürüm ikinci
sayıyı artırır.

- **Nereden çıktı:** Nağmedar'da, 13 Kasım: "ctrl k arama sayfası düzgün çalışmıyor. Her sonucu vermiyor". Nağmedar'ın
  paleti kendi kodudur ve orada düzeltildi (Nağmedar 1.0.7): 64 parçalık bir kütüphanede saydığı 64 parçanın 6'sını
  gösteriyordu. Kitin paleti aynı kesintiyi yapıyor: `PaletteSearch.rows`, süzgeç seçilmedikçe her gruptan en çok altı
  sonuç verir; başlıkta sayı yok.
- **Satır:** "9 sonucun hepsini göster" (İngilizce arayüzde "Show all 9"). Oklar onda durur; Enter, tıklama ya da
  dokunuş grubun hepsini gösterir, palet açık kalır. Seçim satırın olduğu yerde kalır: orası artık grubun sıradaki
  sonucudur; liste kımıldamaz. Basılı tutulan Enter bir kez çalışır (tekrarları satırın yerine gelen sonucu
  çalıştırırdı). Grup, açıldığı sorgu ve süzgeç için bütün kalır; palet yeniden açılınca gruplar yine en iyi
  altılarıyla başlar.
- **Duran fare:** fare listenin üzerinde dururken oklar listeyi kaydırınca, altına gelen her satır seçimi oklardan
  geri alıyordu; oklar listenin kenarını geçemiyordu. Compose bir yerleşimden sonra imlecin son hareketini o an altında
  olana yeniden verir (duran imlecin altındaki üzerine gelme durumları için), palet de satırı üzerine gelinince
  seçiyordu. 2.4.1'in kodu da böyleydi; "hepsini göster" ile listeler uzadı. Satırı artık liste seçer, imlecin
  listedeki yerine göre ve yalnız imleç kendi hareket edince (tekerleği, basılması da sayılır); bir tuş satır seçince
  ya da sorgu değişince imleç yeniden hareket edene kadar seçmez. İmlecin seçtiği satır için liste kaymaz.
- **Sayı:** grubun eşleşmelerinin hepsi, gösterilen altı dahil. "En iyi eşleşme" olarak yukarı alınan sonuç sayılmaz.
- **Değişmeyenler:** süzgeç seçilince grup eskisi gibi bütün gelir, satır çıkmaz. Altı ya da daha az eşleşmesi olan
  grupta satır yoktur. `PaletteSearch.rows` aynı satırları verir (en iyi altı; başlık ve sonuçtan başka tür yok;
  `perGroup = 0` ile grupların başlığı da yok); palet artık kit içi bir listeden çizer. `PaletteRow`, `PaletteItem`, `CommandPaletteState` ve `CommandPalette`
  aynıdır.
- **Testler:** PR'ın ilk gönderisi 2.4.1'in davranışını kaydeder (yedinci eşleşme görünmez; oklar yedinci satırdan
  sonra başa döner). İkincisi düzeltip testi çevirir: satır dokuzu sayar, üzerinde Enter grubu açar ve palet açık
  kalır, seçim sekizinci albümdedir ve Enter onu çalıştırır; tıklama da açar; başka bir sorguda satır yeniden
  hesaplanır. Üçüncüsü PR'ın bağımsız incelemesinin bulduklarını düzeltir: dokunuşla açılan grupta liste, dokunuştan
  önce seçili olan sonuca geri kayıyordu (satır dokunuşla seçilmiyordu); `rows(perGroup = 0)` altı boş grup başlıkları
  veriyordu; basılı Enter; küçülen listede seçimin sonun ötesinde kalabilmesi. Testi iki grupla (ikisi de altıdan
  çok), Enter basılı tutularak yeniden yazıldı; "başka bir sorgu" kısmı hiç kırmızı olamıyordu (" 1" ile altıdan çok
  eşleşen grup kalmıyordu), artık "gec" ile açılan grup "e" yazılınca yine en iyi altısına döner. Dokunuş testi
  (Touch): liste parçaların satırına inmiş, satıra dokunulur, yedinci parça görünürdür. Dördüncüsü duran fareyi
  düzeltir; testi: imleç otuz satırın üçüncüsünde durur, yirmi kez aşağı ok, liste imlecin altında kayar ve seçim
  yirmi üçüncüdedir; imleç bir alt satıra gidince o seçilir, liste yerinde kalır. Bu iki yeni test düzeltmesiz kodla
  çalıştırılmadı; duran farenin nedeni Compose'un kaynağından okundu (`AndroidComposeView`,
  `resendMotionEventOnLayout`).
- **Doğrulama, neyi kapsıyor:** kitin testleri (Robolectric). Kitin örnek uygulaması hiçbir cihazda ya da emülatörde
  çalıştırılmadı. Nağmedar'ın kendi paletindeki benzer satır Android emülatöründe denendi (Nağmedar 1.0.7'nin `search`
  geçişi); bu, kitin paletinin bir cihazda denenmesi değildir.

## 2.4.1 (2026-11-13)

Düzeltme: dokunmatik ekranda parmak hâlâ hareket ederken bırakılan bildirim (`ToastHost`) ve alt sayfa
(`GlassBottomSheet`) artık bırakıldığı yerde kalmaz. Nağmedar'da (Galaxy S24, Android 17) yana kaydırılan bir bildirim
sayfanın önünde, yana kaymış ve yarı soluk hâlde, süresi dolana kadar duruyordu. Nağmedar'ın bildirimleri kitin
bildiriminden uyarlandığı için aynı kod kitte de vardı; alt sayfanın tutamağında ve başlığında da. Kod değişmeden
derlenir, API aynı; kitin bileşenlerini olduğu gibi kullanan uygulamanın yapacağı bir şey yok
([MIGRATION.md](MIGRATION.md#240dan-241e)). Görünür bir değişiklik olsa da sürüm üçüncü sayıyı artırır: yeni bir
davranış gelmiyor, 2.3.0'ın yazdığı davranış (kaydırılan bildirim ve fırlatılan sayfa yoluna devam eder, kısa
bırakılan yerine döner) parmak nasıl kalkarsa kalksın geçerli oluyor.

- **Neden:** parmağın her hareketi kendi coroutine'inde uygulanıyordu (`scope.launch { snapTo(…) }`). Parmak
  hareket hâlindeyken kalkınca Android son hareketi ve kalkışı pencereye birlikte verir, Compose'un `draggable`'ı
  da `onDragStopped`'u sırada bekleyenlerden önce başlatır. Son hareketin `snapTo`'su böylece bırakışın başlattığı
  `animateTo`'dan sonra çalışıp onu durduruyordu (`Animatable`'ın kilidi, "Mutation interrupted"). Parmak durup
  öyle kalkınca sırada bekleyen hareket kalmadığı için sorun görünmüyordu.
- **Bildirim:** telefonda yana fırlatılıp bırakılan bildirim gider (geniş pencerede zaten bir anda kapanıyordu); az
  kaydırılıp bırakılan her genişlikte yerine döner (telefonda hareketin yayıyla, genişte 2.2.0'ın yayıyla).
  Bırakış kararı artık son hareketi de görür; 2.4.0 bir hareket geriden bakıyordu.
- **Alt sayfa:** tutamağından ya da başlığından aşağı fırlatılıp bırakılan sayfa kapanır; az çekilip bırakılan
  yerine döner. Sayfa listesinden çekildiğinde bırakış, listenin kaydırmasının fırlatışı olarak sıraya girer; o
  yolun 2.4.0'da da doğru çalıştığı koddan okundu, test edilmedi. Bu sürümde o yolun da testi var.
- **Nasıl:** kaydırılan şeyin yeri artık kit içi bir `FingerOffset`: parmak onu anında taşır (`moveBy`, `moveTo`),
  bırakınca `settle()` dışarı ya da yerine götürür; parmak onu yeniden sürüklerse süren yol kesilir. Bir hareketin
  sırada bekleyen hiçbir parçası kalmadı. `SheetPull`'un coroutine kapsamına ihtiyacı da kalmadı.
- **Testler:** `LetGoInMotionTest` son hareketle kalkışı birlikte verir ve coroutine'leri cihazın ana iş
  parçacığındaki gibi sıraya koyar (`StandardTestDispatcher`). Kitin testlerinin kurulduğu Compose test kuralı her
  coroutine'i başlatıldığı anda çalıştırır (Compose 1.12 bu kuralı, sıraya koyan yenisi lehine kullanımdan
  kaldırıyor); hiçbir cihazda olmayan bu sıra yüzünden `ToastSwipeTest` ve `SheetGestureTest` sorunu hiç
  göstermedi. PR'ın ilk gönderisi aynı testlerle 2.4.0'ın davranışını kaydeder (bildirim ve sayfa bırakıldığı yerde
  kalır), sonraki ikisi düzeltip testleri çevirir. Her hareket, bırakıldığı anda parmağın o şeyi gerçekten taşımış
  olduğunu da doğrular.
- **Doğrulama, neyi kapsıyor:** kitin kendi kodunu kitin testleri sınar (yukarıda). Cihaz tarafı Nağmedar'da görüldü;
  onun bildirimi kitinkinden uyarlanmıştır, düzeltmesi aynı tasarımdır (kendi `SwipeAway` sınıfı). Android 17
  emülatöründe, telefon boyutunda ekranda, ham dokunma olaylarıyla (son hareket ve kalkış 0–0,1 ms arayla): Nağmedar
  1.0.5'te fırlatılıp bırakılan bildirim üç okumada da bırakıldığı yerde, az kaydırılan yana kaymış duruyor; 1.0.6'da
  fırlatılan gidiyor, az kaydırılan yerine dönüyor. Bu, kitin `ToastCard`'ının ya da alt sayfasının bir cihazda
  denenmesi değildir: kitin örnek uygulaması hiçbir cihazda ya da emülatörde çalıştırılmadı.

## 2.4.0 (2026-11-05)

Ayrıntı geçişi: açılan sayfa baştan saydam değil, listenin üstüne kayarak gelir; liste yalnızca kararır, iki sayfanın
yazıları hiçbir karede üst üste binmez. Bir telefonda (Galaxy, kit 2.3.0) çekilen videoda raflar listesinden bir rafın
kitaplarına geçerken ve dönerken bir an iki sayfa birden yarı saydam çiziliyordu: rafın ilk kitabının adı listenin
"MKE / Barış" seçicisinin üstünde, kapanırken kitap adları listenin raf kodlarının hemen yanında. Neden: 2.3.0'ın
derinlik geçişinde ayrıntı 0'dan solarak gelirken liste altında solarak gidiyordu (açılışta yaklaşık 0–160 ms,
kapanışta 0–120 ms ikisi birden yarı saydam), tablet ve DeX'in paylaşılan ekseninde de solmalar 50 ms üst üste
biniyordu. Kod değişmeden derlenir, API aynı; telefonda görünür bir davranış değişikliği olduğu için sürüm, kuralımız
gereği ikinci sayıyı artırır. Uygulamanın yapacağı bir şey yok ([MIGRATION.md](MIGRATION.md#230dan-240a)). Kare
şeritleri CI'ın `ci/screenshots-pr-7` dalında, `motion/` altında (`detail-open-plain`, `detail-close-plain`,
`detail-back-plain`, `detail-open-timed`, `detail-close-timed`, `detail-close` yeni).

Aynı aileden bir de sekmeler: Soulseek+'ta (Galaxy S24, üç düğmeli gezinme, Android 16) Ayarlar'dan, Sohbet'ten ve
Kullanıcılar'dan Geri tuşuyla Arama'ya dönerken sekme 300 ms küçülüp yana yaslanıyor ve giden sekmenin yazılarının
arasından Arama görünüyordu. İki neden: Geri *düğmesi* kaydırma hareketinin geçişini oynatıyordu, kaydırmada da giden
sekmenin kendi yüzeyi yoktu. İkisi de bu sürümde; kare şeritleri `ci/screenshots-pr-8` dalında (`tab-back`,
`tab-back-button`).

### Telefon

- **Ayrıntı Android'in kendi liste → ayrıntı geçişiyle açılır** (Ayarlar uygulaması, Android 14'ün etkinlik geçişi):
  ayrıntı ilk karesinden saydamsızdır (sayfa rengi kayan katmanın içinde, içeriğin altında), bitiş kenarından
  pencerenin tamamı kadar kayarak `DeskMotion.open` yayıyla listenin üstüne gelir. Liste solmaz: aynı yayla yüzde
  95'e küçülür ve temanın perdesinin yüzde 70'i kadar kararır (perde ayrıntının kenarıyla birlikte ilerler, listenin
  küçülmesiyle açılan kenarları da örter). Kapanış tersi:
  ayrıntı bitiş kenarına kayıp çıkar, perde açılır. Ayrıntı saydamsız olduğu için listenin yazısı onun içinden hiç
  görünmez; liste yalnızca ayrıntının henüz örtmediği yerde görünür.
- **Öngörülü geri:** davranış aynı (ayrıntı parmakla yüzde 90'a küçülür, köşeleri yuvarlanır, kaydırmanın geldiği
  kenardan uzağa yaslanır; liste arkasında yüzde 94'ten yükselir). Liste solarak gelmez: parmak hareket ederken
  perdenin altında koyu kalır (küçülen ayrıntı onun üstünde belirgin durur), bırakınca perde açılır. Ayrıntı
  bırakınca solmadan, pencereden tamamen çıkana kadar kayar (önce beşte iki kayıp soluyordu); kalan yol yine 220 ms,
  eğrisi hızlanan yerine standart (uzun bir kayma hızlanan eğride sonunda sıçrıyordu).
  Vazgeçince ve yolun ortasında fikir değiştirince her şey eskisi gibi geldiği yoldan döner, iki sayfa da saydamsız.
- **Kartla (`detailSharedCard`):** kart başlığa büyürken sayfaların gerisi sırayla solar: liste ilk 90 ms'de tamamen
  gider, ayrıntı ancak ondan sonra 210 ms'de belirir; dönüşte tersi (önceden 60–160 ms arası ikisi birden yarı
  saydamdı). Kartın kendisi uçarken saydam olmaz: iki tarafın kopyası da tam çizilir, başlık kartın üstünde; kartın
  yüzeyi altta hep saydamsız, içerikler aynı sırayla solar (kartınki önce gider, başlığınki sonra gelir). Önceden iki
  kopya birlikte solduğu için kart 90 ms civarında yarı saydam oluyordu.
- **Bir ayrıntıdan ötekine** (anahtar değişince) geçiş de sıralı solar: eski 100 ms'de gider, yeni sonra gelir.
- **Üç düğmeli Geri sekmeyi fade-through'la döndürür:** Android 16'dan beri üç düğmeli Geri'ye (ve donanım Geri
  tuşuna) basmak da öngörülü bir Geri başlatır, kenarı yok (`EDGE_NONE`), bırakınca tamamlanır. Navigation bunu
  kaydırma sayıp `NavHost`'un öngörülü geçişlerini seçiyor ve düğme bırakılınca sonuna kadar oynatıyordu: 2.3.0'ın
  sekme hareketi bir dokunuşla 300 ms oynuyordu, sekme yüzde 90'a küçülüp sağa yaslanıyor, son onda birde soluyordu.
  Düğme kaydırma değildir: `EDGE_NONE` gelince kabuk Navigation'a hareketsiz bir Geri'nin fade-through'unu verir
  (eski sekme 90 ms'de solar, yenisi 210 ms'de belirir), Android 15'teki gibi.
- **Harekette giden sekmenin yüzeyi:** kabuğun `NavHost`'undaki ekranlar kabuğun arka planında duruyordu, kendi
  yüzeyleri yoktu; yazıdan ibaret bir ekran saydamdı. Öngörülü geri hareketinde küçülen sekmenin yazılarının
  arasından arkadaki başlangıç sekmesi görünüyordu: ayrıntıdaki çift görüntünün sekmedeki eşi. Telefonda her sekme
  sayfası ("⋯" ekranı da) artık kendi sayfa renkli yüzeyinde: hareket boyunca bütün, başlangıç sekmesi yalnızca
  çevresinde görünür; köşeleri ayrıntınınki gibi parmakla 28 dp'ye doğru yuvarlanır. Yuvarlanma çıkış geçişinin
  o içerikte oynattığı ölçekten okunur (yüzde 90'a doğru; Compose'un yerleşik ölçek animasyonu adıyla bulunur, ad
  değişirse köşeler düz kalır, başka bir şey kaybolmaz); hiçbir şeyi küçültmeyen fade-through'da köşeler düz kalır.
  Yüzey yalnızca bir geçiş sürerken var ve kabuğun rengindedir: durağanken ve sekme geçişlerinde görünüm aynı,
  durağan sayfaya katman eklenmez; 50 ekran görüntüsü aynı.

### Tablet ve DeX

- **Paylaşılan eksen sıralı solar** (Material'ın paylaşılan ekseni gibi): giden sayfa ilk 100 ms'de tamamen solar,
  gelen ancak ondan sonra 200 ms'de belirir. Önceden açılışta 50–100 ms, kapanışta 50–150 ms ikisi birden yarı
  saydamdı. Kaymalar (yüzde 10, 300 ve 350 ms) ve toplam süreler aynı; geri
  hareketi 2.2.0'daki gibi (gölge üstünde küçülme). Ekran görüntüleri aynı.
- **Üç düğmeli Geri sekmeler arasında:** Geri düğmesi tablette de öngörülü bir Geri başlattığı için Navigation'ın
  kendi öngörülü geçişi oynuyordu, sekme yüzde 70'e küçülüyordu; artık hareketsiz Geri'nin geçişi (ray düzeninde
  fade-through, kenar çubuklu düzende 150 ms'lik geçiş). Hareketle gezinmede sekmeler arası öngörülü geri
  Navigation'ınki, 2.2.0'daki gibi; sekmelerin kendi yüzeyi yalnızca telefonda. Ekran görüntüleri aynı.

### Testler

- `DetailNoDoubleExposureTest`: liste kırmızı, ayrıntı mavi; açılışın, kapanışın, öngörülü geri hareketinin (parmak
  yolda, bırakınca, vazgeçince) ve yolun ortasında dönen açılışın her karesinde, telefonda ayrıntının da listenin de
  saydamsız olduğu ve hiçbir pikselde iki sayfanın karışmadığı denetlenir; kartla, tablette ve DeX'te hiçbir karede
  karışma olmadığı. Antialiasing'in küçülen sayfanın kenarlarında karıştırdığı birkaç piksel için pencerenin yüzde
  biri pay var; 2.3.0'ın çift görüntüsü yüz binlerce pikseldi.
- Kare şeritleri: yazılı bir liste ve ayrıntıyla (raflar → bir rafın kitapları; uydurma adlar) açılış, kapanış ve
  geri hareketi; aynısı tablet katmanında; kartlı kapanış.
- `TabPredictiveBackTest`: Geri düğmesi (başlangıç `EDGE_NONE`; Navigation başlangıç sekmesini birkaç kare içinde
  hedef olarak kompoze eder, bırakma 48 ms sonra): sekme yolda tam boyutunda ve yerinde kalır, hareketin çıkışı onu
  üç karede yüzde 98,4'e, dokuz karede yüzde 95'e küçültüp sağa yaslardı. Hareket:
  başlangıç sekmesi kırmızı, giden sekme arka planı olmayan bir mavi kare; yolun yarısında pencerenin ortasında
  kırmızımsı piksel yok (yarı saydam bir ekranın içinden görünen kırmızı da sayılır), sekme gidince hepsi kırmızı.
- Kare şeritleri: `tab-back` (hareket: giden sekme kendi yüzeyinde küçülür, başlangıç sekmesi çevresinde yükselir)
  ve `tab-back-button` (düğme: basış 48 ms önce, sonra fade-through).

## 2.3.0 (2026-10-31)

Galaxy S24'teki hareketlerin kare kare incelenmesi: takılan ve sıçrayan ne varsa düzeltildi, geçişler telefonun
kendi karakterinde yeniden çizildi. 2.2.0 için yazılmış kod değişmeden derlenir. Yeni API
(`Modifier.detailSharedCard`, `Modifier.playerSharedElement`, `rememberListAppear` ve `Modifier.listAppear`,
`DeskMotion.open`, `DeskMotion.gesture(visibilityThreshold)`, `DeskMotion.spatial(tier, timed, visibilityThreshold)`)
ve telefonda görünür davranış değişiklikleri yüzünden sürüm, kuralımız gereği ikinci sayıyı artırır. Tablet ve
DeX'in süreleri ve görünümü değişmedi (ekran görüntüleri aynı). Uygulamanın fark edebileceği değişiklikler
[MIGRATION.md](MIGRATION.md#220dan-230a) içinde. Her geçişin kare şeridi CI'ın `ci/screenshots-pr-6` dalında,
`motion/` altında.

### Telefon: düzeltmeler

- **Yayların kuyruğu:** Compose bir yayı hedefe kendi biriminde 0,01 yaklaşınca durdurur; 300 ms'de yerleşmiş
  görünen yay son pikselin yüzde birini traşlamak için 300 ms daha çalışıyordu: kapanan ayrıntı (ve
  `CompactTopBar`'ı) yarım saniye fazla duruyor, gizlenen alt çubuk sistem bandını yeniden çiziyor ve dolgu almayan
  ekranları her karede yeniden ölçüyor, oynatıcının kapanışı sürüyor (2.1.0'daki SHEET_OUT bunun belirtisini
  tedavi ediyordu), alt sayfa yüksekliğinin yüzde birini sonda sıçratıyordu. `DeskMotion.gesture(visibilityThreshold)`
  ve `spatial(tier, timed, visibilityThreshold)`: piksel değerlerinde yarım piksel (`DeskMotion.PIXEL`), konum ve
  dikdörtgenlerde `VisibilityThreshold`, kesirlerde binde bir. Yayların sabitleri bu düzeltmede değişmedi.
- **Oynatıcı şeridin olduğu yerden açılır:** çubuklar kaydırmayla gizliyken mini oynatıcıya dokununca çubuklar bir
  anda geri geliyor (şerit 64 dp zıplıyordu), çapa katman kaymadan önce ölçüldüğü için sayfa eski yerden açılıyor,
  kapanınca oraya iniyor ve şerit çubuğun üstünde beliriyordu: bir dokunuşa iki sıçrama. Çubuklar oldukları yerde
  kalır (açılan sayfa onları örter, şerit kapanınca aynı yerdedir), çapa her basışta ölçülür (`showBars()` gitti).
- **Fırlatılan alt sayfa:** yüksekliğin üçte birini geçince ya da fırlatınca `onDismiss` hemen çağrılıyor ve kapanış
  parmağın bıraktığı yerden duruşla başlıyordu: fırlatmanın hemen ardında görünür bir duraklama. Sayfa parmağın
  hızıyla yoluna devam eder, çıktığı anda (yarım piksel kala) kapatılmak ister. Yarı yolda perdeden ya da Geri'yle
  kapatılan sayfa eskisi gibi olduğu yerden gider; yalnızca parmağın çıkardığı sayfa için animasyon kalmaz.
- **"Animasyonları kaldır":** azaltılmış hareket katmanı kabuğu, alt sayfayı, bildirimleri, paleti ve ilerleme
  çubuğunu kapsıyordu, gerisini değil: menüler (KDoc'u söz vermişti), ipuçları, üst çubuğun camı, çizgisi ve
  başlığı, gezinme hapı, basma küçülmesi, denetimlerin renk geçişleri, chip'in onay işareti, sekmenin çizgisi,
  kapağın halkası, kaydırıcının tutamağı, anahtarın basması, oynatıcının yayları ve iki öngörülü geri iptali hareket
  ediyordu. Hepsi `DeskMotion.fade` ya da `spatial`'dan katmanla geçer; oynatıcının durumu katmanı bileşeninden öğrenir.
- **Üst çubuk:** camdan düz renge dönüş de bir solmadır. Yalnızca cama gidiş soluyordu; dönüşte bulanıklık düz
  rengin geldiği karede kapanıyor, görünmez bir solmanın altında kalıyordu. Cam, sayfa rengi üstüne solup kapanıncaya
  kadar durur.
- **Fade-through:** sekme geçişinde yüzde 96'dan büyüme, yeni ekranın solmasıyla birlikte başlar. Büyüme 90 ms erken
  başladığı için ekran görünür olduğunda yalnızca son yüzde iki kalıyordu; solmalar ve sıra (eski gider, yeni gelir)
  aynı.
- **Dolgu almayan ekranlar:** örnek uygulamanın albüm ızgarası, albüm sayfası, Dosyalar, Çalma listeleri, Çöp ve
  Ayarlar `rememberShellContentPadding` istemediği için kabuk onları çubukların kaymasıyla yeniden ölçüyordu:
  çubuklar kayarken (ve yayın kuyruğunda) bütün ekran, kapak ızgarası dahil, her karede. Uçlarını kendileri doldurur
  (`rememberShellContentPadding() + PaddingValues(…)`, `shellContentPadding()`); kitin "Diğer" ekranı da. Kabuğun
  yedek ölçümü 0.2.0 ekranları için kalır.

### Telefon: hareketler

- **Yaylar:** `DeskMotion.gesture()` kritik sönümlü 400'den 0,92 sönümlü 600'e: görünür sekme yok, yine her yerinden
  yakalanır, fırlatma hızını alır, 300 ms'de yerleşir. Yeni `DeskMotion.open()`: Android'in standart uzamsal yayı
  (0,9 sönüm, 700) dokunuşun açtığı ve kapattığı şeyler için — ayrıntı ve içine uçanlar. `gesture()` ve
  `spatial(tier, timed)` eski imzalarıyla da durur (`@JvmOverloads`): 2.2.0'a karşı derlenmiş kod da bağlanır.
- **Ayrıntı derinlikte açılır:** ayrıntı bitiş kenarından yüzde 12 içeri kayıp hemen solarak gelir (dokunuştan
  sonraki 50 ms'lik boşluk gitti), liste altında yüzde 95'e küçülerek solar; dönüş tersi. Yeni
  `Modifier.detailSharedCard(key, shape)` listedeki kartı ve ayrıntının başlığını işaretler: kart başlığa büyür,
  köşeleri düzleşir, yüzeyi sayfaya döner, başlığın içeriği kartınkinin üstünde belirir; geri dönüşte başlık kartın
  yerine küçülür. Kapak ve başlık kartın üstünde kendi başlarına uçmayı sürdürür; kartla ayrıntı kaymaz, kartın
  ardından solar. Tablet genişliğinden yukarı 0.2.0'ın paylaşılan ekseni kalır.
- **Öngörülü geri:** ayrıntının geçişi `SeekableTransitionState` üzerinde. Parmak kapanışı yüzde 85'e kadar oynatır:
  liste küçülen ayrıntının gerçekten arkasındadır ve yüzde 94'ten parmakla yükselir; ayrıntı bütün, köşeleri yuvarlak,
  kaydırmanın geldiği kenara yaslı kalır. Bırakınca kalan yol parmağın bıraktığı yerden 220 ms'de biter: ayrıntı o
  kenara kayıp solar, liste yükselmesini tamamlar. Vazgeçince her şey geldiği yoldan yaylanarak döner. Listenin
  yerine duran gölge, yeni bir hareketin içine düşebilen gecikmeli sıfırlama ve küçülmüş sayfadan tam boyuta sıçrayan
  kapak gittiler; `onBack`'ten sonra ayrıntısını tutan uygulamada hareket geri alınır. Geri düğmesi yalnızca çıkış
  yolunu oynatır. Yolun ortasında fikir değişikliği (dokunuştan hemen sonra Geri, Geri'den hemen sonra yine dokunuş,
  vazgeçilen hareket) geçişi yeniden hedeflemez, kesri geri oynatır: kart yolundan geri uçar (yeniden hedeflenen
  seekable geçiş paylaşılan öğelerin eşleşmesini bir kare bırakıyor, başlık tam genişliğe sıçrayıp oradan gidiyordu).
  Geri dönüş bitince yoldaki içerik paylaşılan öğelerini bırakır, sonra geçiş durur: ikisi aynı karede olsaydı listenin
  kapağı artık çizmeyen bir kaplamayı bekleyip karanlık kalıyordu (kare şeridi gösterdi). Erken bırakılan parmağın
  kalan payı önce hızla oynatılır, çıkış yolu ondan sonra başlar. Aynı anahtarla yeniden verilen ayrıntı (bir sayaç, bir
  ilerleme) geçişsiz en son nesnesini gösterir. Tablet genişliğinden yukarı 2.2.0'ın hareketi olduğu gibi kalır: ayrıntı
  gölge üstünde küçülür, bırakınca paylaşılan eksenle kapanır, vazgeçince 2.2.0'ın uzamsal yayıyla döner.
- **Oynatıcı tek parça:** sayfa şeridin kendisi olarak başlar — aynı cam, üstünde şeridin denetimleri. Yükselirken
  camın üstüne sayfa rengi gelir, köşeler yolda yuvarlanıp iki uçta düzleşir, perde onunla koyulaşır, alt çubuk
  altına itilir; şeridin denetimleri ilk çeyrekte solar, tam oynatıcınınkiler son bölümde gelir. Yeni
  `Modifier.playerSharedElement(key, shape)`: `mini` ve `full` içeriğinde aynı anahtarla işaretlenen öğeler (kapak,
  başlık) şeritteki yerinden tam oynatıcıdaki yerine uçar ve geri (ilk işaretlenen üstte: başlık büyüyen kapağın
  altından geçer). Her kopya çizildiği gibi kaydedilir: donanım tuvalinde kendi grafik katmanına (Coil ve Glide'ın
  donanım bitmap'leri de olduğu gibi çizilir), yazılım tuvalinde (ekran görüntüsü testi, Robolectric) `Picture`
  olarak; uçuşta son çizimi görünür. Şeridin denetimleri solunca yerleştirilmez (çizilmez, dokunulmaz, TalkBack
  okumaz); açık başlayan oynatıcıda (geri yüklenen durum) bir kez çizilinceye kadar yerleştirilir ki ilk inişte de
  uçsun, yerleştirme kararı türetilmiş durumdan okunur (yolun her karesinde yeniden yerleştirilmez). Sayfa yukarıda
  dururken camı kapalıdır (sayfa rengi altında bulanıklık çizilmez). Dokunuşun yayı 0,9 sönüm, 500 (yarım piksel
  eşikle; azaltılmış harekette atlar). Geri hareketinin küçülmesi sayfayla birlikte iner ve sayfa şeride inince ya da
  parmak yakalayınca bırakır; iniş başlamazsa (sayfa zaten parmakta) iki kare sonra kendisi bırakır. Önce küçülme
  önce açılıp sonra iniyordu.
- **Alt sayfa:** gidiş de dönüş de tek sert yay (0,92 sönüm, 800); sayfa katmanda kaydırılmak yerine yerleştirilir
  (`offset {}`), camı o an arkasında ne varsa onu bulanıklaştırır (Haze konumu yerleşimde okur, katmanın
  kaydırmasını görmez).
- **Menü açıldığı noktadan büyür:** parmağın üstüne çevrilmek zorunda kaldıysa alt köşesinden, pencere kenarından
  içeri itildiyse parmağın altından; konum sağlayıcısı bunu açılır pencere yerleştiğinde hesaplar.
- **Bildirim:** yana kaydırılan bildirim parmağın hızıyla yoluna devam eder ve çıktığı anda kapatılır (parmağın
  bıraktığı yerde durup soluyordu); geri yaylanma da parmağın hızını alır (genişte 2.2.0'ın yayı kalır).
- **Satırlar sırayla gelir:** yeni `rememberListAppear(keys)` ve `Modifier.listAppear(appear, index)`. Liste
  göründüğünde yerinde olan ilk sekiz satır 15 ms arayla, her biri 180 ms'de solup 12 dp yükselerek gelir; sonradan
  gelen satırlar (kaydırılan, eklenen) olduğu gibi yerindedir. Anahtarlar listenin ne zaman yeni sayılacağını söyler:
  iskeletten sonra gelen satırlar, yeni ayrıntının satırları sırayla gelir, ayrıntıdan dönen liste yeniden gelmez.
- **Basma:** telefonda basma istenenin dörtte üçü derinliğinde (varsayılan 0,96 → 0,97: parmak denetimi örter,
  küçülme görülmekten çok hissedilir), bırakınca 0,75 sönümlü 1200 yayla biraz üstüne sekip yerleşir.
- **Anahtar:** tutamağın yayı yolun yüzde birinde (yarım piksel) durur; binde birde görünmeden çalışıyordu.
- **Tam oynatıcıda iç içe kaydırma:** 2.0'ın KDoc'u söz veriyordu, hiçbir şey yapmıyordu: tam oynatıcıdaki bir liste
  (sözler, sıra) her sürüklemeyi alıyordu. Artık içerik önce kayar; en üstteyken aşağı çekmek oynatıcıyı parmakla
  indirir, geri itmek içerik yeniden kaymadan önce oynatıcıyı kaldırır, bırakınca sayfanın kendi fırlatma kuralı
  karar verir (yarı yol, fırlatma). En üste çarpan bir içerik fırlatması oynatıcıyı yerinde bırakır.
- **Chip:** ikonlu bir chip seçilince ikon bir anda gidip onay işareti büyüyordu: etiket ikonun genişliği kadar sola
  sıçrayıp sağa kayıyordu. Telefonda onay işareti ikonun yerinde belirir (solup büyüyerek), chip'in genişliği
  değişmez.
- **Sekmeler arası öngörülü geri:** hareketle gezinmede Navigation, `NavHost`'un öngörülü geri geçişlerini parmakla
  oynatır ve kabuk ona hiç vermemişti: sekme Navigation'ın varsayılanıyla (yüzde 70'e küçülme) dönüyordu, her
  katmanda ve azaltılmış harekette de. Telefonda ayrıntınınki gibi: bırakılan sekme parmakla yüzde 90'a doğru küçülüp
  kaydırmanın geldiği kenardan uzağa yaslanır, yolun son onda birine kadar bütün kalır; başlangıç sekmesi arkasında
  yüzde 94'ten yükselir. Üç düğmeli Geri fade-through'la kalır; azaltılmış harekette ve `ShellTransitions.None` ile
  hiçbir şey oynamaz.
- **Vurgu rengi değişimi:** renkler statik bir yerel değerle veriliyordu; statik yerel değişince altındaki her şey
  atlanmadan yeniden kompoze olur: 150 ms'lik geçişin her karesinde (dokuz kez) bütün uygulama. Renkler artık dinamik
  bir yerel değer (`LocalDeskColors`'un türü aynı); geçiş boyunca yalnızca `DeskTheme.colors` okuyanlar yeniden
  kompoze olur, geri kalanı değişimin başladığı karede bir kez. Material'ın renk şeması (o da statik) yeni paleti bir
  anda alır; kitin bileşenleri yine solarak geçer.
- **Lookahead:** paylaşılan öğeli bir `DetailTransition`'da durağan kaydırılan liste her karede iki kez ölçülüyordu
  (ölçüldü: 57 lookahead, 57 asıl ölçüm), hiçbir geçiş yokken. Telefonda içerik durağanken düz bir `Box` içindedir,
  geçiş ya da Geri hareketi başlayınca durumuyla birlikte (`movableContentOf`: kaydırma yeri, metin, efektler)
  `SharedTransitionLayout`'a taşınır, bitince geri çıkar; kartlar durağanken de kayıtlı kalır.
- **Tek alt cam:** telefonda oynatıcı şeridi, alt çubuk ve sistem düğmelerinin arkasındaki bant üst üste üç cam
  yüzeydi, altlarındaki kaydırmanın her karesinde her biri ayrı bulanıklaştırıyordu; çubuklar katman kaydırmasıyla
  kaydığından camları kayarken ekranın yanlış yerini bulanıklaştırıyordu (Haze konumu yerleşimde okur). Kabuk artık
  şeridin (şerit yoksa çubuğun) üstünden pencerenin altına tek cam çizer, çubuklarla birlikte yerleşimle kaydırır;
  bandın içine kayan çubuk orada kesilir. Üç bulanıklık yerine bir, doğru yerden. Ekran görüntülerinin hepsi aynı.

### Tablet ve DeX

Süreler ve görünüm değişmedi; ekran görüntüleri aynı. Ayrıntı 0.2.0'ın paylaşılan ekseninde açılır ve geri hareketi
2.2.0'daki gibidir (gölge üstünde küçülme, vazgeçince aynı yay), fırlatılan alt sayfa eskisi gibi hemen kapatılmak
ister ve olduğu yerden gider, menü sol üst köşesinden büyür, bildirim bir anda gider ve erken bırakılınca 2.2.0'ın
yayıyla döner, basma eskisi gibi. Ortak olanlar: yayların eşik düzeltmesi (süreli eğrilerde etkisi yok), alt sayfanın
katman yerine yerleşimle kayması (sayfa kayarken cam arkasındakini doğru yerden bulanıklaştırır; süre aynı),
"Animasyonları kaldır" açıkken her şeyin bir anda olması, anahtarın tutamağının yarım pikselde durması ve vurgu
değişiminde yalnızca rengi okuyanın yeniden kompoze olması (Material bileşenlerinin rengi bir anda değişir). Sekmeler
arası öngörülü geri azaltılmış harekette ve `ShellTransitions.None` ile artık oynamaz. Bir de
örnek uygulamanın Ayarlar listesi ile kitin "Diğer" ekranı: yüzen oynatıcı varken liste onun altına kadar uzanır ve
dolgusunu kendi verir (eskiden ekran oynatıcının üstünde bitiyordu); kısa listelerde görünüm aynı.

### Örnek uygulama

- Telefonda her liste cam çubukların altına uzanır ve uçlarını kendi doldurur.
- Albümler kartla açılır (`detailSharedCard`), mini ve tam oynatıcının kapağı ve başlığı uçar
  (`playerSharedElement`), Parçalar, Albümler (yer tutucular gidince), albüm sayfası, Çalma listeleri, Çöp ve
  Dosyalar'ın satırları sırayla gelir.

### Testler

- Yeni testler: yayların eşikli süresi ve katman seçimi, azaltılmış harekette her şeyin bir anda olması, üst çubuğun
  iki yöne de solması, çubuklar gizliyken oynatıcının şeritten açılıp oraya inmesi, fırlatılan sayfanın kendi hızıyla
  çıkması (kare kare), kartın başlığa büyüyüp geri gelmesi ve yolun ortasında dönmesi (kare kare genişlik), geri
  hareketinde listenin gerçekten arkada olması, kapağın şeritten tam oynatıcıya uçuşu (piksel piksel) ve şeridin
  denetimlerinin solunca yerleştirilmemesi, bildirimin kaydırılınca yoluna devam etmesi ve genişte bir anda gitmesi,
  menünün büyüme köşesi, satırların sırayla gelişi ve telefonun basması. İncelemeden sonra: vazgeçilen geri
  hareketinden sonraki ikinci hareketin kartı yine uçurması, aynı anahtarla yeniden verilen ayrıntının en son
  nesnesini göstermesi, tablet genişliğinde geri hareketinin 2.2.0'daki gibi kalması (liste arkada değil) ve açık
  başlayan oynatıcının ilk inişinde kapağın uçması. Sonra: tam oynatıcıda içeriğin önce kayıp sonra oynatıcıyı
  çekmesi, telefonda ikonlu chip'in genişliğini kare kare koruması, sekmeler arası öngörülü geri, vurgu değişiminde
  rengi okumayanın yeniden kompoze olmaması (dokuz kezden bire), durağan kaydırmada lookahead ölçümü olmaması, tek alt
  camın şeritten pencerenin altına uzanıp çubuklarla kayması.
- `MotionStripsTest`: her geçişin kare şeridi (sekme geçişi, ayrıntının açılışı, yolda dönüşü, geri hareketi ve
  vazgeçilen hareket, oynatıcının açılışı, kapanışı ve geri hareketi, çubuklar gizliyken açılış ve kapanış, alt
  sayfanın açılışı ve kapanışı, satırların gelişi) `kit/build/outputs/screenshots/motion` altına yazılır, CI çekme
  isteğinin `ci/screenshots-pr-<numara>` dalına koyar.
- Robolectric'te art arda kareler arasında boşta kalınmayınca recomposer uyur; kare kare ilerleyen testler her
  kareden sonra `waitForIdle` çağırır. Seekable geçişte paylaşılan öğeler bir Geri olayının kompozisyonunda eşleşir ve
  sınırlarını bir sonraki olayın seek'i oynatır; hareket testleri parmak gibi birden çok olay gönderir.

### Bu sürümde olmayan

- Hareketle gezinmede sekmeler arası öngörülü geri tablet ve DeX'te Navigation'ın kendi geçişiyle kalır (2.2.0'daki
  gibi); kitin kendi geçişi telefonun.
- Ayrıntı geçişinin lookahead tasarrufu telefonun: tablet ve DeX'te paylaşılan öğe düzeni 2.2.0'daki gibi hep
  açık (geçiş orada aynı karede başladığı için düzeni o anda kurmak kapağın uçuşunu kaçırıyordu).
- Üst çubuğun camı ayrı kalır (alttakilere bitişik değil).

## 2.2.0 (2026-10-30)

Walkman Sync 2.1.0'a geçerken bulunanlar. 2.1.0 için yazılmış kod değişmeden derlenir; yeni:
`showShortcutHints()`, `DeskCard(verticalArrangement)`, `DenseTable`/`DeskGrid(clearSelectionOnBack)`. Uygulamanın
fark edebileceği değişiklikler [MIGRATION.md](MIGRATION.md#210dan-220a) içinde.

- **Geri seçimi temizler:** 2.1.0'dan beri uzun basma seçiyor ve seçim varken parmak dokunuşu seçimi değiştiriyor;
  "Seç/Bitti" düğmesi olmayan ekranda telefonda seçimden çıkmanın tek yolu öğeleri tek tek bırakmaktı (Esc için
  klavye gerekir). `DenseTable` ve `DeskGrid`'de süren bir seçim varken (dokunmatik yerleşimde ya da seçim modunda
  her seçim, farede birden çok öğe; ızgarada işaret dairelerinin göründüğü durum) sistemin Geri hareketi önce
  seçimi temizler, Android'in eylem kipindeki gibi. Farenin tıklayıp seçtiği tek öğede ve seçim yokken Geri'ye
  dokunmaz; uygulama temizlemeyi almazsa sonraki Geri ona geçer. `clearSelectionOnBack = false` Geri'yi
  uygulamaya bırakır; geri çağrısı sağlayıcısı olmayan yerde (bir hizmetin penceresi, ekran görüntüsü testi)
  hiçbir şey eklenmez.
- **İlerleme çubuğunun adı:** açıklama verilmeyen her `DeskProgressBar` TalkBack'te "Yükleniyor" okunuyordu;
  kopyalamanın ne kadarının bittiğini gösteren çubuk da (liste satırınınki dahil). Bu adı artık yalnızca miktarı
  belirsiz çubuk alır; miktarı belli çubuk yüzdesini söyler, uygulamanın verdiği ad ikisinde de kullanılır.
- **Kısayol ipuçları tek kural:** kısayolların ne zaman yazılacağı (fare ya da donanım klavyesi, parmak tek
  başına değil) artık genel API: `showShortcutHints()`. Bildirimler, komut paleti ve menü çubuğunun arama alanı
  da ona uyar: klavye kapağı bağlı dokunmatik yerleşimde bildirimde "Ctrl+Z", palette Enter, Ctrl+Enter ve Esc
  görünür; elde tutulan telefonda paletin öğelerindeki, klavyesiz tablette arama alanındaki kısayollar gizlenir.
- **Komut paleti açıkken kısayollar:** etkinlik tuşları Compose'dan önce gördüğü için README'nin önerdiği
  yönlendirme palet açıkken de çalışıyordu: örnek uygulamada Ctrl+K yazılanı siliyor, F5 ve Ctrl+B paletin
  arkasında çalışıyordu; Ctrl+Enter'a ya da düz tuşa bağlı bir kısayol paletin kendi tuşunu alırdı. Örnek uygulama
  palet açıkken (`palette.isOpen`) yönlendirmeyi atlar; bunun için paletin durumu etkinlikte durur
  (`CommandPaletteState()`). README, `matchNative` ve `CommandPalette` belgeleri bunu gösterir.
- **Kartta öğe aralığı:** `DeskCard(verticalArrangement = Arrangement.spacedBy(…))` kartın sütununu her Column
  gibi düzenler: öğeler aralanır, başlığın altına da aynı aralık eklenir, hizalamalar kartın kenarına göre kalır.
  Varsayılan görünüm değişmez. Örnek uygulamanın bölümleri bunu kullanır.
- **MIGRATION:** başlık ve 1. adım güncel sürümü söyler; 0.1.3'ten gelenler için telefon ile tablet ve DeX'te
  "görünümde fark edecekleriniz" bölümü; saf Kotlin/JVM modülü ve foojay-resolver-convention notu (Gradle 9 ile
  en az 1.0.0); bileşen tablosunda `DeskCard`'ın boşlukları, `KeyValueRow`'un ayrıntıları, `MenuButton`'ın oku,
  şerit simgeleri ve `CheckboxRow`; Soulseek+'ın bildirim örneğindeki eski "sırayla gösterir" notu düzeltildi.
- **Testler:** ilerleme çubuğunun TalkBack adı, kısayol ipucu kuralı (fare, parmak, klavye kapağı), kartın öğe
  aralığı, başlık boşluğu ve kenara hizalama, Geri'nin ızgarada ve tabloda seçimi temizlemesi, farenin tek
  seçiminde ve uygulamanın almadığı temizlemede Geri'yi bırakması ve kapatılabilmesi.

## 2.1.0 (2026-10-30)

Galaxy S24 ve Galaxy Tab S11 Ultra'daki kullanım testlerinde bulunanların düzeltmeleri. 2.0.0 için yazılmış kod
değişmeden derlenir. Düzeltmelere eşlik eden üç ekleme (`Modifier.keepsKeyboardOpen()`, `hasHardwareKeyboard()`,
`detailSharedElement(…, shape)`) ve davranış değişiklikleri yüzünden sürüm, kuralımız gereği ikinci sayıyı artırır.
Uygulamanın fark edebileceği değişiklikler [MIGRATION.md](MIGRATION.md#200dan-210a) içinde.

### Telefon

- **Üst çubuk:** içerik altından kayarken camlaşırken birkaç kare griye dönmüyor (arka plan `Color.Transparent`'a,
  yani saydam siyaha doğru soluyordu; artık yalnızca kendi renginin saydamlığı değişir). Ekran değişince yeni başlık
  eskisi gittikten sonra belirir; iki başlık üst üste görünmez.
- **İlk açılış:** alt çubuğun altında boş bir şerit kalmıyor. Root, kabuğun gezinme çubuğunu kendisi çizip
  çizmediğini ölçüm sırasında okuyordu; kabuk tam o sırada kaydolduğu için boşluk bir sonraki yeniden yerleşime
  (tema değişimi gibi) kadar yanlış kalıyordu.
- **Cam alt sayfa ("⋯"):** listenin her yerinden aşağı çekince kapanır (liste en üstteyken), yalnızca tutamaktan ve
  başlıktan değil: yavaşça üçte birini geçince ya da hızlı bir fırlatışla. Çekilmiş sayfayı yukarı itmek önce sayfayı
  kaldırır, sonra liste kayar. Fare tekerleği sayfayı oynatmaz.
- **Açık/kapalı eylemler:** `MenuEntry.Item(checked = …)` alt sayfada satırın sonunda bir anahtardır (kapalıyken boş
  duran onay sütunu yok). Öğe kendi simgesini korur, dokunmak sayfayı kapatmadan değiştirir, TalkBack anahtar olarak
  okur. Menülerde onay işareti kalır ve durumunu TalkBack'e söyler.
- **Kapak geçişi:** albümden geri dönerken kapak uçarken de köşeleri yuvarlak (önce köşeli uçup yerine inince
  yuvarlanıyordu). `Modifier.detailSharedElement(key, shape)` öğeyi yerinde de uçarken de o biçimle kırpar;
  `CoverCell` içinde hücrenin biçimi varsayılandır.
- **Izgara ve tablo:** parmak ya da kalem dokunuşu kapağı ya da satırı açar, seçmez (açıp geri gelince "18'den 1
  seçili" yazıyor, her kapakta işaret dairesi duruyordu). Seçim, seçim modunda (`toggleOnClick`) yapılır; bir öğe
  seçiliyken dokunuş ekler ya da çıkarır. Uzun basma menüyü açar ve öğeyi seçer, menü üzerinde çalıştığı öğeyi
  seçimde bulur. Yerleşim değil işaretçi belirler: fare tıklaması dokunmatik yerleşimde de seçer, parmak fare
  yerleşiminde de açar. İşaret daireleri farede birden çok kapak seçiliyken görünür.
- **Ekran klavyesi:** çubuklar kaydırmayla gizlendikten sonra klavye açılınca mini oynatıcı klavyenin arkasında
  kalmıyor, klavyenin hemen üstünde durur. Klavye açıkken parmakla içeriği kaydırmak ya da metin alanlarının dışına
  dokunmak alanın odağını alır ve klavye kapanır. Alanın içinden başlayan hareket (alana ya da düğmelerine dokunmak,
  uzun metni kaydırmak), alanın görünür kalmak için yaptığı kaydırma ve fare tekerleği klavyeyi açık bırakır;
  donanım klavyesi bağlıyken odak yerinde kalır, yazmak sürer. Açık bırakması gereken denetim (mesaj alanının
  yanındaki Gönder düğmesi) ya da uygulamanın kendi metin alanı `Modifier.keepsKeyboardOpen()` alır. Fare
  tıklamasını Compose kendisi yönetir (varsayılan olarak alanın dışına tıklamak odağı alır).
- **Oynatıcı:** tam ekran oynatıcı kapanınca alt çubuk yarım saniye gizli kalıp sonra birden belirmiyor: sayfa,
  yayının son piksel kesirlerini beklemeden mini oynatıcı göründüğü anda çekilir.
- **Kısayol ipuçları:** menülerde ve "⋯" sayfasında kısayollar (Ctrl+K gibi) yalnızca fare ya da donanım klavyesi
  varken yazılır. Yeni `hasHardwareKeyboard()`: klavye kapağı, Bluetooth ya da DeX klavyesi bağlı ve katlanmamışken
  true; örneğin ayarlardaki kısayol listesini yalnızca o zaman göstermek için.
- `StatusBanner` telefonda eylem düğmesini metnin altına koyar; metin dar bir sütuna sıkışmaz. `SwatchChoice`
  sığmayan renkleri ikinci satıra geçirir.

### Tablet ve DeX

- **Sürükle bırak:** Samsung Dosyalarım'daki gibi, işaretçi önce öğenin üstünde sistemin dokunup basılı tutma süresi
  kadar (Ayarlar › Erişilebilirlik, varsayılan yarım saniye) durmalı; daha önce kayarsa sürükleme olmaz, listede
  hızlıca gezinirken satır kapılmaz. Fare, touchpad ve kalemde sürükleme bundan sonra işaretçi kayınca başlar, yavaş
  bir tıklama (öğeye ya da içindeki düğmeye) tıklama olarak kalır; parmakta süre dolunca, `touch = true` ile. Satırın
  saydam kopyası yerine işaretçinin üstünde küçük bir kart taşınır: etiket ve birden çok öğede vurgu renginde sayı.
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

- Yeni testler: üst çubuğun solması (kare kare parlaklık), ilk açılıştaki boşluk, alt sayfanın her yerden hızlı ve
  yavaş çekilmesi, uçan kapağın köşeleri, basılı tutunca başlayan sürükleme, tıklama olarak kalan yavaş tıklama ve
  canlı kalan satırlar, parmak ve fareyle seçim, uzun basmanın seçtiği öğe, klavyenin kapanması ve kapanmaması
  (dokunuş, kaydırma, alan içinde kaydırma, Gönder düğmesi, fare tekerleği, ekran klavyesi yokken, klavye kapağıyla),
  klavye açıkken oynatıcı, oynatıcı kapanınca alt çubuk, bildirimlerin yer değiştirmesi, menünün konumu ve kısayol
  ipuçları.
- Robolectric'in penceresi klavye kipinde başlar; orada odak bırakılınca sistem onu hemen ilk alana geri verir.
  Klavye testleri, gerçek bir dokunuşun yaptığı gibi dokunma kipinde başlar.

### Bu sürümde olmayan

- **Sürüklerken kenar çubuğunda bekleyince ekranı açmak:** Compose bırakma hedeflerini sürükleme başlarken toplar;
  sürükleme sürerken açılan ekranın hedefleri bırakmayı almaz.
- **Uygun olmayan hedefleri soldurmak:** Compose sürüklemenin bittiğini yalnızca sürüklemeyi kabul eden hedefe
  söyler. Soldurmak için her hedefin her sürüklemeyi kabul edip yanlış türü bırakılırken geri çevirmesi gerekir;
  bu, düzeltmelerden oluşan bu sürüme göre büyük bir değişiklik.

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
