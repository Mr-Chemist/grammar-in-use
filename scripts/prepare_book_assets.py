#!/usr/bin/env python3
"""
Master Asset Builder for Grammar in Use (Native Kotlin + 30-Unit Combined ArvanCloud ZIP Packs)

Surgical Footer Cleanup:
- Uses exact PDF bounding-box coordinates of the bottom website URL and extends ONLY
  slightly to the left (covering the tiny square green M-book logo right next to the URL),
  without ever touching the printed page number or the blue reference bar above it!

Usage:
    python scripts/prepare_book_assets.py --pdf "E:\\EngData\\Intermediate_Grammar_in_Use.pdf" --audio "E:\\EngData\\Audio CD"
"""

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path
from typing import Dict, List, Tuple

try:
    import pypdfium2 as pdfium
except ImportError:
    pdfium = None

try:
    import pdfplumber
except ImportError:
    pdfplumber = None

try:
    from PIL import Image, ImageDraw
except ImportError:
    Image = None
    ImageDraw = None


START_PDF_PAGE = 14
TOTAL_UNITS = 145
APPENDICES_RANGE = (304, 313)
ADDITIONAL_RANGE = (314, 337)
STUDY_GUIDE_RANGE = (338, 347)
KEY_EXERCISES_RANGE = (348, 379)
KEY_STUDY_GUIDE_PDF = 384

PACK_RANGES = [
    ("pack_1", 1, 30),
    ("pack_2", 31, 60),
    ("pack_3", 61, 90),
    ("pack_4", 91, 120),
    ("pack_5", 121, 145),
]

URL_WORD_PATTERN = re.compile(
    r"(https?://|www\.|\.(com|ir|org|net|edu|uk|info|me)\b|t\.me/|@\w+)",
    re.IGNORECASE,
)


def find_unit_audio_files(audio_root: Path, unit_num: int) -> Dict[str, Path]:
    candidates = [
        audio_root / f"Unit {unit_num}",
        audio_root / f"Unit{unit_num}",
        audio_root / f"unit {unit_num}",
        audio_root / f"unit_{unit_num:03d}",
    ]
    unit_dir = next((p for p in candidates if p.is_dir()), None)
    if unit_dir is None and audio_root.is_dir():
        pat = re.compile(rf"^unit\s*_?0*{unit_num}$", re.IGNORECASE)
        for child in audio_root.iterdir():
            if child.is_dir() and pat.match(child.name.strip()):
                unit_dir = child
                break
    result: Dict[str, Path] = {}
    if unit_dir is None:
        return result
    for f in sorted(unit_dir.iterdir()):
        if f.is_file() and f.suffix.lower() == ".mp3":
            m = re.match(r"^([A-H])(?:\b|_|\s|$)", f.stem.strip().upper())
            if m:
                result[m.group(1)] = f
    return result


def find_footer_url_and_logo_boxes(plumber_page, logo_left_pad_pt: float) -> List[Tuple[float, float, float, float]]:
    """
    Locate exact normalized (x0, y0, x1, y1) box of the bottom website URL and extend
    ONLY to its left by logo_left_pad_pt to erase the small square logo beside it,
    while keeping page numbers (corners) and the blue reference bar above untouched.
    """
    w = float(plumber_page.width)
    h = float(plumber_page.height)
    words = plumber_page.extract_words()
    boxes: List[Tuple[float, float, float, float]] = []

    for wd in words:
        top = float(wd["top"])
        txt = wd["text"].strip()
        if top >= h * 0.92 and not txt.isdigit() and URL_WORD_PATTERN.search(txt):
            # Extend left by logo_left_pad_pt to cover the small square logo, never below x=14%
            x0 = max(0.14, (float(wd["x0"]) - logo_left_pad_pt) / w)
            # Extend slightly up/down ONLY around the URL/logo height (never reaching the blue bar)
            y0 = max(0.955, (top - 5.0) / h)
            x1 = min(0.88, (float(wd["x1"]) + 6.0) / w)
            y1 = min(1.0, (float(wd["bottom"]) + 5.0) / h)
            boxes.append((x0, y0, x1, y1))

    return boxes


def render_page_clean(
    pdf_doc,
    plumber_page,
    page_idx_zero: int,
    out_path: Path,
    target_width: int,
    quality: int,
    logo_left_pad_pt: float,
) -> float:
    page = pdf_doc[page_idx_zero]
    w_pt, _ = page.get_size()
    scale = target_width / float(w_pt)
    pil_img = page.render(scale=scale).to_pil().convert("RGB")
    w_px, h_px = pil_img.size
    draw = ImageDraw.Draw(pil_img)

    boxes = find_footer_url_and_logo_boxes(plumber_page, logo_left_pad_pt)
    if boxes:
        for x0, y0, x1, y1 in boxes:
            draw.rectangle(
                [int(x0 * w_px), int(y0 * h_px), int(x1 * w_px), int(y1 * h_px)],
                fill=(255, 255, 255),
            )
    else:
        # Ultra-narrow center-only fallback (bottom 2.4%, x=35%..72%) if URL is rasterized
        draw.rectangle(
            [int(w_px * 0.35), int(h_px * 0.976), int(w_px * 0.72), h_px],
            fill=(255, 255, 255),
        )

    out_path.parent.mkdir(parents=True, exist_ok=True)
    pil_img.save(out_path, format="JPEG", quality=quality, optimize=True)
    return round(w_px / float(h_px), 4)


def extract_lesson_info(plumber_page, expected_labels: List[str]) -> Tuple[str, List[Tuple[str, float, float]]]:
    page_w = float(plumber_page.width)
    page_h = float(plumber_page.height)
    words = plumber_page.extract_words(extra_attrs=["size"])

    header_words = [
        w
        for w in words
        if (0.04 * page_h) <= float(w["top"]) <= (0.135 * page_h)
        and float(w["x0"]) >= (0.14 * page_w)
        and float(w["size"]) >= 11.5
    ]
    header_words.sort(key=lambda w: (round(float(w["top"]) / 6), float(w["x0"])))
    title = re.sub(r"\s+", " ", " ".join(w["text"] for w in header_words)).strip() or "Grammar Lesson"

    allowed = set(expected_labels) if expected_labels else {"A", "B", "C", "D", "E", "F"}
    candidates = [
        (w["text"].strip(), float(w["top"]) / page_h)
        for w in words
        if w["text"].strip() in allowed
        and float(w["x0"]) <= (0.16 * page_w)
        and (0.11 * page_h) <= float(w["top"]) <= (0.91 * page_h)
        and float(w["size"]) >= 10.5
    ]
    candidates.sort(key=lambda item: item[1])
    ordered_labels = sorted(allowed)
    found: Dict[str, float] = {}
    last_y = 0.0
    for lbl in ordered_labels:
        for t, y_pct in candidates:
            if t == lbl and y_pct > last_y + 0.03:
                found[lbl] = max(0.11, y_pct - 0.012)
                last_y = y_pct
                break

    final_labels = ordered_labels if expected_labels else sorted(found.keys()) or ["A", "B", "C"]
    body_top, body_bottom = 0.13, 0.915
    y_positions = [
        found[lbl] if lbl in found else body_top + (idx / max(1, len(final_labels))) * (body_bottom - body_top)
        for idx, lbl in enumerate(final_labels)
    ]
    for i in range(1, len(y_positions)):
        if y_positions[i] <= y_positions[i - 1] + 0.05:
            y_positions[i] = min(body_bottom - 0.04, y_positions[i - 1] + 0.08)

    bands = []
    for i, lbl in enumerate(final_labels):
        top_pct = round(y_positions[i], 4)
        next_top = y_positions[i + 1] if i + 1 < len(y_positions) else body_bottom
        bands.append((lbl, top_pct, round(max(0.05, next_top - top_pct), 4)))
    return title, bands


def detect_column_bounds(plumber_page) -> List[Tuple[float, float]]:
    w = float(plumber_page.width)
    h = float(plumber_page.height)
    words = [wd for wd in plumber_page.extract_words() if (0.06 * h) <= float(wd["top"]) <= (0.93 * h)]
    if not words:
        return [(0.0, w)]

    bins = [0] * 100
    for wd in words:
        b0 = max(0, min(99, int((float(wd["x0"]) / w) * 100)))
        b1 = max(0, min(99, int((float(wd["x1"]) / w) * 100)))
        for b in range(b0, b1 + 1):
            bins[b] += 1

    if min(bins[29:38]) <= 2 and min(bins[62:71]) <= 2:
        c1 = (29 + bins[29:38].index(min(bins[29:38]))) / 100.0 * w
        c2 = (62 + bins[62:71].index(min(bins[62:71]))) / 100.0 * w
        return [(0.0, c1), (c1, c2), (c2, w)]

    if min(bins[44:56]) <= 3:
        c = (44 + bins[44:56].index(min(bins[44:56]))) / 100.0 * w
        return [(0.0, c), (c, w)]

    return [(0.0, w)]


def extract_all_answer_keys(plumber_pdf) -> Dict[int, str]:
    lines: List[str] = []
    total_pages = len(plumber_pdf.pages)
    for pno in range(KEY_EXERCISES_RANGE[0], min(KEY_EXERCISES_RANGE[1], total_pages) + 1):
        page = plumber_pdf.pages[pno - 1]
        h = float(page.height)
        for x0, x1 in detect_column_bounds(page):
            col_txt = page.crop((x0, h * 0.05, x1, h * 0.94)).extract_text() or ""
            for ln in col_txt.splitlines():
                clean = re.sub(r"https?://\S+|www\.\S+", "", ln, flags=re.IGNORECASE).strip()
                if clean and not clean.lower().startswith("key to exercises"):
                    lines.append(clean)

    joined = "\n".join(lines)
    parts = re.split(r"(?im)^\s*UNIT\s+(\d+)\b", joined)
    keys: Dict[int, str] = {}
    for i in range(1, len(parts) - 1, 2):
        u_num = int(parts[i])
        body = parts[i + 1].strip()
        if 1 <= u_num <= 145:
            if u_num not in keys or len(body) > len(keys[u_num]):
                keys[u_num] = body
    return keys


def extract_study_guide_mcqs(plumber_pdf) -> List[dict]:
    key_map: Dict[str, List[str]] = {}
    if KEY_STUDY_GUIDE_PDF <= len(plumber_pdf.pages):
        key_txt = plumber_pdf.pages[KEY_STUDY_GUIDE_PDF - 1].extract_text() or ""
        for m in re.finditer(r"\b(\d+\.\d+)\s+([A-E](?:\s*,\s*[A-E])*)", key_txt):
            key_map[m.group(1)] = [c.strip() for c in m.group(2).split(",") if c.strip()]

    sg_lines: List[str] = []
    for pno in range(STUDY_GUIDE_RANGE[0], min(STUDY_GUIDE_RANGE[1], len(plumber_pdf.pages)) + 1):
        page = plumber_pdf.pages[pno - 1]
        h = float(page.height)
        sg_lines.extend((page.crop((0, h * 0.05, float(page.width), h * 0.94)).extract_text() or "").splitlines())

    q_blocks = re.split(r"(?m)^\s*(\d+\.\d+)\s+", "\n".join(sg_lines))
    mcqs: List[dict] = []
    for i in range(1, len(q_blocks) - 1, 2):
        qid = q_blocks[i].strip()
        block = q_blocks[i + 1].strip()
        correct_keys = key_map.get(qid, [])
        if not correct_keys:
            continue

        unit_nums = [int(n) for n in re.findall(r"\b(?:Unit|Units?)\s+(\d+)", block) if 1 <= int(n) <= 145]
        if not unit_nums:
            sec_num = int(qid.split(".")[0])
            start_u = max(1, (sec_num - 1) * 9 + 1)
            unit_nums = list(range(start_u, min(145, start_u + 8) + 1))

        opt_matches = list(re.finditer(r"(?:^|\s)([A-E])\s+([^\n]+?)(?=(?:\s+[A-E]\s+|\n|$))", block))
        if len(opt_matches) < 2:
            continue
        prompt = re.sub(r"\s+", " ", block[: opt_matches[0].start()]).strip()
        if len(prompt) < 6:
            continue

        options = []
        seen = set()
        for om in opt_matches:
            k, t = om.group(1), re.sub(r"\s+", " ", om.group(2)).strip()
            if k not in seen and t:
                seen.add(k)
                options.append({"key": k, "text": t})

        if len(options) >= 2:
            mcqs.append({
                "id": f"sg_{qid.replace('.', '_')}",
                "unitNumbers": sorted(set(unit_nums)),
                "prompt": prompt,
                "options": options,
                "correctKeys": correct_keys,
            })
    return mcqs


def build_written_questions(answer_keys: Dict[int, str]) -> List[dict]:
    written: List[dict] = []
    for u_num in range(1, TOTAL_UNITS + 1):
        key_body = answer_keys.get(u_num, "")
        if not key_body:
            continue
        current_ex = f"{u_num}.1"
        count = 0
        for raw_ln in key_body.splitlines():
            ln = raw_ln.strip()
            ex_hdr = re.match(rf"^({u_num}\.\d+)\b", ln)
            if ex_hdr:
                current_ex = ex_hdr.group(1)
                continue
            item_m = re.match(r"^(\d{1,2})\s+(.+)$", ln)
            if item_m and count < 8:
                q_num, ans_raw = item_m.group(1), item_m.group(2).strip()
                if len(ans_raw) < 2 or len(ans_raw) > 95:
                    continue
                alts = [a.strip() for a in re.split(r"\s+or\s+|\s*/\s*", ans_raw) if a.strip()]
                if alts:
                    written.append({
                        "id": f"wr_u{u_num}_{current_ex.replace('.', '_')}_{q_num}",
                        "unitNumbers": [u_num],
                        "sourceLabel": f"Unit {u_num} · تمرین {current_ex} · شماره {q_num}",
                        "prompt": f"پاسخ شماره {q_num} از تمرین {current_ex} در صفحه تمرین یونیت {u_num} را تایپ کنید:",
                        "acceptedAnswers": alts[:4],
                    })
                    count += 1
    return written


def write_zip(entries: List[Tuple[Path, str]], out_zip: Path):
    out_zip.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_STORED) as zf:
        for fpath, arc in entries:
            zf.write(fpath, arc)


def main() -> int:
    parser = argparse.ArgumentParser(description="Build 30-unit combined ArvanCloud ZIP packs + Kotlin JSON assets.")
    parser.add_argument("--pdf", required=True, help="Path to Intermediate_Grammar_in_Use.pdf")
    parser.add_argument("--audio", required=True, help="Path to extracted Audio CD folder")
    parser.add_argument("--width", type=int, default=900)
    parser.add_argument("--quality", type=int, default=74)
    parser.add_argument("--logo-pad", type=float, default=42.0, help="Extra points to the left of URL to erase the square logo (default: 42)")
    args = parser.parse_args()

    if pdfium is None or pdfplumber is None or Image is None:
        print("[ERROR] Missing packages. Run: pip install -r scripts/requirements.txt")
        return 1

    pdf_path = Path(args.pdf).resolve()
    audio_root = Path(args.audio).resolve()
    project_root = Path(__file__).resolve().parent.parent

    if not pdf_path.is_file():
        print(f"[ERROR] PDF not found: {pdf_path}")
        return 1
    if not audio_root.is_dir():
        print(f"[ERROR] Audio folder not found: {audio_root}")
        return 1

    packs_root = project_root / "arvan_packs"
    packs_out = packs_root / "packs"
    tmp_pages = packs_root / "_tmp_pages"
    assets_dir = project_root / "app" / "src" / "main" / "assets"
    packs_out.mkdir(parents=True, exist_ok=True)
    tmp_pages.mkdir(parents=True, exist_ok=True)
    assets_dir.mkdir(parents=True, exist_ok=True)

    print(f"[INFO] Opening PDF: {pdf_path.name}")
    pdf_doc = pdfium.PdfDocument(str(pdf_path))

    book_pages = []
    unit_summaries = []
    pack_entries: Dict[str, List[Tuple[Path, str]]] = {name: [] for name, _, _ in PACK_RANGES}
    resource_entries: List[Tuple[Path, str]] = []

    with pdfplumber.open(str(pdf_path)) as plumber_pdf:
        print("[INFO] Extracting 1/2/3-column Answer Keys...")
        answer_keys = extract_all_answer_keys(plumber_pdf)
        print(f"[INFO] Extracted Answer Keys for {len(answer_keys)}/145 units.")

        print("[INFO] Extracting interactive MCQ & Written quizzes...")
        mcqs = extract_study_guide_mcqs(plumber_pdf)
        writtens = build_written_questions(answer_keys)
        print(f"[INFO] Quiz bank ready: {len(mcqs)} MCQs + {len(writtens)} Written items.")

        for unit_num in range(1, TOTAL_UNITS + 1):
            lesson_pdf_idx = (START_PDF_PAGE - 1) + (unit_num - 1) * 2
            exercise_pdf_idx = lesson_pdf_idx + 1
            lesson_book_page = unit_num * 2
            exercise_book_page = lesson_book_page + 1

            u_tag = f"u{unit_num:03d}"
            lesson_rel = f"pages/{u_tag}_lesson.jpg"
            exercise_rel = f"pages/{u_tag}_exercise.jpg"
            lesson_out = tmp_pages / f"{u_tag}_lesson.jpg"
            exercise_out = tmp_pages / f"{u_tag}_exercise.jpg"

            lesson_plumber = plumber_pdf.pages[lesson_pdf_idx]
            exercise_plumber = plumber_pdf.pages[exercise_pdf_idx]

            lesson_ratio = render_page_clean(pdf_doc, lesson_plumber, lesson_pdf_idx, lesson_out, args.width, args.quality, args.logo_pad)
            exercise_ratio = render_page_clean(pdf_doc, exercise_plumber, exercise_pdf_idx, exercise_out, args.width, args.quality, args.logo_pad)

            if unit_num == 4:
                (packs_root / "preview_u004_lesson.jpg").write_bytes(lesson_out.read_bytes())

            pack_name = next(name for name, s, e in PACK_RANGES if s <= unit_num <= e)
            pack_entries[pack_name].append((lesson_out, lesson_rel))
            pack_entries[pack_name].append((exercise_out, exercise_rel))

            audio_files = find_unit_audio_files(audio_root, unit_num)
            expected_labels = sorted(audio_files.keys())
            title, bands = extract_lesson_info(lesson_plumber, expected_labels)

            sections_payload = []
            for label, top_pct, height_pct in bands:
                audio_rel = None
                if label in audio_files:
                    audio_rel = f"audio/unit_{unit_num:03d}/{label}.mp3"
                    pack_entries[pack_name].append((audio_files[label], audio_rel))
                sections_payload.append({"label": label, "audioPath": audio_rel, "topPct": top_pct, "heightPct": height_pct})

            lesson_idx = len(book_pages)
            book_pages.append({
                "id": f"{u_tag}_lesson",
                "unitNumber": unit_num,
                "unitTitle": title,
                "bookPage": lesson_book_page,
                "kind": "lesson",
                "imagePath": lesson_rel,
                "aspectRatio": lesson_ratio,
                "sections": sections_payload,
            })

            exercise_idx = len(book_pages)
            book_pages.append({
                "id": f"{u_tag}_exercise",
                "unitNumber": unit_num,
                "unitTitle": title,
                "bookPage": exercise_book_page,
                "kind": "exercise",
                "imagePath": exercise_rel,
                "aspectRatio": exercise_ratio,
                "sections": [],
            })

            unit_summaries.append({
                "unitNumber": unit_num,
                "title": title,
                "lessonPageId": f"{u_tag}_lesson",
                "exercisePageId": f"{u_tag}_exercise",
                "lessonBookPage": lesson_book_page,
                "exerciseBookPage": exercise_book_page,
                "lessonPageIndex": lesson_idx,
                "exercisePageIndex": exercise_idx,
                "answerKey": answer_keys.get(unit_num, ""),
            })

            if unit_num % 30 == 0 or unit_num == TOTAL_UNITS:
                print(f"[PROGRESS] Units {unit_num}/{TOTAL_UNITS} processed...")

        # Back-matter resources -> pack_resources.zip
        resources = []
        specs = [
            ("appendices", "appendix", "ضمائم کتاب (Appendices 1–7)", "افعال بی‌قاعده، زمان‌ها، شکل کوتاه، املا و انگلیسی آمریکایی", APPENDICES_RANGE, 292),
            ("additional", "additional", "تمرین‌های تکمیلی (Additional Exercises)", "تمرین‌های ترکیبی مرور چند یونیت هم‌زمان", ADDITIONAL_RANGE, 302),
            ("study_guide", "study_guide", "راهنمای مطالعه و تعیین سطح (Study Guide)", "آزمون تعیین سطح برای تشخیص یونیت‌های نیازمند مرور", STUDY_GUIDE_RANGE, 326),
        ]
        for res_id, kind, title_fa, sub_fa, (ps, pe), bp_start in specs:
            start_idx = len(book_pages)
            count = 0
            for offset, pdf_pno in enumerate(range(ps, min(pe, len(pdf_doc)) + 1)):
                rel = f"pages/{res_id}_{pdf_pno}.jpg"
                out_jpg = tmp_pages / f"{res_id}_{pdf_pno}.jpg"
                ratio = render_page_clean(
                    pdf_doc, plumber_pdf.pages[pdf_pno - 1], pdf_pno - 1, out_jpg, args.width, args.quality, args.logo_pad
                )
                resource_entries.append((out_jpg, rel))
                book_pages.append({
                    "id": f"{res_id}_{pdf_pno}",
                    "unitNumber": 0,
                    "unitTitle": title_fa,
                    "bookPage": bp_start + offset,
                    "kind": kind,
                    "imagePath": rel,
                    "aspectRatio": ratio,
                    "sections": [],
                })
                count += 1
            resources.append({
                "id": res_id,
                "category": kind,
                "title": title_fa,
                "subtitle": sub_fa,
                "startPageIndex": start_idx,
                "pageCount": count,
            })

    for pack_name, _, _ in PACK_RANGES:
        zpath = packs_out / f"{pack_name}.zip"
        write_zip(pack_entries[pack_name], zpath)
        print(f"[OK] {zpath.name}: {len(pack_entries[pack_name])} files ({zpath.stat().st_size / (1024 * 1024):.1f} MB)")

    res_zip = packs_out / "pack_resources.zip"
    write_zip(resource_entries, res_zip)
    print(f"[OK] {res_zip.name}: {len(resource_entries)} files ({res_zip.stat().st_size / (1024 * 1024):.1f} MB)")

    (assets_dir / "book_data.json").write_text(
        json.dumps({"pages": book_pages, "units": unit_summaries, "resources": resources}, ensure_ascii=False),
        encoding="utf-8",
    )
    (assets_dir / "quiz_data.json").write_text(
        json.dumps({"mcqs": mcqs, "writtens": writtens}, ensure_ascii=False),
        encoding="utf-8",
    )

    print("\n[DONE] Ready for ArvanCloud & GitHub Actions!")
    print(f"  - Inspect clean footer preview : {packs_root / 'preview_u004_lesson.jpg'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())