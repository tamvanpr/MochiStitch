# MochiStitch release rules.
# R8: pustaka arsip dipakai lewat kode yang dirujuk langsung, tetap
# disimpan penuh agar pembacaan RAR/7Z tidak rusak setelah minify.
-keep class com.github.junrar.** { *; }
-keep class org.apache.commons.compress.archivers.sevenz.** { *; }
-keep class org.tukaani.xz.** { *; }
