#!/usr/bin/env python3
"""
Master Asset Builder v2 for English Grammar In Use (Native Kotlin + PDF-Native Packs)

Produces:
1. arvan_packs/packs/pack_1.zip .. pack_5.zip
   - Lossless sliced PDFs (pages/pack_1.pdf .. pack_5.pdf) for crisp high-DPI zooming via Android PdfRenderer
   - High-DPI cropped Cambridge Answer Key images (keys/u001_key.png .. u145_key.png) preserving exact typography
   - Original Audio CD MP3s (audio/unit_001/A.mp3, ...)
2. arvan_packs/packs/pack_resources.zip
   - Lossless sliced PDF (pages/pack_resources.pdf) for Appendices 1-7, Additional Exercises, and Study Guide
3. app/src/main/assets/book_data.json & app/src/main/assets/quiz_data.json
   - Full unit metadata, section hotspots, Answer Key image paths + text fallbacks
   - Study Guide MCQs with vector blank restoration (________)
   - Written fill-in-the-blank exercises extracted directly from Exercise pages with smart answer keys

Usage:
    python scripts/prepare_book_assets.py --pdf "E:\\EngData\\MUCLecture_2022_5217521.pdf" --audio "E:\\EngData\\Audio CD"
"""

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path
from typing import Dict, List, Optional, Tuple

try:
    import pypdfium2 as pdfium
except ImportError:
    pdfium = None

try:
    from pypdf import PdfReader, PdfWriter
except ImportError:
    PdfReader = None
    PdfWriter = None

try:
    import pdfplumber
except ImportError:
    pdfplumber = None

try:
    from PIL import Image
except ImportError:
    Image = None


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


def slice_pdf_pages(reader: "PdfReader", zero_based_indices: List[int], out_pdf: Path):
    writer = PdfWriter()
    total = len(reader.pages)
    for idx in zero_based_indices:
        if 0 <= idx < total:
            writer.add_page(reader.pages[idx])
    out_pdf.parent.mkdir(parents=True, exist_ok=True)
    with open(out_pdf, "wb") as f:
        writer.write(f)


def extract_lesson_info(plumber_page, expected_labels: List[str]) -> Tuple[str, List[Tuple[str, float, float]], float]:
    page_w = float(plumber_page.width)
    page_h = float(plumber_page.height)
    aspect_ratio = round(page_w / page_h, 4) if page_h > 0 else 0.7386
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
    return title, bands, aspect_ratio


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


def reconstruct_lines_with_blanks(plumber_page, y_min_pct: float = 0.05, y_max_pct: float = 0.94) -> List[str]:
    """
    Reconstructs text lines while detecting PDF vector blanks (dotted lines, underlines,
    or wide horizontal gaps > 16pt between words) and inserting '________'.
    """
    w = float(plumber_page.width)
    h = float(plumber_page.height)
    y_min = h * y_min_pct
    y_max = h * y_max_pct

    words = [
        wd for wd in plumber_page.extract_words(keep_blank_chars=False, use_text_flow=False)
        if y_min <= float(wd["top"]) <= y_max
    ]
    if not words:
        return []

    # Collect horizontal vector segments (lines / thin rects / dotted curves) that represent blanks
    blank_segments: List[Tuple[float, float, float]] = []  # (y_mid, x0, x1)
    for ln in getattr(plumber_page, "lines", []) or []:
        lx0, lx1 = float(ln["x0"]), float(ln["x1"])
        ltop, lbot = float(ln["top"]), float(ln["bottom"])
        if abs(lbot - ltop) <= 3.5 and (lx1 - lx0) >= 16.0 and y_min <= ltop <= y_max:
            blank_segments.append(((ltop + lbot) / 2.0, lx0, lx1))
    for rc in getattr(plumber_page, "rects", []) or []:
        rx0, rx1 = float(rc["x0"]), float(rc["x1"])
        rtop, rbot = float(rc["top"]), float(rc["bottom"])
        if abs(rbot - rtop) <= 3.5 and (rx1 - rx0) >= 16.0 and y_min <= rtop <= y_max:
            blank_segments.append(((rtop + rbot) / 2.0, rx0, rx1))

    # Group words into visual lines by vertical proximity
    words.sort(key=lambda wd: (round(float(wd["top"]) / 3.0), float(wd["x0"])))
    line_groups: List[List[dict]] = []
    for wd in words:
        if not line_groups:
            line_groups.append([wd])
            continue
        last_line = line_groups[-1]
        avg_top = sum(float(x["top"]) for x in last_line) / len(last_line)
        if abs(float(wd["top"]) - avg_top) <= 5.0:
            last_line.append(wd)
        else:
            line_groups.append([wd])

    out_lines: List[str] = []
    for grp in line_groups:
        grp.sort(key=lambda wd: float(wd["x0"]))
        line_top = min(float(wd["top"]) for wd in grp)
        line_bot = max(float(wd["bottom"]) for wd in grp)
        line_blanks = [
            (bx0, bx1) for (by, bx0, bx1) in blank_segments
            if (line_top - 4.0) <= by <= (line_bot + 6.0)
        ]

        tokens: List[str] = []
        # Check if a blank precedes the first word (after item number)
        for idx, wd in enumerate(grp):
            txt = wd["text"].strip()
            # Replace sequences of 3+ dots or underscores with canonical ________
            txt = re.sub(r"[._…]{3,}", "________", txt)
            if idx > 0:
                prev = grp[idx - 1]
                gap = float(wd["x0"]) - float(prev["x1"])
                has_vec_blank = any(
                    bx0 >= float(prev["x1"]) - 6.0 and bx1 <= float(wd["x0"]) + 6.0
                    for (bx0, bx1) in line_blanks
                )
                # Avoid treating left-margin question number tab or right-column option spacing as a blank
                prev_is_qnum = bool(re.match(r"^\d+(\.\d+)?\.?$", prev["text"].strip()))
                curr_is_opt = bool(re.match(r"^[A-E]$", txt))
                if (has_vec_blank or (gap >= 22.0 and not prev_is_qnum and not curr_is_opt)):
                    if not tokens or tokens[-1] != "________":
                        tokens.append("________")
            tokens.append(txt)

        # Check if a vector blank sits after the last word on the line
        last_x1 = float(grp[-1]["x1"])
        if any(bx0 >= last_x1 - 4.0 and (bx1 - bx0) >= 22.0 for (bx0, bx1) in line_blanks):
            if not tokens or tokens[-1] != "________":
                tokens.append("________")

        line_str = re.sub(r"\s+", " ", " ".join(tokens)).strip()
        line_str = re.sub(r"(________\s*){2,}", "________ ", line_str).strip()
        if line_str:
            out_lines.append(line_str)
    return out_lines


def extract_all_answer_keys(plumber_pdf) -> Dict[int, str]:
    lines: List[str] = []
    total_pages = len(plumber_pdf.pages)
    for pno in range(KEY_EXERCISES_RANGE[0], min(KEY_EXERCISES_RANGE[1], total_pages) + 1):
        page = plumber_pdf.pages[pno - 1]
        h = float(page.height)
        for x0, x1 in detect_column_bounds(page):
            col_txt = page.crop((x0, h * 0.055, x1, h * 0.945)).extract_text() or ""
            for ln in col_txt.splitlines():
                clean = re.sub(r"https?://\S+|www\.\S+|facebook\.com/\S+", "", ln, flags=re.IGNORECASE).strip()
                if clean and not clean.lower().startswith("key to exercises"):
                    lines.append(clean)

    joined = "\n".join(lines)
    parts = re.split(r"(?im)^\s*UNIT\s+(\d+)\b", joined)
    keys: Dict[int, str] = {}
    for i in range(1, len(parts) - 1, 2):
        u_num = int(parts[i])
        body = parts[i + 1].strip()
        if 1 <= u_num <= TOTAL_UNITS:
            if u_num not in keys or len(body) > len(keys[u_num]):
                keys[u_num] = body
    return keys


def crop_answer_key_images(plumber_pdf, pdf_doc, tmp_keys_dir: Path, dpi: int = 210) -> Dict[int, Path]:
    """
    Crops each unit's exact block ('UNIT 1' .. 'UNIT 145') from Key to Exercises pages
    at crisp high DPI, automatically stitching blocks that span two columns or pages.
    Preserves Cambridge's exact typography: bold, italic, numbering, and indentation.
    """
    tmp_keys_dir.mkdir(parents=True, exist_ok=True)
    total_pages = len(plumber_pdf.pages)
    scale = dpi / 72.0

    # 1. Build ordered stream of columns across all Key to Exercises pages
    col_stream: List[Tuple[int, float, float, float, float]] = []  # (pno_0, x0, x1, y_top, y_bot)
    anchors: List[Tuple[int, int, float]] = []  # (unit_num, col_stream_idx, y_top_pt)

    for pno in range(KEY_EXERCISES_RANGE[0], min(KEY_EXERCISES_RANGE[1], total_pages) + 1):
        p_idx = pno - 1
        page = plumber_pdf.pages[p_idx]
        h = float(page.height)
        y_min, y_max = h * 0.055, h * 0.945
        cols = detect_column_bounds(page)
        words = page.extract_words()

        for x0, x1 in cols:
            stream_idx = len(col_stream)
            col_stream.append((p_idx, x0, x1, y_min, y_max))
            # Find 'UNIT' followed by a number in this column
            col_words = [
                wd for wd in words
                if float(wd["x0"]) >= x0 - 2 and float(wd["x1"]) <= x1 + 2
                and y_min <= float(wd["top"]) <= y_max
            ]
            col_words.sort(key=lambda wd: (round(float(wd["top"]) / 3.0), float(wd["x0"])))
            for i in range(len(col_words) - 1):
                w1 = col_words[i]["text"].strip().upper()
                w2 = col_words[i + 1]["text"].strip()
                if w1 == "UNIT" and re.match(r"^\d{1,3}$", w2):
                    if abs(float(col_words[i]["top"]) - float(col_words[i + 1]["top"])) <= 4.0:
                        u_num = int(w2)
                        if 1 <= u_num <= TOTAL_UNITS:
                            anchors.append((u_num, stream_idx, max(y_min, float(col_words[i]["top"]) - 4.0)))

    if not anchors:
        return {}

    # Deduplicate keeping first occurrence in reading order
    seen_units = set()
    ordered_anchors: List[Tuple[int, int, float]] = []
    for u_num, s_idx, y_pt in anchors:
        if u_num not in seen_units:
            seen_units.add(u_num)
            ordered_anchors.append((u_num, s_idx, y_pt))

    # Cache rendered PIL pages as we go
    rendered_pages: Dict[int, "Image.Image"] = {}

    def get_rendered_page(p_idx: int) -> "Image.Image":
        if p_idx not in rendered_pages:
            # Keep memory lean: cache at most 3 pages at once
            if len(rendered_pages) >= 3:
                rendered_pages.clear()
            rendered_pages[p_idx] = pdf_doc[p_idx].render(scale=scale).to_pil().convert("RGB")
        return rendered_pages[p_idx]

    key_images: Dict[int, Path] = {}
    for idx, (u_num, start_s, start_y) in enumerate(ordered_anchors):
        if idx + 1 < len(ordered_anchors):
            _, end_s, end_y = ordered_anchors[idx + 1]
        else:
            end_s = len(col_stream) - 1
            end_y = col_stream[end_s][4]

        slices: List["Image.Image"] = []
        for s_idx in range(start_s, min(end_s + 1, start_s + 3)):
            p_idx, cx0, cx1, cy_min, cy_max = col_stream[s_idx]
            y0 = start_y if s_idx == start_s else cy_min
            y1 = (end_y - 4.0) if s_idx == end_s else cy_max
            if y1 - y0 < 12.0:
                continue
            img = get_rendered_page(p_idx)
            px0 = max(0, int(cx0 * scale) - 6)
            px1 = min(img.width, int(cx1 * scale) + 6)
            py0 = max(0, int(y0 * scale))
            py1 = min(img.height, int(y1 * scale))
            if px1 > px0 + 20 and py1 > py0 + 20:
                crop = img.crop((px0, py0, px1, py1))
                # Trim excessive trailing white space at bottom of slice
                gray = crop.convert("L")
                bbox = gray.point(lambda p: 0 if p > 245 else 255).getbbox()
                if bbox:
                    pad = 14
                    crop = crop.crop((
                        max(0, bbox[0] - pad),
                        max(0, bbox[1] - pad),
                        min(crop.width, bbox[2] + pad),
                        min(crop.height, bbox[3] + pad),
                    ))
                    slices.append(crop)

        if not slices:
            continue

        max_w = max(im.width for im in slices)
        total_h = sum(im.height for im in slices) + (len(slices) - 1) * 16 + 24
        canvas = Image.new("RGB", (max_w + 24, total_h), (255, 255, 255))
        cur_y = 12
        for im in slices:
            canvas.paste(im, (12, cur_y))
            cur_y += im.height + 16

        out_png = tmp_keys_dir / f"u{u_num:03d}_key.png"
        canvas.save(out_png, format="PNG", optimize=True)
        key_images[u_num] = out_png

    return key_images


def extract_study_guide_mcqs(plumber_pdf) -> List[dict]:
    key_map: Dict[str, List[str]] = {}
    if KEY_STUDY_GUIDE_PDF <= len(plumber_pdf.pages):
        key_txt = plumber_pdf.pages[KEY_STUDY_GUIDE_PDF - 1].extract_text() or ""
        for m in re.finditer(r"\b(\d+\.\d+)\s+([A-E](?:\s*,\s*[A-E])*)", key_txt):
            key_map[m.group(1)] = [c.strip() for c in m.group(2).split(",") if c.strip()]

    sg_lines: List[str] = []
    for pno in range(STUDY_GUIDE_RANGE[0], min(STUDY_GUIDE_RANGE[1], len(plumber_pdf.pages)) + 1):
        page = plumber_pdf.pages[pno - 1]
        sg_lines.extend(reconstruct_lines_with_blanks(page, 0.055, 0.94))

    q_blocks = re.split(r"(?m)^\s*(\d+\.\d+)\s+", "\n".join(sg_lines))
    mcqs: List[dict] = []
    for i in range(1, len(q_blocks) - 1, 2):
        qid = q_blocks[i].strip()
        block = q_blocks[i + 1].strip()
        correct_keys = key_map.get(qid, [])
        if not correct_keys:
            continue

        unit_nums = [int(n) for n in re.findall(r"\b(?:Unit|Units?)\s+(\d+)", block) if 1 <= int(n) <= TOTAL_UNITS]
        if not unit_nums:
            sec_num = int(qid.split(".")[0])
            start_u = max(1, (sec_num - 1) * 9 + 1)
            unit_nums = list(range(start_u, min(TOTAL_UNITS, start_u + 8) + 1))

        # Strip "IF YOU ARE NOT SURE..." or trailing unit hints
        clean_block = re.sub(r"\b(?:Units?\s+\d+(?:\s*[-–,]\s*\d+)*)\b", "", block).strip()
        opt_matches = list(re.finditer(r"(?:^|\s)([A-E])\s+([^\n]+?)(?=(?:\s+[A-E]\s+|\n|$))", clean_block))
        if len(opt_matches) < 2:
            continue

        prompt = re.sub(r"\s+", " ", clean_block[: opt_matches[0].start()]).strip()
        # Clean stray leading question IDs or trailing punctuation glitches
        prompt = re.sub(r"^\d+\.\d+\s*", "", prompt).strip()
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


def extract_exercise_prompts_for_unit(plumber_page, unit_num: int) -> Dict[Tuple[str, str], Tuple[str, str]]:
    """
    Extracts actual exercise sentences with '________' blanks from a unit's Exercise page.
    Returns mapping: (ex_code e.g. '1.1', item_num e.g. '2') -> (instruction, prompt_sentence)
    """
    lines = reconstruct_lines_with_blanks(plumber_page, 0.06, 0.94)
    results: Dict[Tuple[str, str], Tuple[str, str]] = {}
    current_ex = f"{unit_num}.1"
    current_inst = ""
    current_item: Optional[str] = None
    buf: List[str] = []

    def flush():
        nonlocal current_item, buf
        if current_item and buf:
            text = re.sub(r"\s+", " ", " ".join(buf)).strip()
            if len(text) >= 6:
                if "________" not in text:
                    text = f"{text} ________"
                results[(current_ex, current_item)] = (current_inst, text)
        current_item = None
        buf = []

    for ln in lines:
        if ln.lower().startswith("exercises") or re.match(r"^unit\s+\d+$", ln.lower()):
            continue
        ex_m = re.match(rf"^({unit_num}\.\d+)\s+(.*)$", ln)
        if ex_m:
            flush()
            current_ex = ex_m.group(1)
            current_inst = ex_m.group(2).strip()
            continue
        item_m = re.match(r"^(\d{1,2})\s+(.+)$", ln)
        if item_m:
            num_val = int(item_m.group(1))
            if 1 <= num_val <= 20:
                flush()
                current_item = item_m.group(1)
                buf = [item_m.group(2).strip()]
                continue
        if current_item is not None:
            buf.append(ln)
        elif current_inst and len(current_inst) < 140:
            current_inst = f"{current_inst} {ln}".strip()
    flush()
    return results


def build_written_questions(plumber_pdf, answer_keys: Dict[int, str]) -> List[dict]:
    written: List[dict] = []
    total_pages = len(plumber_pdf.pages)

    for u_num in range(1, TOTAL_UNITS + 1):
        key_body = answer_keys.get(u_num, "")
        if not key_body:
            continue

        exercise_pdf_idx = (START_PDF_PAGE - 1) + (u_num - 1) * 2 + 1
        ex_prompts: Dict[Tuple[str, str], Tuple[str, str]] = {}
        if 0 <= exercise_pdf_idx < total_pages:
            ex_prompts = extract_exercise_prompts_for_unit(plumber_pdf.pages[exercise_pdf_idx], u_num)

        current_ex = f"{u_num}.1"
        count = 0
        for raw_ln in key_body.splitlines():
            ln = raw_ln.strip()
            ex_hdr = re.match(rf"^({u_num}\.\d+)\b", ln)
            if ex_hdr:
                current_ex = ex_hdr.group(1)
                continue
            item_m = re.match(r"^(\d{1,2})\s+(.+)$", ln)
            if item_m and count < 10:
                q_num, ans_raw = item_m.group(1), item_m.group(2).strip()
                if len(ans_raw) < 2 or len(ans_raw) > 95:
                    continue
                # Skip example answers like "Example" or meta notes
                if ans_raw.lower().startswith("example"):
                    continue
                alts = [a.strip() for a in re.split(r"\s+or\s+|\s*/\s*", ans_raw) if a.strip()]
                if not alts:
                    continue

                inst, extracted_prompt = ex_prompts.get((current_ex, q_num), ("", ""))
                if extracted_prompt:
                    prompt_text = extracted_prompt
                else:
                    prompt_text = f"Exercise {current_ex}, item {q_num}: ________"

                written.append({
                    "id": f"wr_u{u_num}_{current_ex.replace('.', '_')}_{q_num}",
                    "unitNumbers": [u_num],
                    "sourceLabel": f"{current_ex} · #{q_num}",
                    "instruction": inst,
                    "prompt": prompt_text,
                    "acceptedAnswers": alts[:6],
                })
                count += 1
    return written


def write_zip(entries: List[Tuple[Path, str]], out_zip: Path):
    out_zip.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
        for fpath, arc in entries:
            zf.write(fpath, arc)


def main() -> int:
    parser = argparse.ArgumentParser(description="Build PDF-native ArvanCloud ZIP packs + Kotlin JSON assets (v2).")
    parser.add_argument("--pdf", required=True, help="Path to clean English Grammar in Use PDF")
    parser.add_argument("--audio", required=True, help="Path to extracted Audio CD folder")
    parser.add_argument("--key-dpi", type=int, default=210, help="DPI for cropped Answer Key images (default 210)")
    args = parser.parse_args()

    if pdfium is None or pdfplumber is None or Image is None or PdfReader is None:
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
    tmp_pdfs = packs_root / "_tmp_pdfs"
    tmp_keys = packs_root / "_tmp_keys"
    assets_dir = project_root / "app" / "src" / "main" / "assets"
    for d in (packs_out, tmp_pdfs, tmp_keys, assets_dir):
        d.mkdir(parents=True, exist_ok=True)

    print(f"[INFO] Opening clean PDF: {pdf_path.name}")
    pdf_reader = PdfReader(str(pdf_path))
    pdf_doc = pdfium.PdfDocument(str(pdf_path))

    book_pages = []
    unit_summaries = []
    pack_entries: Dict[str, List[Tuple[Path, str]]] = {name: [] for name, _, _ in PACK_RANGES}
    pack_pdf_indices: Dict[str, List[int]] = {name: [] for name, _, _ in PACK_RANGES}
    resource_entries: List[Tuple[Path, str]] = []
    resource_pdf_indices: List[int] = []

    with pdfplumber.open(str(pdf_path)) as plumber_pdf:
        print("[INFO] Extracting Answer Keys (text + high-DPI Cambridge crops)...")
        answer_keys = extract_all_answer_keys(plumber_pdf)
        key_images = crop_answer_key_images(plumber_pdf, pdf_doc, tmp_keys, dpi=args.key_dpi)
        print(f"[INFO] Extracted Answer Keys: {len(answer_keys)}/145 text, {len(key_images)}/145 cropped images.")

        print("[INFO] Extracting Study Guide MCQs (with vector blanks) & Written exercises...")
        mcqs = extract_study_guide_mcqs(plumber_pdf)
        writtens = build_written_questions(plumber_pdf, answer_keys)
        print(f"[INFO] Quiz bank ready: {len(mcqs)} MCQs + {len(writtens)} Written items.")

        for unit_num in range(1, TOTAL_UNITS + 1):
            lesson_pdf_idx = (START_PDF_PAGE - 1) + (unit_num - 1) * 2
            exercise_pdf_idx = lesson_pdf_idx + 1
            lesson_book_page = unit_num * 2
            exercise_book_page = lesson_book_page + 1

            u_tag = f"u{unit_num:03d}"
            pack_name = next(name for name, s, e in PACK_RANGES if s <= unit_num <= e)
            pack_pdf_rel = f"pages/{pack_name}.pdf"

            lesson_local_idx = len(pack_pdf_indices[pack_name])
            pack_pdf_indices[pack_name].append(lesson_pdf_idx)
            exercise_local_idx = len(pack_pdf_indices[pack_name])
            pack_pdf_indices[pack_name].append(exercise_pdf_idx)

            key_img_rel = ""
            if unit_num in key_images:
                key_img_rel = f"keys/{u_tag}_key.png"
                pack_entries[pack_name].append((key_images[unit_num], key_img_rel))

            audio_files = find_unit_audio_files(audio_root, unit_num)
            expected_labels = sorted(audio_files.keys())
            title, bands, ratio = extract_lesson_info(plumber_pdf.pages[lesson_pdf_idx], expected_labels)

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
                "pdfPath": pack_pdf_rel,
                "pdfPageIndex": lesson_local_idx,
                "imagePath": "",
                "aspectRatio": ratio,
                "sections": sections_payload,
            })

            exercise_idx = len(book_pages)
            book_pages.append({
                "id": f"{u_tag}_exercise",
                "unitNumber": unit_num,
                "unitTitle": title,
                "bookPage": exercise_book_page,
                "kind": "exercise",
                "pdfPath": pack_pdf_rel,
                "pdfPageIndex": exercise_local_idx,
                "imagePath": "",
                "aspectRatio": ratio,
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
                "answerKeyImagePath": key_img_rel,
            })

            if unit_num % 30 == 0 or unit_num == TOTAL_UNITS:
                print(f"[PROGRESS] Units {unit_num}/{TOTAL_UNITS} processed...")

        # Back-matter resources -> pack_resources.zip (lossless sliced PDF)
        resources = []
        specs = [
            ("appendices", "appendix", "Appendices 1–7", "Irregular verbs, tenses, short forms, spelling & American English", APPENDICES_RANGE, 292),
            ("additional", "additional", "Additional Exercises", "Mixed review exercises combining multiple units", ADDITIONAL_RANGE, 302),
            ("study_guide", "study_guide", "Study Guide", "Diagnostic test to identify units you need to review", STUDY_GUIDE_RANGE, 326),
        ]
        res_pdf_rel = "pages/pack_resources.pdf"
        for res_id, kind, title_en, sub_en, (ps, pe), bp_start in specs:
            start_idx = len(book_pages)
            count = 0
            for offset, pdf_pno in enumerate(range(ps, min(pe, len(pdf_reader.pages)) + 1)):
                local_idx = len(resource_pdf_indices)
                resource_pdf_indices.append(pdf_pno - 1)
                p_obj = plumber_pdf.pages[pdf_pno - 1]
                ratio = round(float(p_obj.width) / float(p_obj.height), 4)
                book_pages.append({
                    "id": f"{res_id}_{pdf_pno}",
                    "unitNumber": 0,
                    "unitTitle": title_en,
                    "bookPage": bp_start + offset,
                    "kind": kind,
                    "pdfPath": res_pdf_rel,
                    "pdfPageIndex": local_idx,
                    "imagePath": "",
                    "aspectRatio": ratio,
                    "sections": [],
                })
                count += 1
            resources.append({
                "id": res_id,
                "category": kind,
                "title": title_en,
                "subtitle": sub_en,
                "startPageIndex": start_idx,
                "pageCount": count,
            })

    # Slice lossless PDFs and write all 6 ZIP packs
    print("[INFO] Slicing lossless PDF packs and building ZIP archives...")
    for pack_name, _, _ in PACK_RANGES:
        sliced_pdf = tmp_pdfs / f"{pack_name}.pdf"
        slice_pdf_pages(pdf_reader, pack_pdf_indices[pack_name], sliced_pdf)
        pack_entries[pack_name].insert(0, (sliced_pdf, f"pages/{pack_name}.pdf"))
        zpath = packs_out / f"{pack_name}.zip"
        write_zip(pack_entries[pack_name], zpath)
        print(f"[OK] {zpath.name}: {len(pack_entries[pack_name])} files ({zpath.stat().st_size / (1024 * 1024):.1f} MB)")

    res_pdf = tmp_pdfs / "pack_resources.pdf"
    slice_pdf_pages(pdf_reader, resource_pdf_indices, res_pdf)
    resource_entries.insert(0, (res_pdf, "pages/pack_resources.pdf"))
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

    print("\n[DONE] v2 PDF-Native assets are ready!")
    print(f"  1. Upload the 6 ZIPs in: {packs_out}")
    print("     into folder 'packs/' inside your ArvanCloud bucket (overwrite old ZIPs).")
    print("  2. Commit & Push updated book_data.json & quiz_data.json to GitHub.")
    print("  3. GitHub Actions will automatically build your signed v2.0.0 Myket APKs!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
