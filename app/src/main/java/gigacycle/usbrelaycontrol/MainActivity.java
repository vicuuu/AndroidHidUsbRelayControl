package gigacycle.usbrelaycontrol;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String ACTION_USB_PERMISSION = "gigacycle.usbrelaycontrol.USB_PERMISSION";

    private static final String PREFS = "relay_prefs";
    private static final String K1 = "relay_name_1";
    private static final String K2 = "relay_name_2";
    private static final String K3 = "relay_name_3";
    private static final String K4 = "relay_name_4";
    private static final String KLOG = "app_log";

    private UsbManager usbManager;
    private Switch sw1, sw2, sw3, sw4;

    private final List<UsbDevice> devices = new ArrayList<>();
    private volatile boolean isUpdatingUi = false;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action) ||
                    UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                refreshList();
                return;
            }

            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice dev = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (dev != null) {
                    log("Permission " + (granted ? "OK" : "DENIED") + " dla: " + dev.getDeviceName());
                }
                refreshList();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        sw1 = findViewById(R.id.swRelay1);
        sw2 = findViewById(R.id.swRelay2);
        sw3 = findViewById(R.id.swRelay3);
        sw4 = findViewById(R.id.swRelay4);

        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class))
        );

        sw1.setOnCheckedChangeListener((b, on) -> { if (!isUpdatingUi) send(1, on); });
        sw2.setOnCheckedChangeListener((b, on) -> { if (!isUpdatingUi) send(2, on); });
        sw3.setOnCheckedChangeListener((b, on) -> { if (!isUpdatingUi) send(3, on); });
        sw4.setOnCheckedChangeListener((b, on) -> { if (!isUpdatingUi) send(4, on); });

        IntentFilter f = new IntentFilter();
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        f.addAction(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, f);

        applyRelayNames();
        refreshList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyRelayNames();
    }

    private void applyRelayNames() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        sw1.setText(p.getString(K1, "Przekaźnik 1"));
        sw2.setText(p.getString(K2, "Przekaźnik 2"));
        sw3.setText(p.getString(K3, "Przekaźnik 3"));
        sw4.setText(p.getString(K4, "Przekaźnik 4"));
    }

    private UsbDevice getSelectedDevice() {
        return devices.isEmpty() ? null : devices.get(0);
    }

    private void send(int relay, boolean on) {
        UsbDevice d = getSelectedDevice();
        if (d == null) {
            log("Brak modulow USB");
            return;
        }

        if (!usbManager.hasPermission(d)) {
            log("Brak permission - zaakceptuj okno USB");
            requestPerm(d);
            return;
        }

        controlRelay(d, relay, on);
    }

    private void requestPerm(UsbDevice d) {
        PendingIntent pi = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(ACTION_USB_PERMISSION),
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        ? PendingIntent.FLAG_IMMUTABLE
                        : 0
        );
        usbManager.requestPermission(d, pi);
    }

    private void controlRelay(UsbDevice device, int relay, boolean on) {
        UsbDeviceConnection conn = usbManager.openDevice(device);
        if (conn == null) {
            log("Nie mozna otworzyc urzadzenia");
            return;
        }

        UsbInterface intf = device.getInterface(0);
        if (!conn.claimInterface(intf, true)) {
            conn.close();
            log("Nie mozna przejac interfejsu");
            return;
        }

        UsbEndpoint out = null;
        for (int i = 0; i < intf.getEndpointCount(); i++) {
            UsbEndpoint ep = intf.getEndpoint(i);
            if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                out = ep;
                break;
            }
        }

        if (out == null) {
            conn.releaseInterface(intf);
            conn.close();
            log("Brak endpoint OUT");
            return;
        }

        byte[] cmd = new byte[64];
        cmd[0] = (byte) 0xA0;
        cmd[1] = (byte) relay;
        cmd[2] = (byte) (on ? 0x01 : 0x00);
        cmd[3] = (byte) (0xA0 + relay + (on ? 1 : 0));  // checksum

        int r = conn.bulkTransfer(out, cmd, 64, 3000);

        conn.releaseInterface(intf);
        conn.close();

        if (r == 64) {
            log("Relay " + relay + " -> " + (on ? "ON" : "OFF"));

            runOnUiThread(() -> {
                isUpdatingUi = true;
                try {
                    if (relay == 1) sw1.setChecked(on);
                    if (relay == 2) sw2.setChecked(on);
                    if (relay == 3) sw3.setChecked(on);
                    if (relay == 4) sw4.setChecked(on);
                } finally {
                    isUpdatingUi = false;
                }
            });
        } else {
            log("Blad wysylania: " + r);
        }
    }

    private void refreshList() {
        devices.clear();

        for (UsbDevice d : usbManager.getDeviceList().values()) {
            if (d.getVendorId() == 0x5131 && d.getProductId() == 0x2007) {
                devices.add(d);
                if (!usbManager.hasPermission(d)) requestPerm(d);
            }
        }

        boolean enabled = !devices.isEmpty();
        sw1.setEnabled(enabled);
        sw2.setEnabled(enabled);
        sw3.setEnabled(enabled);
        sw4.setEnabled(enabled);

        log("Znaleziono " + devices.size() + " modulow USB");
    }

    private void log(String s) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String line = "[" + ts + "] " + s;

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String old = p.getString(KLOG, "");
        String merged = (old == null || old.isEmpty()) ? line : (old + "\n" + line);

        int maxChars = 20000;
        if (merged.length() > maxChars) {
            merged = merged.substring(merged.length() - maxChars);
        }

        p.edit().putString(KLOG, merged).apply();
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(usbReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
