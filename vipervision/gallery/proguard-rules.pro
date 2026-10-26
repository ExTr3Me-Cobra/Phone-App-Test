# Release shrinking rules for Gallery inside the combined app.
# Reprint's old fingerprint module uses an Android API that newer SDKs no longer ship.
-dontwarn android.hardware.fingerprint.FingerprintManager$AuthenticationCallback
-dontwarn android.hardware.fingerprint.FingerprintManager$AuthenticationResult
-dontwarn android.hardware.fingerprint.FingerprintManager$CryptoObject
-dontwarn android.hardware.fingerprint.FingerprintManager
# Gallery's JPEG XL Glide plugin expects an older jxl-coder than Image Toolbox brings.
-dontwarn com.awxkee.jxlcoder.JxlResizeFilter
