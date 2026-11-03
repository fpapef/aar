"""Petits outils adb pour le test sur emulateur : toucher un texte, faire defiler, capturer l'ecran."""
import base64, io, re, subprocess, sys, time


def adb(*args, **kw):
    return subprocess.run(["adb", *args], capture_output=True, **kw)


def noeuds():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml", text=True).stdout
    for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        yield m.group(1), (int(m.group(2)) + int(m.group(4))) // 2, (int(m.group(3)) + int(m.group(5))) // 2


def toucher(texte, essais=6):
    for _ in range(essais):
        for t, x, y in noeuds():
            if texte in t:
                adb("shell", "input", "tap", str(x), str(y))
                time.sleep(2)
                return True
        defiler()
    print(f"::error::Texte introuvable a l'ecran : {texte}")
    return False


def defiler():
    adb("shell", "input", "swipe", "540", "1700", "540", "700", "300")
    time.sleep(1)


def capture(nom):
    png = adb("exec-out", "screencap", "-p").stdout
    open(f"captures/{nom}.png", "wb").write(png)
    try:
        from PIL import Image
        im = Image.open(io.BytesIO(png)).convert("RGB")
        im.thumbnail((540, 1200))
        buf = io.BytesIO()
        im.save(buf, "JPEG", quality=72)
        print(f"===CAPTURE {nom}===")
        print(base64.b64encode(buf.getvalue()).decode())
        print(f"===FIN {nom}===")
    except Exception as e:  # la capture reste dans les artefacts
        print("capture non affichee :", e)


if __name__ == "__main__":
    cmd, *rest = sys.argv[1:]
    if cmd == "toucher":
        sys.exit(0 if toucher(rest[0]) else 1)
    elif cmd == "defiler":
        for _ in range(int(rest[0]) if rest else 1):
            defiler()
    elif cmd == "capture":
        capture(rest[0])
