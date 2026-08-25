# -*- coding: utf-8 -*-
"""
Author -- Michael Widrich, Erich Kobler, Andreas Schörgenhumer
Contact -- schoergenhumer@ml.jku.at
Date -- 11.04.2022

###############################################################################

The following copyright statement applies to all code within this file.

Copyright statement:
This  material,  no  matter  whether  in  printed  or  electronic  form,
may  be  used  for personal  and non-commercial educational use only.
Any reproduction of this manuscript, no matter whether as a whole or in parts,
no matter whether in printed or in electronic form, requires explicit prior
acceptance of the authors.

###############################################################################

Example solution for exercise 4
"""

import numpy as np


# TEST SET Specification
MIN_OFFSET = 0
MAX_OFFSET = 8
MIN_SPACING = 2
MAX_SPACING = 6
MIN_KNOWN_PIXELS = 144


def ex4(image_array: np.ndarray, offset: tuple, spacing: tuple):
    if not isinstance(image_array, np.ndarray):
        raise TypeError("image_array must be a numpy array!")
    
    if image_array.ndim != 3 or image_array.shape[2] != 3:
        raise NotImplementedError("image_array must be a 3D numpy array whose 3rd dimension is of size 3")
    
    # Check for conversion to int (would raise ValueError anyway, but we will write a nice error message)
    try:
        offset = [int(o) for o in offset]
        spacing = [int(s) for s in spacing]
    except ValueError as e:
        raise ValueError(f"Could not convert entries in offset and spacing ({offset} and {spacing}) to int! Error: {e}")
    
    for i, o in enumerate(offset):
        if o < MIN_OFFSET or o > MAX_OFFSET:
            raise ValueError(f"Value in offset[{i}] must be in [{MIN_OFFSET}, {MAX_OFFSET}] but is {o}")
    
    for i, s in enumerate(spacing):
        if s < MIN_SPACING or s > MAX_SPACING:
            raise ValueError(f"Value in spacing[{i}] must be in [{MIN_SPACING}, {MAX_SPACING}] but is {s}")
    
    # Change dimensions from (H, W, C) to PyTorch's (C, H, W)
    image_array = np.transpose(image_array, (2, 0, 1))
    
    # Create known_array
    known_array = np.zeros_like(image_array)
    known_array[:, offset[1]::spacing[1], offset[0]::spacing[0]] = 1
    
    known_pixels = np.sum(known_array[0], dtype=np.uint32)
    if known_pixels < MIN_KNOWN_PIXELS:
        raise ValueError(f"The number of known pixels after removing must be at "
                         f"least {MIN_KNOWN_PIXELS} but is {known_pixels}")

    # Use image_array as input_array
    image_array[known_array == 0] = 0
    
    return image_array, known_array  # image_array is the input_array
