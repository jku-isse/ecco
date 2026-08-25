# -*- coding: utf-8 -*-
"""example_project/datasets.py

Author -- Michael Widrich, Andreas Schörgenhumer
Contact -- schoergenhumer@ml.jku.at
Date -- 15.04.2022

###############################################################################

The following copyright statement applies to all code within this file.

Copyright statement:
This material, no matter whether in printed or electronic form, may be used for
personal and non-commercial educational use only. Any reproduction of this
manuscript, no matter whether as a whole or in parts, no matter whether in
printed or in electronic form, requires explicit prior acceptance of the
authors.

###############################################################################

Datasets file of example project.
"""
import pickle
import random

import numpy as np
import torchvision.transforms as transforms
import torchvision.transforms.functional as TF
import glob
import os
from PIL import Image
import torch
from torch.utils.data import Dataset
from torch.utils.data import DataLoader

import ex4_sample


# 3. Create te data loader and stacking function for the minibatches (see code files of Unit 5).
class AugmentedImages(Dataset):
    def __init__(self, dataset: Dataset, transform_chain: transforms.Compose = None):
        """Provides images from 'dataset' as inputs and images rotated by 'rotation_angle' as targets"""
        self.dataset = dataset
        self.transform_chain = transform_chain  # Note: torchvision.transforms will be topic in Unit 08
        self.mean = np.array([0.485, 0.456, 0.406])
        self.std = np.array([0.229, 0.224, 0.225])

    def __len__(self):
        if self.transform_chain is not None:
            return len(self.dataset) * 2
        else:
            return len(self.dataset)

    def __getitem__(self, idx):

        if self.transform_chain is not None:
            image, idx_old = self.dataset[idx // 2]
            rotation_angle = int((idx % 2) * 90)
            # rotate
            image = TF.rotate(image, angle=rotation_angle, interpolation=TF.InterpolationMode.BILINEAR)
            # apply transform chain
            image = self.transform_chain(image)
        else:
            image, idx_old = self.dataset[idx]

        # # Convert to float32
        image = np.array(image, dtype=np.float32) / 255
        image_array = np.transpose(image.copy(), (2, 0, 1))
        offset = (random.randint(0, 8), random.randint(0, 8))
        spacing = (random.randint(2, 6), random.randint(2, 6))
        input_array, known_array = ex4_sample.ex4(image, offset, spacing)

        # apply preprocessing
        preprocess(input_array, offset, spacing)

        # apply mean + std normalization
        input_array = (input_array - self.mean[:, None, None]) / self.std[:, None, None]

        concat_input_array = np.concatenate((input_array, known_array), axis=0)

        return image_array, concat_input_array, known_array, self.mean, self.std, idx


class SimpleImageDataset(Dataset):
    def __init__(self, data_folder):
        self.image_files = sorted(glob.glob(os.path.join(data_folder, "**", "*.jpg"), recursive=True))
        self.im_shape = 100
        self.resize_transforms = transforms.Compose([
            transforms.Resize(size=self.im_shape),
            transforms.CenterCrop(size=(self.im_shape, self.im_shape)),
        ])

    def __getitem__(self, index):
        image = Image.open(self.image_files[index])
        image = self.resize_transforms(image)
        return image, index

    def __len__(self):
        return len(self.image_files)


class TestDataset(Dataset):
    def __init__(self, data_folder):
        with open(os.path.join(data_folder, 'inputs.pkl'), 'rb') as f:
            x = pickle.load(f)
            self.input_arrays = x['input_arrays']
            self.known_arrays = x['known_arrays']
            self.offsets = x['offsets']
            self.spacings = x['spacings']
            self.sample_ids = x['sample_ids']
            self.mean = np.array([0.485, 0.456, 0.406])
            self.std = np.array([0.229, 0.224, 0.225])

    def __getitem__(self, index):
        input_array = self.input_arrays[index]
        known_array = self.known_arrays[index]
        offset = self.offsets[index]
        spacing = self.spacings[index]
        input_array = np.array(input_array, dtype=np.float32) / 255

        # create empty arrays for collate_fn
        unknown_image_array = np.zeros(input_array.shape)

        # apply preprocessing
        preprocess(input_array, offset, spacing)

        # apply mean + std normalization
        input_array = (input_array - self.mean[:, None, None]) / self.std[:, None, None]

        concat_input_array = np.concatenate((input_array, known_array), axis=0)

        return unknown_image_array, concat_input_array, known_array, self.mean, self.std, self.sample_ids[
            index]

    def __len__(self):
        return len(self.input_arrays)


# You will need to write a stacking function for the DataLoader (collate_fn). For
# this, you can take the maximum over the X and the maximum over the Y dimensions of
# the input array and create a zero-tensor of shape (n samples, n feature channels,
# max X, max Y), so that it can hold the stacked input arrays. Then you can copy the input
# values into this zero-tensor. For a 1D example, see “Task 01” in 05_solutions.py.
def image_collate_fn(batch_as_list: list):
    #
    # Handle image_sequences
    #
    # Get sequence entries, which are at index 0 in each sample tuple
    image_sequences = [sample[0] for sample in batch_as_list]
    stacked_image_sequences = torch.stack([torch.tensor(image, dtype=torch.float32) for image in image_sequences],
                                          dim=0)
    #
    # Handle input_sequences
    #
    # Get sequence entries, which are at index 0 in each sample tuple
    input_sequences = [sample[1] for sample in batch_as_list]
    stacked_input_sequences = torch.stack([torch.tensor(inp, dtype=torch.float32) for inp in input_sequences], dim=0)
    #
    # Handle known_sequences
    #
    # Get sequence entries, which are at index 0 in each sample tuple
    known_sequences = [sample[2] for sample in batch_as_list]
    stacked_known_sequences = torch.stack([torch.tensor(known, dtype=torch.float32) for known in known_sequences],
                                          dim=0)
    #
    # Handle means
    #
    # Get label entries, which are at index 1 in each sample tuple
    means = [sample[3] for sample in batch_as_list]
    # Convert them to tensors and stack them
    stacked_means = torch.stack([torch.tensor(mean, dtype=torch.float32) for mean in means], dim=0)
    #
    # Handle stds
    #
    # Get label entries, which are at index 1 in each sample tuple
    stds = [sample[4] for sample in batch_as_list]
    stacked_stds = torch.stack([torch.tensor(std, dtype=torch.float32) for std in stds], dim=0)
    #
    # Handle labels
    #
    # Get label entries, which are at index 1 in each sample tuple
    labels = [sample[5] for sample in batch_as_list]
    stacked_labels = torch.stack([torch.tensor(label, dtype=torch.float32) for label in labels], dim=0)

    return stacked_image_sequences, stacked_input_sequences, stacked_known_sequences, stacked_means, stacked_stds, stacked_labels


def preprocess(input_array, offset: tuple, spacing: tuple):

    max_y = ((99 - offset[0]) // spacing[0]) * spacing[0] + offset[0]
    max_x = ((99 - offset[1]) // spacing[1]) * spacing[1] + offset[1]

    for y in range(offset[0], max_y):
        left = (y - offset[0]) % spacing[0]
        right = spacing[0] - left
        input_array[:, offset[1]::spacing[1], y] = (input_array[:, offset[1]::spacing[1], y + right] -
                                                    input_array[:, offset[1]::spacing[1], y - left]) / spacing[0] * \
                                                   left + input_array[:, offset[1]::spacing[1], y - left]

    for x in range(offset[1] + 1, max_x):
        left = (x - offset[1]) % spacing[1]
        right = spacing[1] - left
        input_array[:, x, offset[0]:max_y] = (input_array[:, x + right, offset[0]:max_y] -
                                              input_array[:, x - left, offset[0]:max_y]) / spacing[1] * \
                                             left + input_array[:, x - left, offset[0]:max_y]

    # input_array = np.pad(input_array[:, offset[1]:max_x, offset[0]:max_y],
    #                      ((0, 0), (offset[1], 100 - max_x), (offset[0], 100 - max_y)), mode='edge')

    return input_array


# for testing:
# Iterate through the data loader
def test_run(dataloader: DataLoader):
    for j, (images, inputs, known, means, stds, ids) in enumerate(dataloader):
        print(f"Batch {j}:")
        print(f"image ids: {ids}")
        print(f"image shape: {images.shape}")
        print(f"input shape: {inputs.shape}")
        print(f"known shape: {known.shape}")
        print(f"means shape: {means.shape}")
        print(f"stds shape: {stds.shape}")


# for testing the dataloader and collate_fn
TESTING = False
if TESTING:
    image_dataset = AugmentedImages(SimpleImageDataset("training"))
    image_loader = DataLoader(image_dataset, shuffle=True, batch_size=10, collate_fn=image_collate_fn)
    test_run(image_loader)

    test_dataset = TestDataset("test")
    test_loader = DataLoader(test_dataset, shuffle=False, batch_size=10, collate_fn=image_collate_fn)
    test_run(test_loader)
