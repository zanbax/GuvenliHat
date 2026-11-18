"""WeAreNear / Güvenli Hat simgesi (5 numaralı tasarım) için Android vektörleri ve önizlemeler üretir.

Tasarım 150x150'lik bir kutuda çizildi:
  büyük beyaz kalp : translate(65,74) scale(0.82)
  yeşil ahize      : translate(45.8,60.8) scale(1.6)   (24'lük Material ahize yolu)
  küçük sarı kalp  : translate(107,94) rotate(12) scale(0.4), yeşil kontur 9
"""
import math
import re
import sys
from pathlib import Path

HEART = ("M0 48 C -34 23 -50 5 -50 -14 C -50 -28 -38 -38 -25 -38 C -13 -38 -5 -31 0 -22 "
         "C 5 -31 13 -38 25 -38 C 38 -38 50 -28 50 -14 C 50 5 34 23 0 48 Z")
HANDSET = ("M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 "
           "3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 "
           "1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z")

GREEN = "#34A065"
GOLD = "#F2C14E"
WHITE = "#FFFFFF"

TOKEN = re.compile(r"[MmLlHhVvCcZz]|-?\d*\.?\d+(?:[eE][-+]?\d+)?")
ARGS = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "Z": 0}


def parse_abs(d):
    """Yolu mutlak M/L/C/Z komut listesine çevirir: [(cmd, [x1,y1,...]), ...]."""
    toks = TOKEN.findall(d)
    out, i, cmd = [], 0, None
    cx = cy = sx = sy = 0.0
    while i < len(toks):
        t = toks[i]
        if t.isalpha():
            cmd = t
            i += 1
            if cmd in "Zz":
                out.append(("Z", []))
                cx, cy = sx, sy
                continue
        n = ARGS[cmd.upper()]
        vals = [float(v) for v in toks[i:i + n]]
        i += n
        rel = cmd.islower()
        U = cmd.upper()
        if U == "M":
            x, y = vals
            if rel:
                x, y = cx + x, cy + y
            out.append(("M", [x, y]))
            cx, cy = sx, sy = x, y
            cmd = "l" if rel else "L"  # ardışık koordinatlar çizgi sayılır
        elif U == "L":
            x, y = vals
            if rel:
                x, y = cx + x, cy + y
            out.append(("L", [x, y]))
            cx, cy = x, y
        elif U == "H":
            x = vals[0] + (cx if rel else 0)
            out.append(("L", [x, cy]))
            cx = x
        elif U == "V":
            y = vals[0] + (cy if rel else 0)
            out.append(("L", [cx, y]))
            cy = y
        elif U == "C":
            p = vals
            if rel:
                p = [p[0] + cx, p[1] + cy, p[2] + cx, p[3] + cy, p[4] + cx, p[5] + cy]
            out.append(("C", p))
            cx, cy = p[4], p[5]
    return out


def affine(tx=0.0, ty=0.0, rot_deg=0.0, s=1.0):
    """translate(tx,ty) rotate(rot) scale(s) — SVG sırası: önce ölçek, sonra dönüş, sonra öteleme."""
    a = math.radians(rot_deg)
    c, si = math.cos(a) * s, math.sin(a) * s
    return (c, si, -si, c, tx, ty)  # (a,b,c,d,e,f)


def compose(m1, m2):
    """m1 * m2 (önce m2 uygulanır)."""
    a1, b1, c1, d1, e1, f1 = m1
    a2, b2, c2, d2, e2, f2 = m2
    return (a1 * a2 + c1 * b2, b1 * a2 + d1 * b2,
            a1 * c2 + c1 * d2, b1 * c2 + d1 * d2,
            a1 * e2 + c1 * f2 + e1, b1 * e2 + d1 * f2 + f1)


def apply(m, x, y):
    a, b, c, d, e, f = m
    return a * x + c * y + e, b * x + d * y + f


def fmt(v):
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def transform_path(d, m, comma=False):
    parts = []
    for cmd, vals in parse_abs(d):
        if cmd == "Z":
            parts.append("Z")
            continue
        pts = []
        for k in range(0, len(vals), 2):
            x, y = apply(m, vals[k], vals[k + 1])
            pts.append(f"{fmt(x)},{fmt(y)}" if comma else f"{fmt(x)} {fmt(y)}")
        parts.append(cmd + " ".join(pts))
    return " ".join(parts)


def scale_of(m):
    return math.hypot(m[0], m[1])


# 150'lik tasarım kutusundaki parçalar
M_HEART = affine(65, 74, 0, 0.82)
M_HAND = affine(45.8, 60.8, 0, 1.6)
M_SMALL = affine(107, 94, 12, 0.4)
SMALL_STROKE = 9
# Tek renkli simgede küçük kalbin çevresindeki boşluk: kontur kalınlığı kadar büyütülmüş küçük kalp
M_SMALL_GAP = affine(107, 94, 12, 0.4 * (100 + SMALL_STROKE) / 100)


def layers(G, comma=False):
    """G: 150'lik kutudan hedef koordinatlara dönüşüm."""
    mh, mp, ms = compose(G, M_HEART), compose(G, M_HAND), compose(G, M_SMALL)
    return {
        "heart": transform_path(HEART, mh, comma),
        "handset": transform_path(HANDSET, mp, comma),
        "small": transform_path(HEART, ms, comma),
        "small_stroke": SMALL_STROKE * scale_of(ms),
        "small_gap": transform_path(HEART, compose(G, M_SMALL_GAP), comma),
    }


def argb(hex_rgb):
    return "#FF" + hex_rgb[1:]


def vector_xml(comment, size_dp, viewport, body):
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- {comment} -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size_dp}dp" android:height="{size_dp}dp"\n'
            f'    android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">\n'
            f'{body}</vector>\n')


def vpath(d, fill, stroke=None, sw=None, even_odd=False):
    attrs = [f'android:fillColor="{argb(fill)}"']
    if stroke:
        attrs.append(f'android:strokeColor="{argb(stroke)}"')
        attrs.append(f'android:strokeWidth="{fmt(sw)}"')
        attrs.append('android:strokeLineJoin="round"')
    if even_odd:
        attrs.append('android:fillType="evenOdd"')
    return f'    <path {" ".join(attrs)}\n        android:pathData="{d}" />\n'


def main(res_dir, preview_dir):
    res = Path(res_dir)
    prev = Path(preview_dir)
    prev.mkdir(parents=True, exist_ok=True)

    # 1) Uyarlanabilir simge ön katmanı: 108 görünüm, içerik merkezi (76,78.4) -> (54,54), ölçek 0.52
    G108 = affine(54 - 76 * 0.52, 54 - 78.4 * 0.52, 0, 0.52)
    L = layers(G108, comma=True)
    fg = (vpath(L["heart"], WHITE) + vpath(L["handset"], GREEN)
          + vpath(L["small"], GOLD, GREEN, L["small_stroke"]))
    (res / "drawable/ic_launcher_foreground.xml").write_text(vector_xml(
        "Uyarlanabilir simgenin ön katmanı: içinde ahize olan beyaz kalp ve ona yaslanan küçük sarı kalp",
        108, 108, fg), encoding="utf-8")

    # 2) Tek renkli (temalı simge) katman: ahize kalpte delik olarak
    mono = (vpath(L["heart"] + " " + L["handset"] + " " + L["small_gap"], WHITE, even_odd=True)
            + vpath(L["small"], WHITE))
    (res / "drawable/ic_launcher_monochrome.xml").write_text(vector_xml(
        "Temalı (tek renkli) simge: ahize kalbin içinde boşluk olarak",
        108, 108, mono), encoding="utf-8")

    # 3) Android 7 için yuvarlak simge: 48 görünüm, 108'lik içeriğin 72dp'lik görünür alanı 46'ya oturur
    G48 = compose(affine(-10.5, -10.5, 0, 0.639), G108)
    L48 = layers(G48, comma=True)
    legacy = ('    <path android:fillColor="#FF34A065" android:pathData="M1,24a23,23 0 1,0 46,0a23,23 0 1,0 -46,0z" />\n'
              + vpath(L48["heart"], WHITE) + vpath(L48["handset"], GREEN)
              + vpath(L48["small"], GOLD, GREEN, L48["small_stroke"]))
    (res / "drawable/ic_launcher.xml").write_text(vector_xml(
        "Android 7 için yuvarlak simge (8 ve üstünde drawable-anydpi-v26 kullanılır)",
        48, 48, legacy), encoding="utf-8")

    # 4) Önizleme SVG'leri (aynı mutlak yollarla): daire maskesi, yuvarlak kare maskesi, temalı, eski
    def svg(w, body):
        return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{w}" viewBox="0 0 {w} {w}">{body}</svg>'

    def colored(Lx):
        return (f'<path d="{Lx["heart"]}" fill="{WHITE}"/><path d="{Lx["handset"]}" fill="{GREEN}"/>'
                f'<path d="{Lx["small"]}" fill="{GOLD}" stroke="{GREEN}" stroke-width="{Lx["small_stroke"]:.3f}" stroke-linejoin="round"/>')

    (prev / "adaptive_circle.svg").write_text(svg(108,
        f'<defs><clipPath id="c"><circle cx="54" cy="54" r="36"/></clipPath></defs>'
        f'<g clip-path="url(#c)"><rect width="108" height="108" fill="{GREEN}"/>{colored(L)}</g>'
        f'<circle cx="54" cy="54" r="33" fill="none" stroke="#ff00ff" stroke-width="0.4" stroke-dasharray="2 2"/>'))
    (prev / "adaptive_squircle.svg").write_text(svg(108,
        f'<defs><clipPath id="c"><rect x="18" y="18" width="72" height="72" rx="20"/></clipPath></defs>'
        f'<g clip-path="url(#c)"><rect width="108" height="108" fill="{GREEN}"/>{colored(L)}</g>'))
    (prev / "themed.svg").write_text(svg(108,
        f'<defs><clipPath id="c"><circle cx="54" cy="54" r="36"/></clipPath></defs>'
        f'<g clip-path="url(#c)"><rect width="108" height="108" fill="#DCE8DD"/>'
        f'<path d="{L["heart"]} {L["handset"]} {L["small_gap"]}" fill="#1E4D33" fill-rule="evenodd"/>'
        f'<path d="{L["small"]}" fill="#1E4D33"/></g>'))
    (prev / "legacy.svg").write_text(svg(48,
        f'<circle cx="24" cy="24" r="23" fill="{GREEN}"/>{colored(L48)}'))

    # 5) Play Store simgesi: 512x512 tam dolu kare (köşe yuvarlamasını Play kendisi uygular)
    G512 = affine(0, 0, 0, 512 / 150)
    L512 = layers(G512)
    (prev / "play_icon_512.svg").write_text(svg(512,
        f'<rect width="512" height="512" fill="{GREEN}"/>{colored(L512)}'))
    print("ok")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
