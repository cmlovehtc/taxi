#!/usr/bin/env python3
"""Download, validate, and publish the official Taiwan taxi question bank.

The script writes to a staging directory first. Existing question files are only
replaced after all 46 files parse successfully and pass conservative count checks.
"""

from __future__ import annotations

import hashlib
import io
import json
import os
import re
import shutil
import sys
import tempfile
import time
import unicodedata
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable
from zoneinfo import ZoneInfo

import pdfplumber
import requests
from bs4 import BeautifulSoup
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry


BASE_URL = "https://tx3.npa.gov.tw/NM112-512WebE"
DOWNLOAD_PAGE = f"{BASE_URL}/download"
PREPARE_URL = f"{BASE_URL}/download/downloadQuestionBank"
FILE_URL = f"{BASE_URL}/download/downloadQuestionBank1"
ROOT = Path(__file__).resolve().parents[1]

CITY_CODES = {
    "臺北市": "A",
    "新北市": "F",
    "桃園市": "H",
    "臺中市": "B",
    "臺南市": "D",
    "高雄市": "E",
    "基隆市": "C",
    "新竹市": "O",
    "嘉義市": "I",
    "新竹縣": "J",
    "苗栗縣": "K",
    "彰化縣": "N",
    "南投縣": "M",
    "雲林縣": "P",
    "嘉義縣": "Q",
    "屏東縣": "T",
    "宜蘭縣": "G",
    "花蓮縣": "U",
    "臺東縣": "V",
    "澎湖縣": "X",
    "金門縣": "W",
    "連江縣": "Z",
}


@dataclass(frozen=True)
class ParsedQuestion:
    question: str
    answer: str


def normalize_text(value: str | None) -> str:
    if not value:
        return ""
    value = unicodedata.normalize("NFKC", value)
    value = value.replace("\u3000", " ").replace("\xa0", " ")
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def normalize_answer(value: str, question_type: str) -> str:
    value = normalize_text(value).upper()
    value = value.replace("解答", "").replace("答案", "").strip(" :：。,.，")
    if question_type == "是非題":
        if value in {"O", "0", "○", "是", "對"}:
            return "是"
        if value in {"X", "×", "否", "錯"}:
            return "否"
        return ""
    match = re.search(r"[1-9]", value)
    return match.group(0) if match else ""


def clean_question(value: str) -> str:
    value = normalize_text(value)
    value = re.sub(r"^\d+\s*[.、]?\s*", "", value)
    value = re.sub(r"^\(?\s*[()（）]\s*\)?\s*", "", value)
    value = value.replace("（ ）", "").replace("( )", "")
    value = re.sub(r"(?<=[\u3400-\u9fff])\s+(?=[\u3400-\u9fff])", "", value)
    value = re.sub(r"\s+([,，。;；:：?？])", r"\1", value)
    return value.strip()


def valid_question(question: str, answer: str, question_type: str) -> bool:
    if len(question) < 6 or not answer:
        return False
    if question_type == "選擇題":
        return all(token in question for token in ("(1)", "(2)")) and f"({answer})" in question
    return True


def parse_tables(pdf: pdfplumber.PDF, question_type: str) -> list[ParsedQuestion]:
    results: list[ParsedQuestion] = []
    for page in pdf.pages:
        for table in page.extract_tables() or []:
            for row in table or []:
                cells = [normalize_text(cell) for cell in (row or [])]
                if len(cells) < 3 or not re.fullmatch(r"\d+", cells[0]):
                    continue

                answer_index = -1
                answer = ""
                for index in range(2, len(cells)):
                    candidate = normalize_answer(cells[index], question_type)
                    if candidate:
                        answer_index = index
                        answer = candidate
                        break

                if answer_index < 0:
                    continue
                question = clean_question(" ".join(cells[1:answer_index]))
                if valid_question(question, answer, question_type):
                    results.append(ParsedQuestion(question, answer))
    return results


def split_numbered_blocks(text: str) -> Iterable[str]:
    current: list[str] = []
    for raw_line in text.splitlines():
        line = normalize_text(raw_line)
        if not line or "試題題目" in line or "更新日期" in line:
            continue
        if re.match(r"^\d+\s*[.、]?(?:\s|（|\()", line):
            if current:
                yield "\n".join(current)
            current = [line]
        elif current and not re.search(r"預祝|第\s*\d+\s*頁|背面尚有", line):
            current.append(line)
    if current:
        yield "\n".join(current)


def parse_text_fallback(pdf: pdfplumber.PDF, question_type: str) -> list[ParsedQuestion]:
    text = "\n".join(page.extract_text(layout=True) or "" for page in pdf.pages)
    results: list[ParsedQuestion] = []
    answer_token = r"(?:O|0|X|○|×|是|否|對|錯)" if question_type == "是非題" else r"[1-9]"
    date_token = r"(?:\d{2,4}[/.\-年]\d{1,2}[/.\-月]\d{1,2}日?)?"

    for block in split_numbered_blocks(text):
        # Full question-bank PDFs put the answer in the last columns. Some PDFs
        # append an update date. Wrapped rows can have the answer at the end of
        # the first visual line followed by the rest of the question below it.
        number_match = re.match(r"^(\d+)\s*[.、]?\s*(.*)$", block, flags=re.DOTALL)
        if not number_match:
            continue
        body = number_match.group(2)
        answer_match = re.search(
            rf"(?<!\S)({answer_token})\s*{date_token}\s*(?=\n|$)",
            body,
            flags=re.IGNORECASE | re.MULTILINE,
        )
        if not answer_match:
            continue
        question = clean_question(body[: answer_match.start()] + " " + body[answer_match.end() :])
        answer = normalize_answer(answer_match.group(1), question_type)
        if valid_question(question, answer, question_type):
            results.append(ParsedQuestion(question, answer))
    return results


def parse_pdf(pdf_bytes: bytes, question_type: str) -> list[ParsedQuestion]:
    with pdfplumber.open(io.BytesIO(pdf_bytes)) as pdf:
        questions = parse_tables(pdf, question_type)
        if len(questions) < 10:
            questions = parse_text_fallback(pdf, question_type)

    unique: list[ParsedQuestion] = []
    seen: set[str] = set()
    for question in questions:
        key = normalize_text(question.question)
        if key in seen:
            continue
        seen.add(key)
        unique.append(question)
    return unique


def make_session() -> tuple[requests.Session, str, str]:
    session = requests.Session()
    session.headers.update(
        {
            "User-Agent": (
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
            ),
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "zh-TW,zh;q=0.9,en;q=0.7",
        }
    )
    retry = Retry(
        total=5,
        connect=5,
        read=5,
        status=5,
        backoff_factor=4,
        backoff_jitter=1,
        status_forcelist=(429, 500, 502, 503, 504),
        allowed_methods=None,
        respect_retry_after_header=True,
    )
    session.mount("https://", HTTPAdapter(max_retries=retry))
    response = session.get(DOWNLOAD_PAGE, timeout=45)
    response.raise_for_status()
    soup = BeautifulSoup(response.text, "html.parser")
    csrf = soup.select_one('meta[name="_csrf"]')
    csrf_header = soup.select_one('meta[name="_csrf_header"]')
    if not csrf or not csrf.get("content"):
        raise RuntimeError("官方下載頁未提供 CSRF token")
    return session, str(csrf["content"]), str(csrf_header.get("content") if csrf_header else "X-CSRF-TOKEN")


def download_bundle(traffic: list[str], city: str | None = None) -> bytes:
    session, csrf, csrf_header = make_session()
    payload: dict[str, object] = {"traffic": traffic}
    if city:
        payload["city"] = city
    headers = {
        "Referer": DOWNLOAD_PAGE,
        "X-Requested-With": "XMLHttpRequest",
        csrf_header: csrf,
    }
    prepare = session.post(
        PREPARE_URL,
        data={"downloadForm": json.dumps(payload, ensure_ascii=False, separators=(",", ":"))},
        headers=headers,
        timeout=60,
    )
    prepare.raise_for_status()
    try:
        result = prepare.json()
    except requests.JSONDecodeError:
        result = json.loads(prepare.text)
    if not result.get("result") or not result.get("msg"):
        raise RuntimeError(f"官方網站未建立下載檔：{result.get('msg', '未知錯誤')}")

    download = session.post(
        FILE_URL,
        data={"_csrf": csrf, "token": result["msg"]},
        headers={"Referer": DOWNLOAD_PAGE},
        timeout=90,
    )
    download.raise_for_status()
    if len(download.content) < 1_000:
        raise RuntimeError("官方網站回傳的題庫檔案異常過小")
    return download.content


def extract_pdfs(bundle: bytes) -> dict[str, bytes]:
    if bundle.startswith(b"%PDF"):
        return {"question-bank.pdf": bundle}
    if not zipfile.is_zipfile(io.BytesIO(bundle)):
        raise RuntimeError("官方網站回傳的檔案不是 PDF 或 ZIP")
    with zipfile.ZipFile(io.BytesIO(bundle)) as archive:
        return {
            Path(name).name: archive.read(name)
            for name in archive.namelist()
            if name.lower().endswith(".pdf")
        }


def select_pdf(pdfs: dict[str, bytes], question_type: str) -> bytes:
    keyword = "是非" if question_type == "是非題" else "選擇"
    matches = [content for name, content in pdfs.items() if keyword in normalize_text(name)]
    if len(matches) == 1:
        return matches[0]
    if len(pdfs) == 2:
        scored = sorted(
            ((len(parse_pdf(content, question_type)), content) for content in pdfs.values()),
            key=lambda item: item[0],
            reverse=True,
        )
        if scored[0][0] >= 10 and scored[0][0] > scored[1][0]:
            return scored[0][1]
    raise RuntimeError(f"下載檔中找不到唯一的{question_type} PDF")


def existing_count(filename: str) -> int:
    path = ROOT / filename
    if not path.exists():
        return 0
    return sum(1 for line in path.read_text(encoding="utf-8").splitlines() if "答案:" in line)


def validate_count(filename: str, questions: list[ParsedQuestion]) -> None:
    previous = existing_count(filename)
    minimum = max(10, int(previous * 0.65)) if previous else 10
    if len(questions) < minimum:
        raise RuntimeError(
            f"{filename} 只解析出 {len(questions)} 題，低於安全門檻 {minimum} 題；保留舊版"
        )


def write_questions(path: Path, questions: list[ParsedQuestion]) -> None:
    lines = [f"{question.question}答案:{question.answer}" for question in questions]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def content_hash(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    dry_run = os.environ.get("QUESTION_BANK_DRY_RUN") == "1"
    with tempfile.TemporaryDirectory(prefix="taxi-question-bank-") as temporary:
        stage = Path(temporary)
        generated: list[Path] = []

        print("Downloading national traffic-law question bank…", flush=True)
        traffic_pdfs = extract_pdfs(download_bundle(["1", "2"]))
        for question_type in ("是非題", "選擇題"):
            filename = f"交通法令_{question_type}.txt"
            questions = parse_pdf(select_pdf(traffic_pdfs, question_type), question_type)
            validate_count(filename, questions)
            output = stage / filename
            write_questions(output, questions)
            generated.append(output)
            print(f"  {filename}: {len(questions)} questions", flush=True)

        for position, (city_name, city_code) in enumerate(CITY_CODES.items(), start=1):
            print(f"[{position:02d}/{len(CITY_CODES)}] Downloading {city_name}…", flush=True)
            city_pdfs = extract_pdfs(download_bundle(["3", "4"], city_code))
            for question_type in ("是非題", "選擇題"):
                filename = f"{city_name}_地理環境_{question_type}.txt"
                questions = parse_pdf(select_pdf(city_pdfs, question_type), question_type)
                validate_count(filename, questions)
                output = stage / filename
                write_questions(output, questions)
                generated.append(output)
                print(f"  {filename}: {len(questions)} questions", flush=True)
            time.sleep(1.5)

        if len(generated) != 46:
            raise RuntimeError(f"應產生 46 個題庫檔，實際為 {len(generated)} 個")

        changed = [path for path in generated if not (ROOT / path.name).exists() or content_hash(path) != content_hash(ROOT / path.name)]
        if not changed:
            print("Question bank is already current.")
            return 0

        print(f"Validated {len(generated)} files; {len(changed)} files changed.")
        if dry_run:
            print("Dry run enabled; repository files were not changed.")
            return 0

        for path in generated:
            shutil.copy2(path, ROOT / path.name)

        now = datetime.now(timezone.utc).replace(microsecond=0)
        manifest = {
            "schema_version": 1,
            "updated_at": now.isoformat().replace("+00:00", "Z"),
            "source": "內政部警政署計程車駕駛人服務網",
            "source_url": DOWNLOAD_PAGE,
            "files": len(generated),
            "questions": sum(existing_count(path.name) for path in generated),
            "status": "official-auto-sync",
            "sha256": {path.name: content_hash(ROOT / path.name) for path in sorted(generated)},
        }
        (ROOT / "question-bank-manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

        index_path = ROOT / "index.html"
        index_text = index_path.read_text(encoding="utf-8")
        taipei_date = now.astimezone(ZoneInfo("Asia/Taipei")).strftime("%Y/%m/%d")
        index_text = re.sub(
            r"更新日期:\s*\d{4}/\d{1,2}/\d{1,2}日",
            f"更新日期:{taipei_date}日",
            index_text,
        )
        index_path.write_text(index_text, encoding="utf-8")
        print("Repository question bank updated successfully.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:  # noqa: BLE001 - CI must report the exact failed stage.
        print(f"ERROR: {error}", file=sys.stderr)
        raise
