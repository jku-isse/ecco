"""
Author: Matthias Preuner
Matr.Nr.: K01027927
Exercise 2
"""

import os
import shutil

import PIL
from PIL import ImageStat, Image
import numpy as np
import glob
import hashlib


def validate_images(input_dir: str, output_dir: str, log_file: str, formatter: str = "d"):

    if not os.path.isdir(output_dir):  # create output_dir if not exists
        os.makedirs(output_dir)

    log = open(log_file, "w")  # create log file (overwrite if exists)
    already_copied = []  # store hashes
    n_valid = 0  # count valid images

    # loop over sorted input_dir recursive
    for file in sorted(glob.glob(os.path.join(os.path.abspath(input_dir), '**', '*.*'), recursive=True)):

        file_name = os.path.basename(file)

        # 1. The file name ends with .jpg, .JPG, .jpeg or .JPEG.
        if not (file.endswith(".jpg") or file.endswith(".JPG") or file.endswith(".jpeg") or file.endswith(".JPEG")):
            log.write(file_name + ";%d\n" % 1)
            continue

        # 2. The file size does not exceed 250kB (=250 000 Bytes).
        if os.path.getsize(file) >= 250000:
            log.write(file_name + ";%d\n" % 2)
            continue

        # 3. The file can be read as image (i.e., the PIL/pillow module does not raise an exception when reading the file).
        try:
            img = PIL.Image.open(file)
        except PIL.UnidentifiedImageError:
            log.write(file_name + ";%d\n" % 3)
            continue

        # 4. The image data has a shape of (H, W, 3) with H (height) and W (width) larger than or equal to 96 pixels.
        # The three channels must be in the order RGB (red, green, blue).
        img_array = np.array(img)
        if len(img_array.shape) != 3 or img_array.shape[0] < 96 or img_array.shape[1] < 96 or img_array.shape[2] != 3:
            log.write(file_name + ";%d\n" % 4)
            continue

        if not img.mode == "RGB":
            log.write(file_name + ";%d\n" % 4)
            continue

        # 5. The image data has a variance larger than 0, i.e., there is not just one common RGB pixel in the image data
        if not PIL.ImageStat.Stat(img).var > [0.0, 0.0, 0.0]:
            log.write(file_name + ";%d\n" % 5)
            continue

        # 6. The same image data has not been copied already.
        hashing_function = hashlib.sha256()
        hashing_function.update(img_array.tobytes())
        img_hash = hashing_function.digest()
        if img_hash in already_copied:
            log.write(file_name + ";%d\n" % 6)
            continue

        # copy image
        new_file_name = os.path.join(output_dir, format(n_valid, formatter) + ".jpg")
        shutil.copy(file, new_file_name)
        n_valid += 1
        already_copied.append(img_hash)

    log.close()
    return n_valid
