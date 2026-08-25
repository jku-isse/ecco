# -*- coding: utf-8 -*-
"""example_project/main.py

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

Main file of example project.
"""

import os
import pickle

import numpy as np
import torch
import torch.utils.data
import torch.utils.data
import tqdm
from torch.utils.data import DataLoader
from torch.utils.tensorboard import SummaryWriter
from torchvision.transforms import transforms
from tqdm import tqdm

from architectures import ChallengeCNN
from dataloader import AugmentedImages, image_collate_fn, TestDataset, SimpleImageDataset, AugmentedImages
from utils import plot, RMSELoss

# enable training will create a new model and overwrite any existing
TRAIN = False
TRAIN_CONTINUE = False
EVALUATE = True
PREDICT = True
PLOT_PREDICTIONS = False


def evaluate_model(model: torch.nn.Module, dataloader: torch.utils.data.DataLoader, loss_fn, device: torch.device):
    """Function for evaluation of a model `model` on the data in `dataloader` on device `device`,
    using the specified `loss_fn` loss function"""
    model.eval()
    # We will accumulate the mean loss in variable `loss`
    loss = 0
    with torch.no_grad():  # We do not need gradients for evaluation
        # Loop over all samples in `dataloader`
        for data in tqdm(dataloader, desc="scoring", position=0):
            # Get a sample and move inputs and targets to device
            images, inputs, known, means, stds, file_names = data

            inputs = inputs.to(device)
            known = known.to(device)
            means = means.to(device)
            stds = stds.to(device)
            images = images.to(device)

            # Get outputs of the specified model
            outputs = model(inputs)

            outputs = (outputs.permute(2, 3, 0, 1) * stds + means).permute(2, 3, 0, 1)
            # outputs = (outputs.permute(1, 2, 3, 0) * stds + means).permute(3, 0, 1, 2)

            # mask out known values
            masked_outputs = torch.where(known == 0, outputs, images) * 255
            images *= 255

            masked_outputs.int()
            images.int()
            # Here we could clamp the outputs to the minimum and maximum values of inputs for better performance

            # Add the current loss, which is the mean loss over all minibatch samples
            # (unless explicitly otherwise specified when creating the loss function!)
            loss += loss_fn(masked_outputs, images).item()
    # Get final mean loss by dividing by the number of minibatch iterations (which
    # we summed up in the above loop)
    loss /= len(dataloader)
    model.train()
    print(f"loss: {loss}")
    return loss


def predict_unknown(model: torch.nn.Module,
                    dataloader: torch.utils.data.DataLoader,
                    results_path,
                    plot_path,
                    device: torch.device):
    # """Function for evaluation of a model `model` on the data in `dataloader` on device `device`,
    # using the specified `loss_fn` loss function"""
    model.eval()
    update = 0

    predictions = []

    with torch.no_grad():  # We do not need gradients for evaluation
        # Loop over all samples in `dataloader`
        for data in tqdm(dataloader, desc="predicting", position=0):
            # Get a sample and move inputs and targets to device
            images, inputs, known, means, stds, file_names = data

            images = images.to(device)
            inputs = inputs.to(device)
            known = known.to(device)
            means = means.to(device)
            stds = stds.to(device)

            # Get outputs of the specified model
            outputs = model(inputs)

            outputs = (outputs.permute(2, 3, 0, 1) * stds + means).permute(2, 3, 0, 1)

            # create plots
            if PLOT_PREDICTIONS:
                plot(inputs.detach().cpu().numpy(), images.detach().cpu().numpy(), outputs.detach().cpu().numpy(),
                     plot_path, update)
                update += 1

            # store for pickle
            for output_array, known_array in zip(outputs.detach().cpu().numpy(), known.detach().cpu().numpy()):
                target_array = output_array[known_array == 0].copy()
                target_array = (target_array * 255).astype(np.uint8)
                predictions.append(target_array)

    model.train()
    # store in pickle file
    with open(os.path.join(results_path, f'predictions.pkl'), 'wb') as f:
        pickle.dump(predictions, f)


def main(results_path,
         training_path: str,
         network_config: dict,
         learningrate: int = 1e-3,
         weight_decay: float = 1e-5,
         n_updates: int = int(1e5),
         device: torch.device = torch.device("cuda:0"),
         num_workers: int = 0,
         batch_size: int = 2):
    """Main function that takes hyperparameters and performs training and evaluation of model"""

    results_path = f"{results_path}{training_path.replace('training', '')}_ks{network_config['kernel_size']}_l{network_config['n_hidden_layers']}"

    # Prepare a path to plot to
    plotpath = os.path.join(results_path, 'plots')
    os.makedirs(plotpath, exist_ok=True)

    # Load or dataset
    image_dataset = SimpleImageDataset(data_folder=training_path)

    # 1. Decide which samples you want to use in your training-, validation- or test sets.
    # Split dataset into training, validation, and test set randomly
    training_set = torch.utils.data.Subset(image_dataset, indices=np.arange(int(len(image_dataset) * (3 / 5))))
    validation_set = torch.utils.data.Subset(image_dataset, indices=np.arange(int(len(image_dataset) * (3 / 5)),
                                                                              int(len(image_dataset) * (4 / 5))))
    test_set = torch.utils.data.Subset(image_dataset,
                                       indices=np.arange(int(len(image_dataset) * (4 / 5)), len(image_dataset)))

    # Create datasets and dataloaders without augmentation (for evaluation)
    train_loader = DataLoader(AugmentedImages(training_set),
                              batch_size=1,
                              shuffle=False,
                              num_workers=num_workers,
                              collate_fn=image_collate_fn)
    val_loader = DataLoader(AugmentedImages(validation_set),
                            batch_size=1, shuffle=False,
                            num_workers=num_workers,
                            collate_fn=image_collate_fn)
    test_loader = DataLoader(AugmentedImages(test_set),
                             batch_size=1, shuffle=False,
                             num_workers=num_workers,
                             collate_fn=image_collate_fn)

    # Create datasets and dataloaders with rotated targets with augmentation (for training)
    transform_chain = transforms.Compose([transforms.RandomHorizontalFlip(),
                                          transforms.RandomVerticalFlip()])

    training_set_augmented = AugmentedImages(dataset=training_set, transform_chain=transform_chain)
    train_loader_augmented = DataLoader(training_set_augmented, batch_size=batch_size, shuffle=True,
                                        num_workers=num_workers,
                                        collate_fn=image_collate_fn)

    # Define a tensorboard summary writer that writes to directory "results_path/tensorboard"
    writer = SummaryWriter(log_dir=os.path.join(results_path, 'tensorboard'))

    # Create Network
    net = ChallengeCNN(**network_config)
    net.to(device)

    # Get mse loss function
    mse = RMSELoss

    # Get adam optimizer
    optimizer = torch.optim.Adam(net.parameters(), lr=learningrate, weight_decay=weight_decay)

    interval = 1000
    print_stats_at = 100  # print status to tensorboard every x updates
    plot_at = interval  # plot every x updates
    validate_at = interval  # evaluate model on validation set and check for new best model every x updates

    # Save initial model as "best" model (will be overwritten later)
    best_model_file = os.path.join(results_path, f"best_model.pt")
    last_model_file = os.path.join(results_path, f"last_model.pt")
    progress_file = os.path.join(results_path, f"progress.txt")

    if TRAIN:  # Train until n_updates updates have been reached
        update_progress_bar = tqdm(total=n_updates, desc=f"loss: {np.nan:7.5f}", position=0)

        if TRAIN_CONTINUE:
            with open(progress_file, 'r') as f:
                x = f.read().split('\n')
                update = int(x[0])
                best_validation_loss = float(x[1])
                f.close()

            update_progress_bar.n = update
            update_progress_bar.refresh()
            net = torch.load(last_model_file, map_location=torch.device(device))
        else:
            update = 0  # current update counter
            best_validation_loss = np.inf  # best validation loss so far
            torch.save(net, best_model_file)

        while update < n_updates:
            for data in train_loader_augmented:
                # Get next samples
                images, inputs, known, means, stds, ids = data

                images = images.to(device)
                inputs = inputs.to(device)
                known = known.to(device)
                means = means.to(device)
                stds = stds.to(device)

                # Reset gradients
                optimizer.zero_grad()

                # Get outputs for network
                outputs = net(inputs)

                outputs = (outputs.permute(2, 3, 0, 1) * stds + means).permute(2, 3, 0, 1)

                # Calculate loss, do backward pass, and update weights
                # mask out known values
                masked_outputs = torch.where(known == 0.0, outputs, images) * 255
                images *= 255

                loss = mse(masked_outputs, images)
                loss.backward()
                optimizer.step()

                # Print current status and score
                if (update + 1) % print_stats_at == 0:
                    writer.add_scalar(tag="training/loss", scalar_value=loss.cpu(), global_step=update)

                # Plot output
                if (update + 1) % plot_at == 0:
                    plot(inputs.detach().cpu().numpy(), images.detach().cpu().numpy(),
                         masked_outputs.detach().cpu().numpy(),
                         plotpath, update)

                # Evaluate model on validation set
                if (update + 1) % validate_at == 0:
                    val_loss = evaluate_model(net, dataloader=val_loader, loss_fn=mse, device=device)
                    writer.add_scalar(tag="validation/loss", scalar_value=val_loss, global_step=update)
                    # Add weights and gradients as arrays to tensorboard
                    for i, (name, param) in enumerate(net.named_parameters()):
                        writer.add_histogram(tag=f"validation/param_{i} ({name})", values=param.cpu(),
                                             global_step=update)
                        writer.add_histogram(tag=f"validation/gradients_{i} ({name})", values=param.grad.cpu(),
                                             global_step=update)
                    # Save best model for early stopping
                    if val_loss < best_validation_loss:
                        best_validation_loss = val_loss
                        torch.save(net, best_model_file)

                    torch.save(net, last_model_file)
                    with open(progress_file, 'w') as f:
                        f.write(str(update + 1))
                        f.write('\n')
                        f.write(str(best_validation_loss))
                        f.close()

                update_progress_bar.set_description(f"loss: {loss:7.5f}", refresh=True)
                update_progress_bar.update()

                # Increment update counter, exit if maximum number of updates is reached
                # Here, we could apply some early stopping heuristic and also exit if its
                # stopping criterion is met
                update += 1
                if update >= n_updates:
                    break

        update_progress_bar.close()
        writer.close()
        print("\nFinished Training!")

    else:

        writer.close()
        print("\nNo Training!")

    if EVALUATE:
        # Load best model and compute score on test set
        print(f"Computing scores for best model")
        net = torch.load(best_model_file, map_location=torch.device(device))
        train_loss = evaluate_model(net, dataloader=train_loader, loss_fn=mse, device=device)
        val_loss = evaluate_model(net, dataloader=val_loader, loss_fn=mse, device=device)
        test_loss = evaluate_model(net, dataloader=test_loader, loss_fn=mse, device=device)

        print(f"Scores:")
        print(f"  training loss: {train_loss}")
        print(f"validation loss: {val_loss}")
        print(f"      test loss: {test_loss}")

        # Write result to file
        with open(os.path.join(results_path, f"results.txt"), "w") as rf:
            print(f"Scores:", file=rf)
            print(f"  training loss: {train_loss}", file=rf)
            print(f"validation loss: {val_loss}", file=rf)
            print(f"      test loss: {test_loss}", file=rf)

    if PREDICT:
        print(f"Computing pixels for challenge")
        net = torch.load(best_model_file, map_location=torch.device(device))
        challenge_test_dataset = TestDataset("test")
        challenge_plotpath = os.path.join(results_path, 'predictions')
        os.makedirs(challenge_plotpath, exist_ok=True)
        challenge_test_loader = DataLoader(challenge_test_dataset, shuffle=False, batch_size=1,
                                           collate_fn=image_collate_fn)
        # collate_fn=image_collate_fn)
        predict_unknown(net, dataloader=challenge_test_loader, results_path=results_path, plot_path=challenge_plotpath,
                        device=device)


if __name__ == "__main__":
    import argparse
    import json

    os.environ['KMP_DUPLICATE_LIB_OK']='True'

    # with open(os.path.join('test', 'example_submission_random.pkl'), 'rb') as f:
    #     x = pickle.load(f)

    parser = argparse.ArgumentParser()
    parser.add_argument("config_file", type=str, help="Path to JSON config file")
    args = parser.parse_args()

    with open(args.config_file) as cf:
        config = json.load(cf)

    main(**config)
