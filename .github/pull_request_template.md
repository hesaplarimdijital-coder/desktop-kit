## Ne değişti

<!-- Kısa: neyi, neden. Bir hatanın düzeltmesiyse nerede görüldüğü (uygulama, cihaz, kayıt). -->

## Görünüş ve hareket

- [ ] Tabletin ve DeX'in görünüşü değişmedi, ya da değişiklik ekran görüntüleriyle gösterildi ve onaylandı
- [ ] Telefon (Compact), tablet (Medium/Expanded) ve DeX (Large) ekran görüntüleri bakıldı (`ci/screenshots-pr-<numara>`)
- [ ] Yeni renk, ölçü ve yazı yok; `DeskTheme` değerleri kullanıldı (`tools/design_check.py` yeşil)
- [ ] Hareket yalnızca bilgi taşıyor; "Animasyonları kaldır" açıkken hiçbir şey hareket etmiyor

## Girdi ve erişilebilirlik

- [ ] Parmakla, fareyle ve klavyeyle denendi ya da testi var (sağ tık, uzun basma, Tab, Esc, Enter)
- [ ] Dokunmatik hedefler 48 dp, pointer hedefleri en az 24 dp
- [ ] TalkBack her etkileşimli öğenin adını ve durumunu okuyor
- [ ] Yazı %200'de kırpılmıyor

## Metin ve belgeler

- [ ] Yeni metinler `values` ve `values-tr`'de; sayılar `plurals` ile
- [ ] CHANGELOG, MIGRATION ve README (gerekiyorsa) Türkçe güncellendi
- [ ] Herkese açık API değişikliği varsa MIGRATION.md'de nasıl geçileceği yazıyor
