"""Writes the launcher icons and the status bar icon from one drawing. Run: python tools/icons/gen.py

The logo is a speech bubble holding a pulse line. Each icon only changes the colours.
"""
import os

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "res")

# name: (background, bubble, pulse)
ICONS = {
    "amber": ("#FFC531", "#1E1A2E", "#FFC531"),
    "ink": ("#1E1A2E", "#FFC531", "#1E1A2E"),
    "harbour": ("#10264D", "#F4F7FF", "#E4573D"),
    "coral": ("#E4573D", "#FFF4E8", "#E4573D"),
    "mint": ("#0F6E5C", "#BDF5E3", "#0F6E5C"),
    "paper": ("#F4EFE6", "#25222B", "#F4EFE6"),
}

# On the 108dp adaptive grid, kept inside the 66dp safe zone
BUBBLE = "M35,33h38a6,6 0,0 1,6 6v22a6,6 0,0 1,-6 6H52L41,77V67h-6a6,6 0,0 1,-6 -6V39a6,6 0,0 1,6 -6z"
PULSE = "M36,50h9l4,-8 7,17 5,-9h11"


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def vector(paths, size=108):
    body = "\n".join(paths)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{size}dp" android:height="{size}dp"
    android:viewportWidth="{size}" android:viewportHeight="{size}">
{body}
</vector>
'''


def fill(color, data):
    return f'    <path android:fillColor="{color}" android:pathData="{data}" />'


def stroke(color, data, width):
    return (f'    <path android:strokeColor="{color}" android:strokeWidth="{width}" '
            f'android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{data}" />')


def adaptive(name):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_{name}_background" />
    <foreground android:drawable="@drawable/ic_launcher_{name}_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''


for name, (bg, bubble, pulse) in ICONS.items():
    write(f"{RES}/drawable/ic_launcher_{name}_background.xml", vector([fill(bg, "M0,0h108v108H0z")]))
    write(f"{RES}/drawable/ic_launcher_{name}_foreground.xml", vector([fill(bubble, BUBBLE), stroke(pulse, PULSE, 4)]))
    write(f"{RES}/mipmap-anydpi-v26/ic_launcher_{name}.xml", adaptive(name))

write(f"{RES}/mipmap-anydpi-v26/ic_launcher.xml", adaptive("amber"))
write(f"{RES}/drawable/ic_launcher_monochrome.xml", vector([stroke("#FFFFFF", BUBBLE, 3), stroke("#FFFFFF", PULSE, 3.5)]))

# Status bar icons are white on transparent, so the pulse is drawn as a line alone
write(f"{RES}/drawable/ic_stat_bleepp.xml", vector([stroke("#FFFFFF", "M2,12h5l2.500,-6 5,13 3,-7h4.500", 2.2)], 24))
print("icons written:", ", ".join(ICONS))
