#!/usr/bin/env python3
"""
Model download script for TrailQuest.
Fetches Gemma 3 270M IT Q4_K_M GGUF from Hugging Face pinned to immutable commit revision:
88762a3700deaf5b03184019806c88dc9b5c8876
Verifies SHA-256 hash before atomic move into app/src/main/assets/models.
"""
import hashlib
import os
import shutil
import sys
import tempfile
import urllib.request

REVISION = "88762a3700deaf5b03184019806c88dc9b5c8876"
MODEL_FILENAME = "gemma-3-270m-it-q4_k_m.gguf"
DOWNLOAD_URL = f"https://huggingface.co/gguf-org/gemma-3-270m-it-gguf/resolve/{REVISION}/{MODEL_FILENAME}"
EXPECTED_SHA256 = "55aed1673d2f3014a739732c659c4995fc57abf6e4e9c68462fb2ceee820ea2f"
EXPECTED_BYTES = 253114880

TARGET_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "assets", "models")
TARGET_PATH = os.path.join(TARGET_DIR, MODEL_FILENAME)

def main():
    os.makedirs(TARGET_DIR, exist_ok=True)
    if os.path.exists(TARGET_PATH):
        print(f"Target file exists: {TARGET_PATH}. Verifying existing file hash...")
        h = hashlib.sha256()
        with open(TARGET_PATH, "rb") as f:
            while chunk := f.read(1024 * 1024):
                h.update(chunk)
        if h.hexdigest() == EXPECTED_SHA256:
            print("Existing model verified successfully!")
            return 0
        else:
            print(f"Existing file hash mismatch ({h.hexdigest()} != {EXPECTED_SHA256}). Re-downloading...")

    print(f"Downloading {MODEL_FILENAME} from {DOWNLOAD_URL}...")
    temp_dir = tempfile.gettempdir()
    temp_file = os.path.join(temp_dir, f"{MODEL_FILENAME}.tmp")

    hasher = hashlib.sha256()
    bytes_downloaded = 0
    req = urllib.request.Request(DOWNLOAD_URL, headers={"User-Agent": "TrailQuest-ModelFetcher/1.0"})
    
    with urllib.request.urlopen(req) as resp, open(temp_file, "wb") as out_f:
        total_len = resp.headers.get("Content-Length")
        total_bytes = int(total_len) if total_len else EXPECTED_BYTES
        while chunk := resp.read(1024 * 1024):
            out_f.write(chunk)
            hasher.update(chunk)
            bytes_downloaded += len(chunk)
            pct = (bytes_downloaded / total_bytes) * 100 if total_bytes else 0
            sys.stdout.write(f"\rProgress: {bytes_downloaded / (1024*1024):.1f} MB / {total_bytes / (1024*1024):.1f} MB ({pct:.1f}%)")
            sys.stdout.flush()

    print()
    actual_hash = hasher.hexdigest()
    print(f"Actual SHA-256:   {actual_hash}")
    print(f"Expected SHA-256: {EXPECTED_SHA256}")

    if actual_hash != EXPECTED_SHA256:
        if os.path.exists(temp_file):
            os.remove(temp_file)
        raise ValueError("SHA-256 checksum verification failed!")

    print(f"Moving temporary file atomically to {TARGET_PATH}...")
    shutil.move(temp_file, TARGET_PATH)
    print("Model download and verification complete!")
    return 0

if __name__ == "__main__":
    sys.exit(main())
