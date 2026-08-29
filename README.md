# LinkDrop V1 — تعديل KDE Connect Android

هذه حزمة تعديل جاهزة لتطبيق KDE Connect Android.

## تعديلات V1
- اسم افتراضي جديد: **LinkDrop** (اسم مؤقت).
- Application ID مستقل: `com.linkdrop.connect`.
- تثبيت النسخة بجانب KDE Connect الرسمي.
- وضع مبسط افتراضيًا:
  - مشاركة الملفات والنصوص والروابط.
  - مزامنة الحافظة.
  - Ping لاختبار الاتصال.
- زر جديد: **إرسال إلى جميع الأجهزة**.
- تعريب الزر الجديد.
- الاحتفاظ بنسخة احتياطية من الملفات الأصلية في `.linkdrop_backup`.

## Windows — الطريقة الأسرع
تحتاج Git + Python 3 + Android Studio/JDK.

افتح PowerShell في المجلد وشغّل:

```powershell
.\setup_windows.ps1
```

السكريبت ينزل المصدر الرسمي، يطبق التعديل، ثم يشغّل Build للنسخة Debug.

## يدويًا
```bash
git clone https://github.com/KDE/kdeconnect-android.git
python apply_linkdrop_customization.py kdeconnect-android --build
```

تغيير الاسم والـ Package ID:

```bash
python apply_linkdrop_customization.py kdeconnect-android --name "MyDrop" --application-id "com.example.mydrop" --build
```

الاحتفاظ بكل ميزات KDE Connect بدل الوضع المبسط:

```bash
python apply_linkdrop_customization.py kdeconnect-android --full-features --build
```

## APK
بعد نجاح البناء ستجده عادة في:

`kdeconnect-android/build/outputs/apk/debug/`

## مهم
هذه الحزمة تحتوي **تعديل المصدر وأداة تطبيقه**، وليست APK مبنيًا مسبقًا.

كود KDE Connect يستخدم GPL، لذلك إذا وزعت نسخة مشتقة يجب الالتزام بمتطلبات الرخصة وعدم تقديم التطبيق كأنه النسخة الرسمية.
