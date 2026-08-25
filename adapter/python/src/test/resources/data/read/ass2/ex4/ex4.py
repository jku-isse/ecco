"""
Author: Matthias Preuner
Matr.Nr.: K01027927
Exercise 4
"""

import numpy as np


def ex4(image_array, offset: tuple, spacing: tuple):
    if not isinstance(image_array, np.ndarray):
        raise TypeError("image_array is not a numpy array.")

    # NotImplementedError, image_array is not a 3D array.
    if image_array.ndim != 3:
        raise NotImplementedError("image_array is not a 3D array.")

    # NotImplementedError, if image_array: the size of the 3rd dimension is not equal to 3.
    if image_array.shape[2] != 3:
        raise NotImplementedError("image_array: the size of the 3rd dimension is not equal to 3.")

    # ValueError, if the values in offset and spacing are not convertible to int objects.
    try:
        offset_x = int(offset[0])
        offset_y = int(offset[1])
        spacing_x = int(spacing[0])
        spacing_y = int(spacing[1])
    except ValueError:
        raise ValueError("The values in offset and spacing are not convertible to int objects.")

    # ValueError, if the values in offset are smaller than 0 or larger than 32.
    if offset_x < 0 or 32 < offset_x or offset_y < 0 or 32 < offset_y:
        raise ValueError("The values in offset are smaller than 0 or larger than 32.")

    # ValueError, if the values in spacing are smaller than 2 or larger than 8.
    if spacing_x < 2 or 8 < spacing_x or spacing_y < 2 or 8 < spacing_y:
        raise ValueError("The values in spacing are smaller than 2 or larger than 8.")

    m, n, _ = image_array.shape

    input_array = np.transpose(image_array, (2, 0, 1))
    known_array = np.zeros_like(input_array)  # init with zeros

    x = offset[0]
    while x < n:
        y = offset[1]
        while y < m:
            known_array[:, y, x] = 1  # set grid coordinates to 1
            y += spacing[1]
        x += spacing[0]

    # ValueError, if the number of the remaining known image pixels would be smaller than 144.
    count_known_sum = known_array.sum() / 3
    if count_known_sum < 144:
        raise ValueError(format(
            "The number of known pixels after removing must be at least 144 but is %d" % int(count_known_sum)))

    # using the inverted known_array as boolean mask on image_array for target array
    target_array = input_array[known_array < 1]

    # mask out off-grid in image_array for input array
    input_array = input_array * known_array

    return input_array, known_array, target_array
