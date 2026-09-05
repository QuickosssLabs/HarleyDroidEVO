# Keep ELM327 / Bluetooth reflection helpers
-keepclassmembers class android.bluetooth.BluetoothDevice {
    public *** createRfcommSocket(...);
}
