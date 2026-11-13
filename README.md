# Survey Topografi - BLE Trilateration & Barometer

Aplikasi Android untuk pemetaan topografi 3D `(X, Y, Z)` menggunakan metode **Trilaterasi / Perpotongan Dua Lingkaran (Bluetooth Low Energy)** dan pengukuran elevasi menggunakan **Sensor Barometer** terkalibrasi.

## Arsitektur 3 Perangkat (2 Anchor + 1 Rover)
1. **Perangkat Anchor (`ANCHOR_1` & `ANCHOR_2`):**
    - Berfungsi sebagai titik patokan tetap (*benchmark*) yang diketahui koordinatnya.
    - Mengambil koordinat GPS & elevasi awal, lalu memancarkan paket data **13-byte BLE Service Data** (`[1B ID][4B Lat][4B Lon][4B Alt]`) menggunakan *Custom UUID* (`0000CAFE-0000-1000-8000-00805F9B34FB`).
2. **Perangkat Rover (`Alat Ukur`):**
    - Memindai sinyal BLE dari `ANCHOR_1` dan `ANCHOR_2`, membaca koordinat patokan secara otomatis dari paket iklan BLE, serta menghitung jarak ($d_1, d_2$) berdasarkan nilai RSSI.
    - Mengukur ketinggian/elevasi ($Z$) menggunakan sensor tekanan udara (`BarometerHelper`) yang dikalibrasi otomatis dengan elevasi referensi dari Anchor.
    - Menghitung posisi koordinat Rover menggunakan algoritma perpotongan dua lingkaran pada bidang proyeksi meter lokal (`Trilateration.kt`).
    - Menyimpan daftar titik ukur dan mengekspornya ke file `.csv` di folder publik `Downloads` (`ExportHelper.kt`).

## Cara Penggunaan
1. Siapkan 3 HP Android (2 sebagai **Mode Anchor**, 1 sebagai **Mode Rover**).
2. Pada HP Anchor 1 & 2, buka **Mode Anchor**, atur ID (`ANCHOR_1` dan `ANCHOR_2`), klik **Ambil Koordinat dari GPS**, lalu tekan **Mulai Siarkan Sinyal (BLE Broadcast)**.
3. Pada HP Rover, buka **Mode Rover**, klik **Mulai Scan Sinyal Anchor (BLE)** hingga kedua Anchor terdeteksi dan kolom koordinat terisi otomatis.
4. Klik **Hitung Posisi Trilaterasi (x, y, z)**, tekan **Simpan Titik** untuk merekam data pengukuran, lalu klik **Ekspor CSV** untuk mengunduh laporan ke folder `Downloads`.

## Kontributor
- Affifah Putri
- Zahra Nur'azijah Lutfiani