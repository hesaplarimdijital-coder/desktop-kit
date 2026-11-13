# Yapay zekâ yardımcıları için

Bu depoda ya da bu kitle kurulan bir uygulamada çalışan bir yardımcının (Claude Code ve benzerleri)
bilmesi gerekenler. Görsel değerler [DESIGN.md](DESIGN.md)'de, kurallar "Desktop Kit Tasarım
Standardı" belgesinde, sürümler arası geçiş [MIGRATION.md](MIGRATION.md)'de.

## Ortam

- Kotlin 2.4.20, Compose BOM 2026.09.00 (Compose 1.12), AGP 9.4.1, Gradle 9.8.2,
  navigation-compose 2.10.2, Haze 2.0.1. Java 17 bayt kodu; testler Java 21 ile çalışır.
- minSdk 34, compileSdk 37. Hedef cihazlar: Galaxy S24 (telefon, üç düğmeli gezinme) ve Galaxy Tab
  S11 Ultra (tablet, DeX); ikisi de Android 17.
- Uygulamada: `implementation("com.github.hesaplarimdijital-coder:desktop-kit:<sürüm>")` (JitPack).
- Derleme ve testler GitHub Actions'ta çalışır (`.github/workflows/ci.yml`): birim testleri ve
  Robolectric ekran görüntüleri, ekran görüntüsü karşılaştırması, `tools/design_check.py`.

## Uygulama yazarken

- İskelet her zaman `DesktopKitRoot` + `DesktopShell`. Ekranlar pencere sınıfına göre düzenlenir
  (`currentWidthClass()`); cihaz türüne, yönelime ya da ekran boyutuna bakılmaz.
- Material bileşenleri kullanılmaz; kitte karşılığı yoksa önce sorulur: kite mi yazılacak,
  uygulamaya mı. Kitte bulunan bir hata uygulamada yamanmaz, kite bildirilir.
- Renkler `DeskTheme.colors`'tan, ölçüler `DeskTheme.dimens`'ten, yazılar `DeskTheme.type`'tan
  alınır. `Color(0x…)`, ızgara dışı `dp` ve elle yazılmış yazı boyu yazılmaz.
- Bildirim için `ToastState` (`android.widget.Toast` değil); geri alınabilir eylem "Geri al" ile.
- Her düğme bir şey yapar. Henüz yapılmamış bir eylem ya görünmez ya da bunu söyleyen bir bildirim
  gösterir; `onClick = {}` bırakılmaz.
- Bütün metinler `strings.xml`'de, Türkçe (`values-tr`) ve İngilizce (`values`). Sayılar için
  `plurals`; cümle kod içinde parça parça birleştirilmez. Ünlem ve "..." yok; üç nokta için "…".
- Telefonda liste çubukların altından geçer: `rememberShellContentPadding()` listenin
  `contentPadding`'ine verilir. WebView ya da video gösteren ekran `PauseGlassBlur()` çağırır.
- Liste–ayrıntı için `ListDetailLayout` (tablet genişliğinden yan yana, daha darda `DetailTransition`'ın tek
  bölmesi); telefonda ayrıntının üst çubuğu `CompactTopBar` ile.
- Satır animasyonu (`ListAppear`) yalnızca veri iskeletten sonra geldiğinde; anahtar olarak yükleme
  durumu verilir.
- Kısayollar tek bir `ShortcutRegistry`'de; menü çubuğu, Meta+/ listesi ve komut paleti oradan
  beslenir.

## Kitte çalışırken

- Bir iş için bir dal; her adım ayrı bir gönderi ve CI'da yeşil; ilk gönderiden sonra taslak PR.
- Birleştirme ve etiket depo sahibinindir. Gönderilmiş geçmiş yeniden yazılmaz, zorla gönderilmez.
- Gönderi yazarı: hesaplarim.dijital@gmail.com. CHANGELOG, README, MIGRATION ve PR açıklamaları
  Türkçe; kod ve yorumlar İngilizce.
- Tabletin ve DeX'in görünüşü ve zamanlamaları sahibinin onayı olmadan değişmez. Onaylanmış görünüş
  değişiklikleri sürümün kendi PR'ında, CHANGELOG'da "görünür" diye işaretlenip ekran görüntüsü farklarıyla
  sunulur (`ci/screenshots-pr-<numara>` dalı); bir iş sürümlere bölünmez, sahibi istemedikçe.
- Herkese açık bir API'ye parametre eklerken varsayılanlı ve sona eklenir; konumla çağıran kod
  bozulmaz. Bileşen dışı fonksiyonlarda `@JvmOverloads`.
- Davranışı değişen her şeyin testi olur; bir hata önce onu gösteren bir testle kaydedilir.
- `tools/design_check.py` yeni bir bulgu kabul etmez; bir bulgu azaldıysa taban çizgisi
  `--update` ile indirilir.
