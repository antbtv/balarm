# R8 для release (M8). Библиотеки (Hilt, Room, kotlinx.serialization, Compose, AndroidX) приносят consumer-rules сами;
# здесь — только то, что нужно приложению. Компоненты манифеста (сервисы, ресиверы, Activity) AGP сохраняет автоматически.

# Стектрейсы падений читаемы по mapping-файлу.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
