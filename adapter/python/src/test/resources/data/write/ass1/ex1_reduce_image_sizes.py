"""
Author -- Michael Widrich, Andreas Schörgenhumer
Contact -- schoergenhumer@ml.jku.at
Date -- 03.03.2022

###############################################################################

The following copyright statement applies to all code within this file.

Copyright statement:
This  material,  no  matter  whether  in  printed  or  electronic  form,
may  be  used  for personal  and non-commercial educational use only.
Any reproduction of this manuscript, no matter whether as a whole or in parts,
no matter whether in printed or in electronic form, requires explicit prior
acceptance of the authors.

###############################################################################

This script will take a folder ``input_dir`` and resize all .jpg images in it to
a resolution such that the file size is ``max_file_size`` (bytes) or smaller. Per
default, this value is set to 250kB. Note that the new file size is typically much
smaller due to JPEG compression, but that will not bother us as long as the content
is not too blurry. The resulting image files will be written to the folder
``output_dir``, which per default, is ``input_dir`` with the postfix "_resized".

In some cases, it might also happen that the image resolution reduction heuristic
of this script does not lead to a new file size that is <= ``max_file_size`` (again,
this is due to the JPEG compression algorithm). You can try to slightly reduce
``max_file_size`` even more, or you can just use a different input image.

Usage: python reduce_image_sizes.py INPUT_DIR
            [--output_dir OUTPUT_DIR]
            [--max_file_size MAX_FILE_SIZE]
"""

import argparse
import glob
import math
import os
import shutil
import warnings

from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument("input_dir", type=str, help="The directory containing the images.")
parser.add_argument("--output_dir", type=str,
                    help="The directory containing the resized images. If not specified, 'input_dir' with "
                         "the additional postfix '_resized' will be used (directory will be created).")
parser.add_argument("--max_file_size", type=int, default=250_000,
                    help="Maximum allowed size in bytes up to which images are not resized. Default: 250kB")
args = parser.parse_args()

input_dir = args.input_dir
if not os.path.isdir(input_dir):
    raise ValueError("'input_dir' must be an existing directory")
# Get rid of a potentially trailing path separator in input_dir
output_dir = args.output_dir if args.output_dir is not None else input_dir + "_resized"
os.makedirs(output_dir, exist_ok=True)
max_file_size = args.max_file_size
# Get list of files
image_files = glob.glob(os.path.join(input_dir, "*.jpg"))
n = len(image_files)

for i, image_file in enumerate(image_files):
    file_name = os.path.basename(image_file)
    # Get size of file in bytes
    file_size = os.path.getsize(image_file)
    # Calculate reduction factor
    reduction_factor = max_file_size / file_size
    # We want to apply this factor to x and y dimension -> 2D -> square root
    reduction_factor = math.sqrt(reduction_factor)
    
    # Only change image resolution if we need to reduce the file size
    if reduction_factor < 1:
        with Image.open(image_file) as image:
            # Get current resolution and reduce it given our factor
            width, height = image.width, image.height
            new_width, new_height = int(width * reduction_factor), int(height * reduction_factor)
            # Resize image and save it
            new_image = image.resize((new_width, new_height))
            new_image_file = os.path.join(output_dir, file_name)
            new_image.save(new_image_file)
            new_file_size = os.path.getsize(new_image_file)
            print(f"[{i + 1:>{len(str(n))}}/{n}] {file_size:>8,}B >  {max_file_size:,}B: resized '{file_name}' "
                  f"from {width}x{height} to {new_width}x{new_height} with new size of {new_file_size:,}B")
            if new_file_size > max_file_size:
                warnings.warn(f"'{file_name}': new size {new_file_size:,}B is still bigger than max {max_file_size:,}B")
    else:
        # If the file size is already small enough, we can just copy the file
        shutil.copy(image_file, os.path.join(output_dir, file_name))
        print(f"[{i + 1:>{len(str(n))}}/{n}] {file_size:>8,}B <= {max_file_size:,}B: copied  '{file_name}'")
