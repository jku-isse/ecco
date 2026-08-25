"""
Author: Matthias Preuner
Matr.Nr.: K01027927
Exercise 3
"""

import glob
import os
import numpy as np
import PIL
from PIL import Image


class ImageStandardizer:

    def __init__(self, input_dir: str):
        # Scan this input directory recursively for files ending in .jpg.
        files = sorted(glob.glob(os.path.join(os.path.abspath(input_dir), '**/*.jpg'), recursive=True))

        # Raise a ValueError if there are no .jpg files.
        if len(files) == 0:
            raise ValueError

        # Transform all paths to absolute paths and sort them alphabetically in ascending order.
        # Store the sorted absolute file paths in an attribute self.files
        self.files = sorted([os.path.abspath(file) for file in files])
        self.mean = None
        self.std = None

    def analyze_images(self):
        # Compute the means and standard deviations for each color channel of all images in the
        # list self.files. Each mean and standard deviation will thus have three entries: one for
        # the red (R), one for the green (G) and one for the blue channel (B).
        means = np.zeros(shape=(len(self.files), 3))
        stds = np.zeros(shape=(len(self.files), 3))
        for i, file in enumerate(self.files):
            img_array = np.array(PIL.Image.open(file))
            means[i] = img_array.mean(axis=(0, 1))
            stds[i] = img_array.std(axis=(0, 1))

        # Store the average over these RGB means of all images in the attribute self.mean (global
        # RGB mean). This value should be a 1D numpy array of datatype np.float64 and with shape (3,).
        self.mean = np.average(means, axis=0).astype(np.float64)

        # Store the average over these RGB standard deviations of all images in the attribute self.std (global
        # RGB standard deviation). This value should be a 1D numpy array of datatype np.float64 and with shape (3,).
        self.std = np.average(stds, axis=0).astype(np.float64)
        return self.mean, self.std

    def get_standardized_images(self):

        # Raise a ValueError if self.mean or self.std is None.
        if self.mean is None or self.std is None:
            raise ValueError

        # Yield the pixel data of each image (generator function), i.e., the raw numpy data with
        # shape (H, W, 3), in the order that the image files appear in self.files. For this, in
        # each yield-iteration, the method should:

        for file in self.files:
            # Load the image and store the image data (pixels) in a 3D numpy array of datatype np.float32.
            img_array = np.array(PIL.Image.open(file)).astype(np.float32)

            # Standardize the image data using the global RGB mean and standard deviation in
            # self.mean and self.std. To do this, subtract the corresponding self.mean from
            # the pixel values and subsequently divide the pixel values by self.std for each
            # color channel.
            img_standardized = (img_array - self.mean) / self.std

            # Yield the standardized image data as 3D numpy array of datatype np.float32.
            yield img_standardized.astype(np.float32)
