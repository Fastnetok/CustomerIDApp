# CustomerIDApp — Android Studio Starter Project

یہ Project آپ کی بتائی گئی Requirements پر مبنی ہے: GPS first-activation workflow،
Payment Sources (Faysal Bank, Bank Alfalah, JazzCash, Easypaisa, SadaPay, Raast ID),
SMS Reader → OCR → AI fallback Payment Verification Engine, Manual T-ID entry,
ہمیشہ نظر آنے والی Package Price، اور Insufficient Balance rule۔

## ⚠️ Filhaal Design-Preview Mode Chalu Hai

Firebase abhi disable kar diya gaya hai (`app/build.gradle` mein `google-services` plugin comment out hai)
taake app **google-services.json ke bagair bhi turant Run ho jaye** aur aap Home Dashboard Design
check kar sakein. App direct `HomeActivity` (mock/sample data ke sath) se open hoga, Login screen abhi Launcher nahi hai.

**Jab Firebase/Coding par kaam karna ho, yeh steps karein:**
1. `app/build.gradle` mein `id 'com.google.gms.google-services'` line se `//` hata dein۔
2. Firebase Console se `google-services.json` download kar ke `app/` folder mein rakhein۔
3. `AndroidManifest.xml` mein Launcher intent-filter wapis `LoginActivity` par le jayein
   (abhi `HomeActivity` par hai, preview ke liye)۔
4. `HomeActivity.kt` mein `loadPreviewData()` hata kar sirf `loadDashboard()` use karein۔

## Android Studio میں کھولنے کا طریقہ

1. Android Studio کھولیں → **Open** → یہ `CustomerIDApp` فولڈر منتخب کریں۔
2. Gradle Sync خودکار ہو جائے گا (پہلی بار انٹرنیٹ درکار ہوگا تاکہ Gradle/Dependencies ڈاؤن لوڈ ہوں)۔
3. **Firebase Console** پر جا کر ایک نیا Android App بنائیں (applicationId: `com.ebone.customeridapp`)
   اور اس سے ملنے والی `google-services.json` فائل کو `app/` فولڈر میں رکھیں
   (`app/google-services.json` — یہ فائل جان بوجھ کر `.gitignore` میں شامل ہے)۔
4. Firestore میں `customers` collection بنائیں، ہر document کی id = Customer ID،
   اور fields: `name, phone, packageId, packagePrice, currentBalance, latitude, longitude, locationCapturedAt, isActive`۔
5. Run کریں (▶) کسی Emulator یا Real Device پر۔

## موجودہ Project کا Structure

```
app/src/main/java/com/ebone/customeridapp/
 ├─ ui/login/LoginActivity.kt        → Customer ID Login + پہلی بار GPS Capture
 ├─ ui/packages/PackageActivity.kt   → Package Price ہمیشہ نمایاں
 ├─ ui/payment/PaymentActivity.kt    → Payment Source, Manual T-ID, Insufficient Balance Rule
 ├─ ui/location/LocationHelper.kt    → GPS صرف پہلی Activation پر Firebase میں Save
 ├─ data/Customer.kt                 → Data models (Customer, PaymentTransaction, enums)
 ├─ data/FirestoreRepository.kt      → تمام Firestore Reads/Writes + Business Rules
 ├─ data/PaymentSmsReceiver.kt       → آنے والے Bank/Wallet SMS کی Detection
 ├─ data/SmsPaymentParser.kt         → SMS سے Amount + T-ID نکالنا (Regex)
 ├─ data/OcrPaymentReader.kt         → Screenshot سے ML Kit OCR (SMS ناکام ہونے پر Fallback)
 └─ data/AiPaymentInterpreter.kt     → OCR بھی ناکام ہو تو AI/ChatGPT سے تشریح
```

## Payment Verification Engine کا Flow

```
SMS Reader (خودکار) → ناکام؟ → OCR (Screenshot) → ناکام؟ → AI/ChatGPT Interpretation
                                                                      ↓
                                              Manual T-ID Entry (ہمیشہ دستیاب Override)
```

## اہم Business Rules (پہلے سے Code میں شامل)

- **Insufficient Balance:** اگر Paid Amount < Package Price → Status = `INSUFFICIENT`,
  Recharge Block ہو جاتا ہے اور پیغام دکھایا جاتا ہے (`FirestoreRepository.evaluatePaymentStatus`)۔
- **GPS صرف ایک بار:** `locationCapturedAt` موجود ہونے پر دوبارہ Location کبھی Save نہیں ہوتی۔
- **Package Price ہمیشہ نمایاں:** Package Screen اور Payment Screen دونوں پر بڑے، رنگین Text میں دکھائی جاتی ہے۔

## ابھی باقی کام (اگلے Steps)

- [ ] Ebone Admin Panel میں **Customer Bill Pay Menu** — Logout اور Settings کے درمیان (یہ Admin Panel کا الگ Module ہے، ابھی صرف CustomerIDApp پر کام ہوا ہے)۔
- [ ] Employee App Integration — ابھی Scope میں شامل نہیں۔
- [ ] Screenshot Upload → Image Picker UI (`PaymentActivity.btnUploadScreenshot` میں TODO موجود ہے)۔
- [ ] AI/ChatGPT API Key کو Secure طریقے سے Store کرنا (فی الحال Placeholder ہے)۔
- [ ] Manual Bank Addition (مستقبل کی Feature) کے لیے Admin-configurable Payment Sources۔
- [ ] Firebase Auth کے ذریعے حقیقی Login Security (فی الحال صرف Customer ID lookup ہے)۔

## اگلا حصہ کیا بنایا جائے؟

بتا دیں تو میں **Admin Panel کا Customer Bill Pay Menu**، یا **Screenshot Upload + OCR UI**،
یا **AI Fallback کی مکمل Integration** میں سے اگلا حصہ بنا دیتا ہوں۔
