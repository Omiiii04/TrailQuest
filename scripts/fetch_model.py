#!/usr/bin/env python3
"""
Model download script for TrailQuest.
Fetches Gemma 3 270M IT Q4_K_M GGUF from Hugging Face pinned to immutable commit revision:
88762a3700deaf5b03184019806c88dc9b5c8876
Verifies SHA-256 hash and exact file size before atomic replacement into app/src/main/assets/models.
"""
import hashlib
import os
import sys
import urllib.request
import uuid

REVISION = "88762a3700deaf5b03184019806c88dc9b5c8876"
MODEL_FILENAME = "gemma-3-270m-it-q4_k_m.gguf"
DOWNLOAD_URL = f"https://huggingface.co/gguf-org/gemma-3-270m-it-gguf/resolve/{REVISION}/{MODEL_FILENAME}"
EXPECTED_SHA256 = "55aed1673d2f3014a739732c659c4995fc57abf6e4e9c68462fb2ceee820ea2f"
EXPECTED_BYTES = 253114880

TARGET_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "assets", "models")
TARGET_PATH = os.path.join(TARGET_DIR, MODEL_FILENAME)

def verify_file(filepath: str) -> bool:
    if not os.path.exists(filepath):
        return False
    if os.path.getsize(filepath) != EXPECTED_BYTES:
        return False
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(1024 * 1024):
            h.update(chunk)
    return h.hexdigest() == EXPECTED_SHA256

def main():
    os.makedirs(TARGET_DIR, exist_ok=True)
    if os.path.exists(TARGET_PATH):
        print(f"Target file exists: {TARGET_PATH}. Verifying existing file...")
        if verify_file(TARGET_PATH):
            print("Existing model verified successfully (size and SHA-256 match)!")
            return 0
        else:
            print(f"Existing file corrupt or incomplete. Re-downloading...")

    unique_suffix = uuid.uuid4().hex[:8]
    temp_file = os.path.join(TARGET_DIR, f"{MODEL_FILENAME}.{unique_suffix}.tmp")
    print(f"Downloading {MODEL_FILENAME} from {DOWNLOAD_URL}...")
    print(f"Temporary download target: {temp_file}")

    hasher = hashlib.sha256()
    bytes_downloaded = 0
    req = urllib.request.Request(DOWNLOAD_URL, headers={"User-Agent": "TrailQuest-ModelFetcher/1.0"})

    try:
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
        actual_size = os.path.getsize(temp_file)
        actual_hash = hasher.hexdigest()
        print(f"Downloaded Size:  {actual_size} bytes (Expected: {EXPECTED_BYTES})")
        print(f"Actual SHA-256:   {actual_hash}")
        print(f"Expected SHA-256: {EXPECTED_SHA256}")

        if actual_size != EXPECTED_BYTES:
            raise ValueError(f"Size mismatch: downloaded {actual_size} bytes, expected {EXPECTED_BYTES}")

        if actual_hash != EXPECTED_SHA256:
            raise ValueError(f"SHA-256 mismatch: actual {actual_hash}, expected {EXPECTED_SHA256}")

        print(f"Atomic replacement via os.replace: {temp_file} -> {TARGET_PATH}...")
        os.replace(temp_file, TARGET_PATH)
        print("Model download, verification, and replacement complete!")
        return 0
    except Exception as e:
        if os.path.exists(temp_file):
            try:
                os.remove(temp_file)
                print(f"Cleaned up incomplete temporary file: {temp_file}")
            except OSError as cleanup_err:
                print(f"Warning: Failed to clean up {temp_file}: {cleanup_err}")
        print(f"Error during model fetch: {e}", file=sys.stderr)
        raise

if __name__ == "__main__":
    sys.exit(main())
