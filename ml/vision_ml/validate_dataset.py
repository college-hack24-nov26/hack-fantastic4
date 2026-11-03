"""Validate approved COCO annotations without starting training."""

import argparse
import json
from pathlib import Path

from .dataset import validate_coco


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--annotations", required=True)
    parser.add_argument("--images", required=True)
    args = parser.parse_args()
    document = json.loads(Path(args.annotations).read_text(encoding="utf-8"))
    print(json.dumps(validate_coco(document, Path(args.images))))


if __name__ == "__main__":
    main()
