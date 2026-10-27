"""Turns SigLIP 2's tokenizer.json (34 MB) into vocab.bin (about 3 MB): just the word pieces and
their numbers, which is all the app's search uses (Siglip.vocabulary). The format is what Java's
DataOutputStream writes: the count, then each piece (writeUTF, modified UTF-8) and its number."""
import json
import struct
import sys


def modified_utf8(text):
    units = text.encode("utf-16-le")
    out = bytearray()
    for i in range(0, len(units), 2):
        c = units[i] | (units[i + 1] << 8)
        if 0 < c < 0x80:
            out.append(c)
        elif c < 0x800:
            out += bytes([0xC0 | (c >> 6), 0x80 | (c & 0x3F)])
        else:
            out += bytes([0xE0 | (c >> 12), 0x80 | ((c >> 6) & 0x3F), 0x80 | (c & 0x3F)])
    return bytes(out)


def main(source, target):
    with open(source, encoding="utf-8") as f:
        vocab = json.load(f)["model"]["vocab"]
    with open(target, "wb") as out:
        out.write(struct.pack(">i", len(vocab)))
        for piece, number in sorted(vocab.items(), key=lambda item: item[1]):
            data = modified_utf8(piece)
            out.write(struct.pack(">H", len(data)) + data + struct.pack(">i", number))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
