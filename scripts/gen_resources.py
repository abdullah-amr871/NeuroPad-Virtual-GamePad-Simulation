"""Generates the small XML resources and the vector icon of the NeuroPad app.

The launcher icon, its monochrome variant, the in-app logo and the status-bar icon all
share one gamepad outline, so they are produced from a single path definition here.

    python scripts/gen_resources.py
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

# Gamepad body with a D-pad and two face buttons cut out (even-odd fill), 108x108 viewport.
PAD = (
    "M38,42h32a14,14 0,0 1,14 14l2,10a6,6 0,0 1,-11 4l-5,-8h-32l-5,8a6,6 0,0 1,-11 -4"
    "l2,-10a14,14 0,0 1,14 -14z"
    "M38.4,47h3.2v3.4h3.4v3.2h-3.4v3.4h-3.2v-3.4h-3.4v-3.2h3.4z"
    "M65,55m-2.8,0a2.8,2.8 0,1 0,5.6 0a2.8,2.8 0,1 0,-5.6 0"
    "M71.5,49.5m-2.8,0a2.8,2.8 0,1 0,5.6 0a2.8,2.8 0,1 0,-5.6 0"
)
WAVES_NEAR = "M15,45q-5,10 0,20M93,45q5,10 0,20"
WAVES_FAR = "M7,40q-8,15 0,30M101,40q8,15 0,30"

HEAD = '<?xml version="1.0" encoding="utf-8"?>\n'
VECTOR = (
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    xmlns:aapt="http://schemas.android.com/aapt"\n'
    '    android:width="{size}dp" android:height="{size}dp"\n'
    '    android:viewportWidth="108" android:viewportHeight="108">\n{body}</vector>\n'
)


def group(scale: float, body: str) -> str:
    return (
        f'    <group android:pivotX="54" android:pivotY="55" android:scaleX="{scale}" android:scaleY="{scale}">\n'
        f"{body}    </group>\n"
    )


def fill(color: str, path: str) -> str:
    return f'        <path android:fillColor="{color}" android:fillType="evenOdd"\n            android:pathData="{path}" />\n'


def stroke(color: str, path: str, width: float = 3.4) -> str:
    return (
        f'        <path android:strokeColor="{color}" android:strokeWidth="{width}" android:strokeLineCap="round"\n'
        f'            android:pathData="{path}" />\n'
    )


FILES = {
    "values/strings.xml": HEAD + (
        "<resources>\n"
        '    <string name="app_name">NeuroPad</string>\n'
        '    <string name="tile_label">NeuroPad bridge</string>\n'
        "</resources>\n"
    ),
    "values/colors.xml": HEAD + (
        "<resources>\n"
        '    <color name="np_background">#0A0E1A</color>\n'
        '    <color name="np_primary">#7C5CFF</color>\n'
        '    <color name="np_secondary">#22D3EE</color>\n'
        "</resources>\n"
    ),
    "values/themes.xml": HEAD + (
        "<resources>\n"
        '    <style name="Theme.NeuroPad" parent="android:Theme.Material.NoActionBar">\n'
        '        <item name="android:windowBackground">@color/np_background</item>\n'
        '        <item name="android:statusBarColor">@color/np_background</item>\n'
        '        <item name="android:navigationBarColor">@color/np_background</item>\n'
        '        <item name="android:colorAccent">@color/np_primary</item>\n'
        "    </style>\n"
        "</resources>\n"
    ),
    "xml/file_paths.xml": HEAD + '<paths>\n    <cache-path name="shared" path="shared/" />\n</paths>\n',
    "xml/device_filter.xml": HEAD + (
        "<!-- Devices that offer to open NeuroPad when plugged in (same list as 1.0). -->\n"
        "<resources>\n"
        "    <!-- Xbox 360 wired controller and anything exposing an XInput interface -->\n"
        '    <usb-device vendor-id="1118" product-id="654" />\n'
        '    <usb-device class="255" subclass="93" protocol="1" />\n'
        '    <usb-device class="255" subclass="93" protocol="129" />\n'
        "    <!-- GameSir -->\n"
        '    <usb-device vendor-id="13623" />\n'
        "    <!-- Other vendors that ship XInput pads -->\n"
        + "".join(f'    <usb-device vendor-id="{v}" />\n' for v in (12068, 4614, 9414, 3695, 1848, 5426, 3853))
        + "</resources>\n"
    ),
    "mipmap-anydpi-v26/ic_launcher.xml": HEAD + (
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@drawable/ic_launcher_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
        "</adaptive-icon>\n"
    ),
    # Android 7.x has no adaptive icons: stack the same two layers in a plain layer list.
    "mipmap/ic_launcher.xml": HEAD + (
        '<layer-list xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <item android:drawable="@drawable/ic_launcher_background" />\n'
        '    <item android:drawable="@drawable/ic_launcher_foreground" />\n'
        "</layer-list>\n"
    ),
    "drawable/ic_launcher_background.xml": HEAD + VECTOR.format(size=108, body=(
        '    <path android:pathData="M0,0h108v108h-108z">\n'
        '        <aapt:attr name="android:fillColor">\n'
        '            <gradient android:type="linear"\n'
        '                android:startX="0" android:startY="0" android:endX="108" android:endY="108">\n'
        '                <item android:offset="0" android:color="#FF7C5CFF" />\n'
        '                <item android:offset="0.55" android:color="#FF4F46E5" />\n'
        '                <item android:offset="1" android:color="#FF0EA5C9" />\n'
        "            </gradient>\n"
        "        </aapt:attr>\n"
        "    </path>\n"
        '    <path android:fillColor="#14FFFFFF"\n'
        '        android:pathData="M54,54m-34,0a34,34 0,1 1,68 0a34,34 0,1 1,-68 0" />\n'
    )),
    "drawable/ic_launcher_foreground.xml": HEAD + VECTOR.format(size=108, body=group(
        0.74, fill("#FFFFFFFF", PAD) + stroke("#FFFFFFFF", WAVES_NEAR) + stroke("#99FFFFFF", WAVES_FAR),
    )),
    "drawable/ic_launcher_monochrome.xml": HEAD + VECTOR.format(size=108, body=group(
        0.74, fill("#FF000000", PAD) + stroke("#FF000000", WAVES_NEAR + WAVES_FAR),
    )),
    "drawable/ic_logo.xml": HEAD + VECTOR.format(size=48, body=group(
        1.0, fill("#FFFFFFFF", PAD) + stroke("#FFFFFFFF", WAVES_NEAR) + stroke("#99FFFFFF", WAVES_FAR),
    )),
    "drawable/ic_stat_neuropad.xml": HEAD + VECTOR.format(size=24, body=group(
        1.1, fill("#FFFFFFFF", PAD) + stroke("#FFFFFFFF", WAVES_NEAR, 4),
    )),
}

if __name__ == "__main__":
    for rel, text in FILES.items():
        target = RES / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8", newline="\n")
        print("wrote", target.relative_to(ROOT).as_posix())
