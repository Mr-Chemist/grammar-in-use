# گرامر این یوز — نسخه کاتلین نیتیو (فوق‌سبک ۳ تا ۵ مگابایت · آماده مایکت و گیت‌هاب)

## مراحل کامل ساخت APK بدون Android Studio و انتشار در مایکت

### ۱. ساخت ۶ بسته ZIP (هر ۳۰ یونیت با هم) و داده‌های کتاب
```cmd
pip install -r scripts/requirements.txt
python scripts/prepare_book_assets.py --pdf "E:\EngData\Intermediate_Grammar_in_Use.pdf" --audio "E:\EngData\Audio CD"
```
- اول عکس `arvan_packs\preview_u004_lesson.jpg` را چک کنید تا از حذف کامل لوگوی سبز و آدرس مطمئن شوید.

### ۲. آپلود ۶ فایل ZIP در ابر آروان (Drag & Drop)
در پنل ابر آروان وارد صندوقچه `grammar-in-use-assets` شوید، پوشه `packs` بسازید (عمومی / Public) و ۶ فایل داخل `arvan_packs\packs\` (`pack_1.zip` تا `pack_5.zip` + `pack_resources.zip`) را بکشید و رها کنید.

### ۳. ساخت کلید امضای اختصاصی (Keystore) برای مایکت (فقط یک‌بار)
اگر Java JDK نصب دارید، در CMD بزنید (رمزها را یادداشت کنید و فایل `release.keystore` را جای امن نگه دارید — هرگز در GitHub آپلودش نکنید!):
```cmd
keytool -genkey -v -keystore release.keystore -alias grammar_key -keyalg RSA -keysize 2048 -validity 10000
```
سپس خروجی Base64 آن را بگیرید تا در GitHub Secrets بگذارید:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Set-Clipboard
```

### ۴. قرار دادن در GitHub و ساخت خودکار دو APK مایکت (بدون Android Studio)
1. یک مخزن (Repository) جدید در GitHub بسازید (فایل `.gitignore` طوری تنظیم شده که فایل‌های PDF و `arvan_packs` و Keystore آپلود نمی‌شوند).
2. در تنظیمات مخزن GitHub بروید به **Settings → Secrets and variables → Actions** و این ۴ Secret را اضافه کنید:
   - `KEYSTORE_BASE64`: متنی که در مرحله ۳ کپی کردید
   - `KEYSTORE_PASSWORD`: رمز Keystore
   - `KEY_ALIAS`: `grammar_key`
   - `KEY_PASSWORD`: رمز Key
3. کد پروژه را روی GitHub بفرستید (`git push` یا آپلود در GitHub).
4. وارد تب **Actions** در GitHub شوید؛ ورک‌فلو `Build Myket Release APKs` در حدود ۲ دقیقه هر دو فایل زیر را می‌سازد و در بخش **Artifacts → myket-release-apks** برای دانلود آماده می‌کند:
   - `app-arm64-v8a-release.apk` (`versionCode: 2001` — گوشی‌های جدید و اندروید ۱۵+)
   - `app-armeabi-v7a-release.apk` (`versionCode: 1001` — گوشی‌های قدیمی‌تر)

### ۵. انتشار در مایکت
در پنل توسعه‌دهندگان مایکت، یک برنامه جدید با شناسه `ir.hiwa.grammarinuse` بسازید و **هر دو فایل APK** را هم‌زمان در یک انتشار بارگذاری و ارسال کنید.
