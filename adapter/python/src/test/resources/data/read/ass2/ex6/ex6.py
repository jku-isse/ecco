"""
Author: Matthias Preuner
Matr.Nr.: K01027927
Exercise 6
"""

import torch


def ex6(logits: torch.Tensor, activation_function, threshold: torch.Tensor, targets: torch.Tensor):
    if not isinstance(logits, torch.Tensor) or not logits.is_floating_point:
        raise TypeError("logits is not a torch tensor or not of type floating point.")

    if not isinstance(threshold, torch.Tensor):
        raise TypeError("threshold is not a torch tensor")

    if not isinstance(targets, torch.Tensor) or targets.dtype != torch.bool:
        raise TypeError("targets is not a torch tensor or not of type torch.bool.")

    # ValueError, if the shape of logits or targets is not (n_samples,)
    (n_samples,) = logits.shape
    if not logits.shape == (n_samples,) or not targets.shape == (n_samples,):
        raise ValueError(
            f"logits.shape and targets.shape have to be ({n_samples},) but are {logits.shape} and {targets.shape}.")

    # ValueError, if n_samples is not equal for logits and targets
    if logits.shape[0] != targets.shape[0]:
        raise ValueError("n_samples is not equal for logits and targets.")

    # ValueError, if targets does not contain at least one entry with value False and at least one entry with value True
    if not targets.__contains__(True) or not targets.__contains__(False):
        raise ValueError('At least one positive and negative entry in targets must exist.')

    # The computation of scores must be performed using torch.float64 datatype.
    torch.set_default_dtype(torch.float64)

    # 1. the confusion matrix as a nested list [[TP, FN], [FP, TN]]
    nn_output = activation_function(logits)
    TP, FN, FP, TN = (0, 0, 0, 0)
    for output, target in zip((nn_output >= threshold), targets):
        if output == target:
            if output:
                TP += 1
            else:
                TN += 1
        else:
            if output:
                FP += 1
            else:
                FN += 1

    cm = [[TP, FN], [FP, TN]]

    # 2. the F1-score as Python float object (set to 0 in case of a division by 0 error)
    try:
        F1 = float(2 * TP / (2 * TP + FP + FN))
    except ZeroDivisionError:
        F1 = float(0)

    # 3. the accuracy as Python float object
    ACC = float((TP + TN) / (TP + TN + FP + FN))

    # 4. the balanced accuracy as Python float object
    TPR = float(TP / (TP + FN))
    TNR = float(TN / (TN + FP))
    BA = float((TPR + TNR) / 2)

    return cm, F1, ACC, BA
