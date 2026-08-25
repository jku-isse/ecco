"""
Author -- Michael Widrich, Andreas Schörgenhumer
Contact -- schoergenhumer@ml.jku.at
Date -- 02.03.2022

###############################################################################

The following copyright statement applies to all code within this file.

Copyright statement:
This  material,  no  matter  whether  in  printed  or  electronic  form,
may  be  used  for personal  and non-commercial educational use only.
Any reproduction of this manuscript, no matter whether as a whole or in parts,
no matter whether in printed or in electronic form, requires explicit prior
acceptance of the authors.

###############################################################################

Images taken from: https://pixabay.com/
"""

import gzip
import os
import signal
import sys
import traceback
from glob import glob
from types import GeneratorType

import dill as pkl
import numpy as np


def print_outs(outs, line_token="-"):
    print(line_token * 40)
    print(outs, end="" if isinstance(outs, str) and outs.endswith("\n") else "\n")
    print(line_token * 40)


time_given = int(15)

check_for_timeout = hasattr(signal, "SIGALRM")

if check_for_timeout:
    def handler(signum, frame):
        raise TimeoutError(f"Timeout after {time_given}sec")
    
    
    signal.signal(signal.SIGALRM, handler)

ex_file = "ex3.py"
full_points = 15
points = full_points
python = sys.executable

inputs = sorted(glob(os.path.join("unittest", "unittest_input_*"), recursive=True))

if not len(inputs):
    raise FileNotFoundError("Could not find unittest_input_* files")

for test_i, input_folder in enumerate(inputs):
    comment = ""
    fcall = ""
    
    with open(os.devnull, "w") as null:
        # sys.stdout = null
        try:
            if check_for_timeout:
                signal.alarm(time_given)
                from ex3 import ImageStandardizer
                
                signal.alarm(0)
            else:
                from ex3 import ImageStandardizer
            proper_import = True
        except Exception:
            errs = traceback.format_exc()
            points -= full_points / len(inputs)
            proper_import = False
        finally:
            sys.stdout.flush()
            sys.stdout = sys.__stdout__
    
    if proper_import:
        with open(os.devnull, "w") as null:
            # sys.stdout = null
            try:
                if check_for_timeout:
                    signal.alarm(time_given)
                    # check constructor
                    instance = ImageStandardizer(input_dir=input_folder)
                    fcall = f"ImageStandardizer(input_dir='{input_folder}')"
                    signal.alarm(0)
                else:
                    # check constructor
                    instance = ImageStandardizer(input_dir=input_folder)
                    fcall = f"ImageStandardizer(input_dir='{input_folder}')"
                errs = ""
                
                # check correct file names + sorting
                input_basename = os.path.basename(input_folder)
                with open(os.path.join("unittest", "solutions", input_basename, f"filenames.txt"), "r") as f:
                    # must replace the separator that was used when creating the solution files
                    files_sol = f.read().replace("\\", os.path.sep).splitlines()
                    # for simplicity's sake, only compare relative paths here
                    common = os.path.commonprefix(instance.files)
                    rel_instance_files = [os.path.join(input_folder, f[len(common):]) for f in instance.files]
                    if not hasattr(instance, "files"):
                        points -= full_points / len(inputs) / 3
                        comment += f"Attributes 'files' missing.\n"
                    elif rel_instance_files != files_sol:
                        points -= full_points / len(inputs) / 3
                        comment += f"Attribute 'files' should be {files_sol} but is {instance.files} (see directory 'solutions').\n"
                    elif len(instance.files) != len(files_sol):
                        points -= full_points / len(inputs) / 3
                        comment += f"Number of files should be {len(files_sol)} but is {len(instance.files)} (see directory 'solutions').\n"
                
                # check if class has method analyze_images
                method = "analyze_images"
                if not hasattr(instance, method):
                    comment += f"Method '{method}' missing.\n"
                    points -= full_points / len(inputs) / 3
                else:
                    # check for correct data types
                    stats = instance.analyze_images()
                    if (type(stats) is not tuple) or (len(stats) != 2):
                        points -= full_points / len(inputs) / 3
                        comment += f"Incorrect return value of method '{method}' (should be tuple of length 2).\n"
                    else:
                        with open(os.path.join("unittest", "solutions", input_basename, f"mean_and_std.pkl"),
                                  "rb") as fh:
                            data = pkl.load(fh)
                            m = data["mean"]
                            s = data["std"]
                        if not (isinstance(stats[0], np.ndarray) and isinstance(stats[1], np.ndarray) and
                                stats[0].dtype == np.float64 and stats[1].dtype == np.float64 and
                                stats[0].shape == (3,) and stats[1].shape == (3,)):
                            points -= full_points / len(inputs) / 3
                            comment += f"Incorrect return data type of method '{method}' (tuple entries should be np.ndarray of dtype np.float64 and shape (3,)).\n"
                        else:
                            if not np.isclose(stats[0], m, atol=0).all():
                                points -= full_points / len(inputs) / 6
                                comment += f"Mean should be {m} but is {stats[0]} (see directory 'solutions').\n"
                            if not np.isclose(stats[1], s, atol=0).all():
                                points -= full_points / len(inputs) / 6
                                comment += f"Std should be {s} but is {stats[1]} (see directory 'solutions').\n"
                
                # check if class has method get_standardized_images
                method = "get_standardized_images"
                if not hasattr(instance, method):
                    comment += f"Method '{method}' missing.\n"
                    points -= full_points / len(inputs) / 3
                # check for correct data types
                elif not isinstance(instance.get_standardized_images(), GeneratorType):
                    points -= full_points / len(inputs) / 3
                    comment += f"'{method}' is not a generator.\n"
                else:
                    # Read correct image solutions
                    with gzip.open(os.path.join("unittest", "solutions", input_basename, "images.pkl"), "rb") as fh:
                        ims_sol = pkl.load(file=fh)
                    
                    # Get image submissions
                    ims_sub = list(instance.get_standardized_images())
                    
                    if not len(ims_sub) == len(ims_sol):
                        points -= full_points / len(inputs) / 3
                        comment += f"{len(ims_sol)} image arrays should have been returned but got {len(ims_sub)}.\n"
                    elif any([im_sub.dtype.num != np.dtype(np.float32).num for im_sub in ims_sub]):
                        points -= full_points / len(inputs) / 3
                        comment += f"Returned image arrays should have datatype np.float32 but at least one array isn't.\n"
                    else:
                        equal = [np.all(np.isclose(im_sub, im_sol, atol=0)) for im_sub, im_sol in zip(ims_sub, ims_sol)]
                        if not all(equal):
                            points -= full_points / len(inputs) / 3
                            comment += f"Returned images {list(np.where(np.logical_not(equal))[0])} do not match solution (see images.pkl files for solution).\n"
            except Exception:
                errs = traceback.format_exc()
                points -= full_points / len(inputs)
            finally:
                sys.stdout.flush()
                sys.stdout = sys.__stdout__
    
    print()
    print_outs(f"Test {test_i}", line_token="#")
    print("Function call:")
    print_outs(fcall)
    
    if errs:
        print(f"Some unexpected errors occurred:")
        print_outs(errs)
    else:
        print("Notes:")
        print_outs("No issues found" if comment == "" else comment)
        
    # due to floating point calculations it could happen that we get -0 here
    if points < 0:
        assert abs(points) < 1e-7, f"points were {points} < 0: error when subtracting points?"
        points = 0
    print(f"Current points: {points:.2f}")

print(f"\nEstimated points upon submission: {points:.2f} (out of {full_points:.2f})")
print(f"This is only an estimate, see 'Instructions for submitting homework' in Moodle "
      f"for common mistakes that can still lead to 0 points.")
if not check_for_timeout:
    print("\n!!Warning: Had to switch to Windows compatibility version and did not check for timeouts!!")
